package com.myhome.shopping;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

/**
 * 장보기 알림의 발송 권한 확보와 발송 결과 저장을 담당한다.
 *
 * 사용자 ID와 알림 날짜로 최초 발송 여부를 판단하고,
 * DB UNIQUE 제약으로 같은 날 같은 사용자에 대한 중복 등록을 막는다.
 *
 * 각 DB 작업은 독립된 짧은 트랜잭션으로 처리한다.
 * 메일 발송은 이 클래스 밖에서 수행하여 외부 서버 응답을 기다리는 동안
 * DB 트랜잭션이 계속 열려 있지 않도록 한다.
 *
 * 현재는 최초 발송만 지원하며, 실패 건의 자동 재시도는 수행하지 않는다.
 */
@Service
public class ShoppingNotificationHistoryService {
    private final ShoppingNotificationMapper notificationMapper;
    private final TransactionTemplate transactionTemplate;

    public ShoppingNotificationHistoryService(
            ShoppingNotificationMapper notificationMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.notificationMapper = notificationMapper;

        // DB 작업의 시작·커밋·롤백 범위를 코드로 지정하는 도구다.
        // execute는 결과를 반환하고, executeWithoutResult는 반환값 없이 처리한다.

        // 현재 애플리케이션의 트랜잭션 관리자를 이용해 DB 작업 범위를 설정한다.
        // REQUIRES_NEW는 호출한 쪽에 트랜잭션이 있어도 별도 트랜잭션을 시작한다.
        // 따라서 이후 메일 발송이 실패하더라도 이미 저장한 최초 이력은 유지된다.
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    /**
     * 해당 사용자의 알림 날짜에 대한 최초 발송 권한을 확보한다.
     *
     * 이력이 없으면 PROCESSING 상태로 등록한 뒤 생성된 이력을 반환한다.
     * 기존 이력이 있으면 상태와 관계없이 null을 반환하여 발송을 건너뛴다.
     * FAILED도 자동 재시도 정책을 추가하기 전까지는 건너뛴다.
     *
     * 동시 실행으로 중복 등록이 발생하면 실패한 트랜잭션을 롤백한 뒤,
     * 새 트랜잭션에서 해당 사용자·날짜의 이력을 다시 확인한다.
     *
     * @param userId 수신 회원 ID
     * @param notificationDate 한국 기준 알림 실행일
     * @param recipientEmail 이번 발송에 사용할 수신 이메일
     * @return 발송 권한 확보 시 생성된 이력, 기존 이력이 있으면 null
     */
    public ShoppingNotificationHistory claimForSending(
            Long userId,
            LocalDate notificationDate,
            String recipientEmail
    ) {
        try {
            // 콜백이 정상 종료되면 커밋하고, 실행 중 RuntimeException이 발생하면 롤백한다.
            // 여기서 status는 메일 발송 상태가 아니라 트랜잭션 제어 객체다.
            // execute가 성공적으로 반환된 뒤에만 호출한 쪽에서 메일을 발송할 수 있다.
            return transactionTemplate.execute(status -> {
                ShoppingNotificationHistory existing = notificationMapper.findHistory(userId, notificationDate);

                // SENT·PROCESSING·FAILED 모두 이번 최초 발송에서는 건너뛴다.
                if (existing != null) {
                    return null;
                }

                // 앞선 조회에서 이력이 없었더라도 다른 실행이 먼저 등록할 수 있다.
                // 최종 중복 방지는 DB의 USER_ID + NOTIFICATION_DATE UNIQUE 제약이 담당한다.
                int insertRow = notificationMapper.insertProcessing(
                        userId,
                        notificationDate,
                        recipientEmail
                );

                // 최초 이력은 정확히 한 행이 등록되어야 한다.
                // 예상과 다른 결과이면 예외를 발생시켜 이 트랜잭션의 변경을 취소한다.
                if (insertRow != 1) {
                    throw new IllegalStateException("장보기 알림 발송 이력 등록 중 오류가 발생했습니다.");
                }

                // INSERT가 반환한 값은 생성된 ID가 아니라 등록 행 수다.
                // 동일한 사용자·날짜로 다시 조회하여 생성된 ID와 시도 횟수를 가져온다.
                // 이 값은 이후 성공·실패 기록이 어느 발송 시도의 결과인지 확인하는 데 사용한다.
                ShoppingNotificationHistory created = notificationMapper.findHistory(userId, notificationDate);

                if (created == null) {
                    throw new IllegalStateException("등록한 장보기 알림 발송 이력을 찾을 수 없습니다.");
                }

                return created;
            });
        } catch (DuplicateKeyException e) {
            // 이 catch에 도달했을 때 앞선 execute의 실패 트랜잭션은 이미 롤백된 상태다.
            // 같은 사용자·날짜의 이력이 생겼다면 다른 실행이 먼저 권한을 확보한 것으로 본다.
            // 재조회도 새 트랜잭션에서 수행하여 실패한 트랜잭션을 재사용하지 않는다.
            ShoppingNotificationHistory existing = transactionTemplate.execute(status ->
                    notificationMapper.findHistory(userId, notificationDate
                    )
            );

            if (existing != null) {
                return null;
            }

            // 해당 날짜 이력으로 설명되지 않는 중복 오류는 숨기지 않는다.
            throw e;
        }
    }

    /**
     * 메일 서버로 전송이 성공한 발송 시도를 SENT 상태로 기록한다.
     *
     * Mapper에서 이력 ID·PROCESSING 상태·시도 횟수가 모두 일치하는 경우만 수정한다.
     * 성공 시각을 저장하고 이전 오류 메시지를 비운다.
     *
     * 수정 결과가 1행이 아니면 예외를 전달한다.
     * 이 메서드가 실패하더라도 메일은 이미 전송됐을 수 있으므로,
     * 호출한 쪽에서 일반 발송 실패로 바꾸거나 즉시 재발송해서는 안 된다.
     *
     * @param historyId 발송 권한을 확보할 때 받은 이력 ID
     * @param expectedAttemptCount 이번 발송 시도의 횟수
     */
    public void markSent(Long historyId, int expectedAttemptCount) {
        transactionTemplate.executeWithoutResult(status -> {
            int updateRow = notificationMapper.markSent(
                    historyId, expectedAttemptCount
            );

            if (updateRow != 1) {
                throw new IllegalStateException("장보기 알림 발송 성공 기록을 저장하지 못했습니다.");
            }
        });
    }

    /**
     * 메일 전송 중 발생한 실패를 FAILED 상태로 기록한다.
     *
     * Mapper에서 이력 ID·PROCESSING 상태·시도 횟수가 일치하는 경우만 수정한다.
     * 오류 메시지가 없으면 기본 문구를 사용하고, 최대 길이는 Mapper에서 제한한다.
     *
     * 실패 이력 저장에도 오류가 발생하면 예외를 호출한 쪽으로 전달한다.
     * 성공 이력 저장 실패를 처리하는 용도로 호출해서는 안 된다.
     *
     * @param historyId 발송 권한을 확보할 때 받은 이력 ID
     * @param expectedAttemptCount 이번 발송 시도의 횟수
     * @param errorMessage 메일 전송 실패 원인
     */
    public void markFailed(
            Long historyId,
            int expectedAttemptCount,
            String errorMessage
    ) {
        String message = errorMessage == null || errorMessage.isBlank()
                ? "메일 발송 중 오류가 발생했습니다."
                : errorMessage;

        transactionTemplate.executeWithoutResult(status -> {
            int updateRow = notificationMapper.markFailed(
                    historyId,
                    expectedAttemptCount,
                    message
            );

            if (updateRow != 1) {
                throw new IllegalStateException("장보기 알림 발송 실패 기록을 저장하지 못했습니다.");
            }
        });
    }
}
