package com.myhome.shopping;

import com.myhome.common.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 장보기 알림 대상 조회부터 사용자별 메일 발송까지 전체 흐름을 담당한다.
 *
 * 한국 날짜를 기준으로 오늘·내일 구매 예정인 미구매 항목을 조회하고,
 * 사용자별로 묶어 한 통의 이메일로 구성한다.
 *
 * 발송 권한 확보와 결과 저장은 ShoppingNotificationHistoryService에,
 * 실제 이메일 전송은 MailService에 위임한다.
 *
 * 외부 메일 전송을 포함하므로 클래스와 발송 메서드에
 * 전체 작업을 묶는 @Transactional을 적용하지 않는다.
 */
@Service
public class ShoppingNotificationService {

    // 발송 대상 없음, 중복 건너뜀, 발송 결과와 처리 오류를 서버 로그로 남긴다.
    // 메일 본문 전체나 인증 비밀번호는 로그에 기록하지 않는다.
    private static final Logger log = LoggerFactory.getLogger(ShoppingNotificationService.class);

    // 서버 운영체제의 시간대와 관계없이 한국 날짜로 알림 대상을 판단한다.
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final ShoppingNotificationMapper notificationMapper;
    private final ShoppingNotificationHistoryService historyService;
    private final MailService mailService;

    public ShoppingNotificationService(
            ShoppingNotificationMapper notificationMapper,
            ShoppingNotificationHistoryService historyService,
            MailService mailService
    ) {
        this.notificationMapper = notificationMapper;
        this.historyService = historyService;
        this.mailService = mailService;
    }

    /**
     * 이번 실행의 알림 대상을 조회하고 사용자별 발송을 진행한다.
     *
     * 오늘 날짜는 실행 시작 시 한 번만 계산하여 조회 조건과 이력 날짜를 통일한다.
     * 사용자별 처리 중 발생한 오류는 기록하고 나머지 사용자는 계속 처리한다.
     *
     * 최초 대상 조회 자체가 실패하면 발송을 진행할 수 없으므로
     * 해당 예외는 이 메서드를 호출한 스케줄러 등으로 전달한다.
     */
    public void sendNotifications() {
        // 실행 중 날짜가 달라지지 않도록 오늘 날짜를 한 번만 계산한다.
        LocalDate today = LocalDate.now(KOREA_ZONE);
        LocalDate tomorrow = today.plusDays(1);

        // 활성 회원의 항목 중 미구매이며 구매 예정일이 오늘 또는 내일인 대상을 조회한다.
        // 수신 이메일과 메일 본문에 필요한 물품 정보를 함께 가져온다.
        List<ShoppingNotificationTarget> targets = notificationMapper.findTargets(today, tomorrow);

        // 발송 대상이 없으면 이력 생성과 메일 발송 없이 이번 실행을 종료한다.
        if (targets.isEmpty()) {
            log.info("장보기 알림 대상 없음: date={}", today);
            return;
        }

        // 사용자 ID로 묶고 Mapper에서 조회한 사용자·항목 순서를 유지한다.
        Map<Long, List<ShoppingNotificationTarget>> targetsByUser = targets.stream()
                // 같은 사용자의 항목을 하나의 목록으로 묶어 메일을 항목마다 보내지 않도록 한다.
                // LinkedHashMap은 조회된 사용자 순서를 유지하고,
                // 각 사용자 목록도 Mapper의 구매 예정일·항목 ID 정렬 순서를 유지한다.
                .collect(Collectors.groupingBy(
                   ShoppingNotificationTarget::userId,
                   LinkedHashMap::new,
                   Collectors.toList()
                ));

        for (Map.Entry<Long, List<ShoppingNotificationTarget>> entry : targetsByUser.entrySet()) {
            // 예외 처리 범위를 사용자 단위로 나눈다.
            // 특정 사용자의 이력 처리나 본문 생성이 실패해도 다음 사용자 처리를 계속한다.
            try {
                sendForUser(entry.getKey(), today, entry.getValue());
            } catch (RuntimeException e) {
                // 이력 조회·생성 등 사용자별 처리 오류를 기록하고 다음 사용자로 진행한다.
                log.error(
                        "장보기 알림 처리 오류: userId={}, date={}",
                        entry.getKey(), today, e
                );
            }
        }
    }

