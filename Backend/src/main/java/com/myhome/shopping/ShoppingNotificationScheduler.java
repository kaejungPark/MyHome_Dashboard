package com.myhome.shopping;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 정해진 시간에 장보기 알림 서비스를 호출한다.
 * 대상 조회·중복 방지·메일 발송은 Service에서 처리한다.
 */
@Component
public class ShoppingNotificationScheduler {
    private static final Logger log = LoggerFactory.getLogger(ShoppingNotificationScheduler.class);
    private final ShoppingNotificationService notificationService;

    public ShoppingNotificationScheduler (ShoppingNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 매일 한국 시간 오전 9시에 장보기 알림 처리를 시작한다.
     * 백엔드가 중지된 동안 놓친 실행은 자동으로 보충하지 않는다.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void sendDailyNotifications() {
        log.info("장보기 알림 정기 처리 시작");

        try {
            notificationService.sendNotifications();

            // 개별 사용자의 발송 실패는 Service에서 별도로 기록한다.
            // 이 로그는 모든 메일의 성공이 아니라 전체 처리 흐름의 종료를 의미한다.
            log.info("장보기 알림 정기 처리 종료");
        } catch (RuntimeException e) {
            // 대상 조회 자체의 실패 등 Service 밖으로 전달된 오류를 기록한다.
            log.error("장보기 알림 정기 처리 중 오류 발생", e);
        }

    }

}
