package com.myhome.admin;

import com.myhome.common.response.PageResponse;
import com.myhome.common.util.PageUtils;
import com.myhome.common.validation.PageValidator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/**
 * 신고 조회 조건을 검증하고 신고 수용·기각을 처리한다.
 * 신고를 수용하면 대상 게시글도 숨긴다.
 */
@Service
public class AdminCommunityService {
    private final AdminCommunityMapper adminCommunityMapper;

    public AdminCommunityService (AdminCommunityMapper adminCommunityMapper) {
        this.adminCommunityMapper = adminCommunityMapper;
    }

    /**
     * 관리자가 확인할 신고 목록을 상태별로 페이지 조회한다.
     * ALL이면 상태 조건 없이 조회하며, 숨김 게시글의 신고도 포함한다.
     * 조회 범위를 벗어난 페이지는 빈 목록으로 반환한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminReportListResponse> getReports(int page, int size, String status) {
        // 페이지와 페이지 크기를 검증한다.
        PageValidator.validate(page, size);

        // 상태값의 앞뒤 공백을 제거하고, 서비스에 null이나 빈 값이 전달되면 전체 조회로 처리한다.
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
        long offset = PageUtils.calculateOffset(page, size);

        // 목록과 동일한 검색 조건으로 전체 신고 수를 조회한다.
        long totalCount = adminCommunityMapper.countAll(statusType);

        // 나머지가 있으면 마지막 페이지를 하나 추가한다.
        long totalPages = PageUtils.calculateTotalPages(totalCount, size);

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
        // 신고 번호와 존재 여부를 확인하고 현재 처리 상태를 조회한다.
        // 조회만으로 중복 처리를 막을 수 없으므로 아래 UPDATE에서도 상태를 검사한다.
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

        // 신고 하나를 처리하므로 정상 변경 건수는 반드시 1건이어야 한다.
        if (processedRows != 1) {
            throw new IllegalStateException("신고 처리 행 수가 올바르지 않습니다.");
        }

        // 기각한 경우에는 게시글의 현재 공개·숨김 상태를 변경하지 않는다.
        if ("RESOLVED".equals(request.status())) {
            // 신고 ID가 아닌 신고 대상 게시글 ID로 숨김 처리한다.
            // 이미 숨겨진 게시글은 기존 숨김 처리자와 처리일을 유지한다.
            int hiddenRows = adminCommunityMapper.hideCommunity(
                    adminId, report.communityId()
            );

            if (hiddenRows != 1) {
                throw new IllegalStateException("게시글 숨김 처리에 실패했습니다.");
            }
        }
    }


}
