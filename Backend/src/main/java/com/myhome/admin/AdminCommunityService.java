package com.myhome.admin;

import com.myhome.common.response.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AdminCommunityService {
    private final AdminCommunityMapper adminCommunityMapper;

    public AdminCommunityService (AdminCommunityMapper adminCommunityMapper) {
        this.adminCommunityMapper = adminCommunityMapper;
    }
    @Transactional(readOnly = true)
    public PageResponse<AdminReportListResponse> getReports(int page, int size, String status) {
        // 페이지와 페이지 크기를 검증한다.
        if (page < 1 || size < 1 || size > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "페이지는 1 이상, 페이지 크기는 1~50이어야 합니다."
            );
        }

        String statusType = status == null || status.isBlank() ? "ALL" : status.trim();

        if (!statusType.equals("ALL") &&
                !statusType.equals("PENDING") &&
                !statusType.equals("RESOLVED") &&
                !statusType.equals("REJECTED")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "검색 기준은 ALL, PENDING, RESOLVED, REJECTED 중 하나여야 합니다."
            );
        }

        // 곱하기 전에 long으로 변환하여 정수 범위 초과를 방지한다.
        long offset = (long) (page - 1) * size;

        // 목록과 동일한 검색 조건으로 전체 신고 수를 조회한다.
        long totalCount = adminCommunityMapper.countAll(statusType);

        // 나머지가 있으면 마지막 페이지를 하나 추가한다.
        long totalPages = totalCount / size
                + (totalCount % size == 0 ? 0 : 1);

        // 검색 결과가 없거나 요청한 페이지가 범위를 넘으면 빈 목록을 반환한다.
        List<AdminReportListResponse> items = offset >= totalCount
                ? List.of()
                : adminCommunityMapper.findPage(statusType, offset, size);

        return new PageResponse<>(
                items,
                page,
                size,
                totalCount,
                totalPages
        );

    }

    /**
     * 신고 ID로 관리자용 상세 정보를 조회한다.
     * 숨김 게시글과 처리 완료된 신고도 조회할 수 있다.
     */
    @Transactional(readOnly = true)
    public AdminReportDetailResponse getReport(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 신고 번호입니다."
            );
        }

        AdminReportDetailResponse report = adminCommunityMapper.findReport(id);

        if (report == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "신고 내역을 찾을 수 없습니다."
            );
        }

        return report;
    }

    /**
     * 처리 대기 신고를 처리하고, 수용한 경우 대상 게시글을 숨긴다.
     * 신고 처리와 게시글 숨김 중 하나라도 실패하면 전체 변경을 롤백한다.
     */
    @Transactional
    public void processReport(
            Long adminId,
            Long id,
            AdminReportProcessRequest request
    ) {
        // 기존 상세 조회 메서드에서 ID 검증과 신고 존재 여부를 확인한다.
        AdminReportDetailResponse report = getReport(id);

        if (!"PENDING".equals(report.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 처리된 신고입니다."
            );
        }

        // 서비스에서도 허용된 처리 상태인지 확인한다.
        if (!"RESOLVED".equals(request.status())
                && !"REJECTED".equals(request.status())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "처리 상태는 RESOLVED 또는 REJECTED여야 합니다."
            );
        }

        // UPDATE에서도 PENDING 조건을 검사해 동시 중복 처리를 차단한다.
        int processedRows = adminCommunityMapper.processReport(adminId, id, request);

        if (processedRows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 처리되었거나 처리할 수 없는 신고입니다."
            );
        }

        if (processedRows != 1) {
            throw new IllegalStateException("신고 처리 행 수가 올바르지 않습니다.");
        }

        // 기각한 경우에는 게시글의 현재 공개·숨김 상태를 변경하지 않는다.
        if ("RESOLVED".equals(request.status())) {
            int hiddenRows = adminCommunityMapper.hideCommunity(
                    adminId, report.communityId()
            );

            if (hiddenRows != 1) {
                throw new IllegalStateException("게시글 숨김 처리에 실패했습니다.");
            }
        }
    }
}
