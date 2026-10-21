package com.myhome.admin;

import com.myhome.common.response.PageResponse;
import com.myhome.common.util.PageUtils;
import com.myhome.common.validation.PageValidator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AdminHiddenCommunityService {

    private final AdminHiddenCommunityMapper adminHiddenCommunityMapper;

    public AdminHiddenCommunityService(
            AdminHiddenCommunityMapper adminHiddenCommunityMapper
    ) {
        this.adminHiddenCommunityMapper = adminHiddenCommunityMapper;
    }

    /**
     * 숨김 게시글 목록을 페이지 조회한다.
     * 조회 범위를 벗어난 페이지는 빈 목록으로 반환한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminHiddenCommunityListResponse> getHiddenCommunities(
            int page,
            int size
    ) {
        // 기존 공통 검증과 계산 로직을 사용한다.
        PageValidator.validate(page, size);

        long offset = PageUtils.calculateOffset(page, size);
        long totalCount = adminHiddenCommunityMapper.countHidden();
        long totalPages = PageUtils.calculateTotalPages(totalCount, size);

        List<AdminHiddenCommunityListResponse> items =
                offset >= totalCount
                        ? List.of()
                        : adminHiddenCommunityMapper.findHiddenPage(offset, size);

        return new PageResponse<>(
                items,
                page,
                size,
                totalCount,
                totalPages
        );
    }

    /**
     * 숨김 게시글의 상세 정보를 조회한다.
     * 관리자 검토용 조회이므로 조회수를 증가시키지 않는다.
     */
    @Transactional(readOnly = true)
    public AdminHiddenCommunityDetailResponse getHiddenCommunity(Long id) {
        validateId(id);

        AdminHiddenCommunityDetailResponse community =
                adminHiddenCommunityMapper.findById(id);

        if (community == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "게시글을 찾을 수 없습니다."
            );
        }

        if (!community.isHidden()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 공개 상태인 게시글입니다."
            );
        }

        return community;
    }

    /**
     * 숨김 게시글을 다시 공개한다.
     * 기존 신고의 처리 상태와 처리 의견은 그대로 유지한다.
     */
    @Transactional
    public void restoreCommunity(Long id) {
        validateId(id);

        // UPDATE 조건에서 숨김 여부를 검사해 동시 중복 해제를 방지한다.
        int updatedRows = adminHiddenCommunityMapper.restore(id);

        if (updatedRows == 0) {
            // 게시글이 삭제된 경우와 이미 공개된 경우를 구분한다.
            if (!adminHiddenCommunityMapper.existsById(id)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "게시글을 찾을 수 없습니다."
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 공개 상태인 게시글입니다."
            );
        }

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "게시글 숨김 해제 행 수가 올바르지 않습니다."
            );
        }
    }

    /**
     * 상세 조회와 숨김 해제에서 사용하는 게시글 ID를 검증한다.
     */
    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 게시글 번호입니다."
            );
        }
    }
}