    /**
     * 한 사용자의 장보기 항목을 메일로 구성하고 발송 결과를 기록한다.
     *
     * 본문을 먼저 만든 뒤 발송 권한을 확보하여,
     * 본문 생성 오류 때문에 PROCESSING 이력이 남는 것을 줄인다.
     *
     * 권한 확보 결과가 null이면 발송하지 않는다.
     * 발송 실패는 FAILED로 기록하되, 전송 후 성공 기록만 실패한 경우는
     * 이미 메일이 전송됐을 수 있으므로 별도 오류로 남긴다.
     */
    private void sendForUser(
            Long userId,
            LocalDate today,
            List<ShoppingNotificationTarget> items
    ) {
        if (items.isEmpty()) {
            return;
        }

        // 같은 사용자로 묶인 목록이므로 첫 항목의 이메일을 사용한다.
        String recipient = items.get(0).email();

        if (recipient == null || recipient.isBlank()) {
            throw new IllegalStateException("장보기 알림 수신 이메일이 없습니다.");
        }

        recipient = recipient.trim();

        // 본문 작성 오류로 PROCESSING 이력이 남지 않도록 먼저 구성한다.
        String subject = "[MyHome] 장보기 예정 알림";
        String content = buildMailContent(today, items);

        ShoppingNotificationHistory history = historyService.claimForSending(userId, today, recipient);

        // 현재 구현에서는 성공·처리 중·실패 이력이 모두 발송 건너뛰기 대상이다.
        // FAILED도 별도 재시도 기능을 연결하기 전까지 다시 보내지 않는다.
        if (history == null) {
            log.info(
                    "기존 이력으로 장보기 알림 건너뜀: userId={}, date={}",
                    userId, today
            );
            return;
        }

        try {
            // 최초 이력의 커밋이 끝난 후 외부 메일 서버에 전송한다.
            // 이 구간에는 이력 Service에서 시작한 DB 트랜잭션이 열려 있지 않다.
            mailService.send(recipient, subject, content);
        } catch (RuntimeException sendError) {
            log.error(
                    "장보기 알림 메일 전송 실패: historyId={}, userId={}",
                    history.id(), userId, sendError
            );

            try {
                // 메일 전송 중 발생한 예외만 발송 실패로 기록한다.
                // 일부 통신 오류는 서버의 메일 수락 여부가 불명확할 수 있으므로 FAILED 상태라고 해서 수신되지 않았음이 항상 보장되는 것은 아니다.
                historyService.markFailed(
                        history.id(),
                        history.attemptCount(),
                        sendError.getMessage()
                );
            } catch (RuntimeException historyError) {
                // 실패 이력까지 저장되지 않은 경우 별도 확인이 필요하다.
                log.error(
                        "장보기 알림 실패 이력 저장 실패: historyId={}",
                        history.id(), historyError
                );
            }

            return;
        }

        // 메일 발송 try-catch와 성공 이력 저장 try-catch를 분리한다.
        // 전송 성공 후 DB 저장만 실패한 경우를 메일 전송 실패로 오인하지 않도록 한다.
        try {
            historyService.markSent(
                    history.id(),
                    history.attemptCount()
            );

            log.info(
                    "장보기 알림 발송 및 성공 기록 완료: historyId={}, userId={}, itemCount={}",
                    history.id(), userId, items.size()
            );
        } catch (RuntimeException historyError) {
            // 이미 메일은 전송됐으므로 FAILED로 바꾸거나 다시 보내지 않는다.
            log.error(
                    "메일 전송 성공 후 이력 저장 실패. 재발송 전 확인 필요: historyId={}, userId={}",
                    history.id(), userId, historyError
            );
        }
    }

    /**
     * 조회된 항목을 오늘·내일 구매 예정으로 구분하여 일반 텍스트 본문을 만든다.
     * 이 메서드는 문자열만 생성하며 DB 조회나 실제 메일 발송은 수행하지 않는다.
     * 목록은 최초 대상 조회 시점의 상태이므로 이후 사용자의 변경과 차이가 날 수 있다.
     */
    private String buildMailContent(
            LocalDate today,
            List<ShoppingNotificationTarget> items
    ) {
        StringBuilder content = new StringBuilder();

        content.append("안녕하세요. MyHome입니다.\n\n");
        content.append("구매 예정인 장보기 항목을 안내드립니다.\n");

        appendItemsForDate(content, "오늘 구매 예정", today, items);
        appendItemsForDate(content, "내일 구매 예정", today.plusDays(1), items);

        content.append("\n구매하셨다면 MyHome에서 구매 완료로 표시해 주세요.\n");
        content.append("이 메일은 알림 대상 조회 시점의 미구매 항목을 기준으로 작성되었습니다.\n");

        return content.toString();
    }

    /**
     * 지정한 구매 예정일에 해당하는 항목만 본문에 추가한다.
     * 해당 날짜의 항목이 없으면 제목과 빈 목록을 출력하지 않는다.
     * 수량은 불필요한 소수점 0을 제거하되 지수 표기 없이 표시한다.
     */
    private void appendItemsForDate(
            StringBuilder content,
            String heading,
            LocalDate dueDate,
            List<ShoppingNotificationTarget> items
    ) {
        List<ShoppingNotificationTarget> matchingItems =
                items.stream()
                        .filter(item -> dueDate.equals(item.purchaseDueDate()))
                        .toList();

        if (matchingItems.isEmpty()) {
            return;
        }

        content.append("\n[")
                .append(heading)
                .append(" · ")
                .append(dueDate)
                .append("]\n");

        for (ShoppingNotificationTarget item : matchingItems) {
            // 1.00은 1로 표시하고 1.50은 1.5로 표시한다.
            String quantity = item.quantity()
                    .stripTrailingZeros()
                    .toPlainString();

            content.append("- ")
                    .append(item.name())
                    .append(" / ")
                    .append(quantity)
                    .append(" ")
                    .append(item.unit())
                    .append("\n");
        }
    }
}
