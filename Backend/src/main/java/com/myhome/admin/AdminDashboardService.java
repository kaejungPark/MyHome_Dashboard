package com.myhome.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.time.*;

/**
 * 관리자 통계를 조회하고 가입자가 없는 날짜의 인원수를 0명으로 채운다.
 * 날짜 집계는 한국 시간을 기준으로 한다.
 */
@Service
public class AdminDashboardService {
    private final AdminDashboardMapper adminDashboardMapper;

    public AdminDashboardService(AdminDashboardMapper adminDashboardMapper) {
        this.adminDashboardMapper = adminDashboardMapper;
    }

    /**
     * 전체 회원 수, 오늘 가입자 수, 처리 대기 신고 수, 숨김 게시글 수를 조회한다.
     * 오늘 가입자 수는 한국 시간의 하루 범위를 UTC로 변환하여 집계한다.
     */
    @Transactional(readOnly = true)
    public AdminDashboardResponse findAdminDashboard() {

        ZoneId seoulZone = ZoneId.of("Asia/Seoul");

        // 서버의 기본 시간대와 관계없이 한국 시간 기준 오늘 날짜를 구한다.
        LocalDate today = LocalDate.now(seoulZone);

        // 조회 종료 경계는 한국 시간 기준 내일 0시로 설정한다.
        LocalDate tomorrow = today.plusDays(1);

        // 한국 시간의 오늘 0시를 DB에 저장된 UTC 기준 시각으로 변환한다.
        LocalDateTime startUtc = today.atStartOfDay(seoulZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        // 한국 시간의 내일 0시를 UTC로 변환한다.
        // 쿼리에서는 CREATED_DT >= startUtc AND CREATED_DT < endUtc로 조회한다.
        LocalDateTime endUtc = tomorrow.atStartOfDay(seoulZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();


        // 탈퇴한 회원을 제외한 전체 회원 수를 조회한다.
        long totalUserCount = adminDashboardMapper.getTotalUserCount();

        // 한국 시간 기준 오늘 가입한 회원 수를 조회한다.
        long todaySignupCount =
                adminDashboardMapper.getTodaySignupCount(startUtc, endUtc);

        // 아직 관리자가 처리하지 않은 신고 수를 조회한다.
        long pendingReportCount = adminDashboardMapper.getPendingReportCount();

        // 현재 일반 사용자에게 공개되지 않는 숨김 게시글 수를 조회한다.
        long hiddenCommunityCount = adminDashboardMapper.getHiddenCommunityCount();

        // 조회한 네 가지 통계를 하나의 응답으로 묶어 반환한다.
        return new AdminDashboardResponse(
                totalUserCount,
                todaySignupCount,
                pendingReportCount,
                hiddenCommunityCount
        );
    }

    /**
     * 오늘을 포함한 최근 7일의 가입 추이를 조회한다.
     * 한국 날짜 기준으로 집계하고, 가입자가 없는 날짜는 0명으로 반환한다.
     */
    @Transactional(readOnly = true)
    public List<AdminSignupTrendResponse> findSignupTrend() {
        ZoneId seoulZone = ZoneId.of("Asia/Seoul");
        LocalDate today = LocalDate.now(seoulZone);

        // 오늘을 포함하여 총 7일을 조회한다.
        LocalDate startDate = today.minusDays(6);
        LocalDate endDate = today.plusDays(1);

        // 한국 시간의 날짜 경계를 DB에 저장된 UTC 시각으로 변환한다.
        LocalDateTime startUtc = startDate.atStartOfDay(seoulZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        // DB에서 날짜별 가입자 수를 조회한다.
        // 가입자가 없는 날짜는 조회 결과에 포함되지 않을 수 있다.
        LocalDateTime endUtc = endDate.atStartOfDay(seoulZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        List<AdminSignupTrendResponse> rows =
                adminDashboardMapper.findSignupTrend(startUtc, endUtc);

        // 조회된 가입자 수를 날짜로 빠르게 찾을 수 있도록 저장한다.
        Map<LocalDate, Long> countByDate = new HashMap<>();

        for (AdminSignupTrendResponse row : rows) {
            countByDate.put(row.date(), row.signupCount());
        }

        // 가입자가 없는 날도 포함하여 오래된 날짜부터 총 7개를 반환한다.
        List<AdminSignupTrendResponse> result = new ArrayList<>(7);

        for (int day = 0; day < 7; day++) {
            LocalDate date = startDate.plusDays(day);
            // 해당 날짜의 조회 결과가 없으면 가입자 수를 0명으로 처리한다.
            long signupCount = countByDate.getOrDefault(date, 0L);

            result.add(new AdminSignupTrendResponse(date, signupCount));
        }

        return result;
    }
}
