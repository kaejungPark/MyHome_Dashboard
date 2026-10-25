package com.myhome.community;

import com.myhome.common.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DuplicateKeyException;
import com.myhome.common.validation.PageValidator;
import com.myhome.common.util.PageUtils;
import com.myhome.community.comment.CommentMapper;

import java.util.List;

@Service
public class CommunityService {

    // 게시글 조회·등록·수정·삭제와 신고 이력을 관리한다.
    private final CommunityMapper communityMapper;

    // 게시글 삭제 전 댓글·대댓글을 정리하고 동일한 게시글 잠금을 사용한다.
    private final CommentMapper commentMapper;

    public CommunityService(
            CommunityMapper communityMapper,
            CommentMapper commentMapper
    ) {
        this.communityMapper = communityMapper;
        this.commentMapper = commentMapper;
    }

    /**
     * 검색 조건에 맞는 게시글 목록을 페이지 단위로 조회한다.
     * 검색어가 없으면 전체 목록을 조회하며, 기본 검색 기준은 제목이다.
     */
    @Transactional(readOnly = true)
    public PageResponse<CommunityListResponse> getCommunities(
            int page,
            int size,
            String searchType,
            String keyword
    ) {
        // 페이지 번호와 페이지 크기를 검증한다.
        PageValidator.validate(page, size);

        // 검색 기준을 생략하면 제목 검색을 사용한다.
        String type = searchType == null || searchType.isBlank()
                ? "TITLE"
                : searchType.trim();

        if (!type.equals("TITLE") && !type.equals("NICKNAME")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "검색 기준은 TITLE 또는 NICKNAME이어야 합니다."
            );
        }

        // 검색어 앞뒤 공백을 제거한다. 빈 문자열이면 검색 조건을 적용하지 않는다.
        String searchKeyword = keyword == null ? "" : keyword.trim();

        // 곱하기 전에 long으로 변환하여 정수 범위 초과를 방지한다.
        long offset = PageUtils.calculateOffset(page, size);

        // 목록과 동일한 검색 조건으로 전체 게시글 수를 조회한다.
        long totalCount = communityMapper.countAll(type, searchKeyword);

        // 나머지가 있으면 마지막 페이지를 하나 추가한다.
        long totalPages = PageUtils.calculateTotalPages(totalCount, size);

        // 검색 결과가 없거나 요청한 페이지가 범위를 넘으면 빈 목록을 반환한다.
        List<CommunityListResponse> items = offset >= totalCount
                ? List.of()
                : communityMapper.findPage(type, searchKeyword, offset, size);

        return new PageResponse<>(
                items,
                page,
                size,
                totalCount,
                totalPages
        );
    }

    /**
     * 공개된 게시글의 조회수를 증가시킨 뒤 상세 정보를 반환한다.
     * 게시글이 없거나 숨김 상태이면 404를 반환한다.
     * 상세 조회 중 예외가 발생하면 조회수 증가도 함께 롤백한다.
     */
    @Transactional
    public CommunityDetailResponse getCommunity(Long id) {
        int updatedRows = communityMapper.increaseViewCount(id);

        // 게시글이 없거나 숨김 상태이면 조회수가 변경되지 않으므로 404를 반환한다.
        if (updatedRows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "게시글을 찾을 수 없습니다."
            );
        }

        if (updatedRows != 1) {
            throw new IllegalStateException("조회수 처리 중 오류가 발생했습니다.");
        }

        // 증가된 조회수가 포함된 상세 정보를 조회한다.
        CommunityDetailResponse community = communityMapper.findCommunity(id);

        if (community == null || community.isHidden()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "없거나 숨김 처리된 게시글입니다."
            );
        }

        return community;
    }

    /**
     * 수정 화면에 필요한 본인 게시글 정보를 조회한다.
     * 게시글이 없거나 숨김 상태이거나 다른 사용자의 글이면 404를 반환한다.
     * 수정 준비를 위한 조회이므로 조회수는 증가시키지 않는다.
     */
    @Transactional(readOnly = true)
    public CommunityDetailResponse getEdit(Long userId, Long id) {
        // 게시글 ID로 기존 정보를 조회한다.
        CommunityDetailResponse community = communityMapper.findCommunity(id);

        // 게시글이 없으면 404를 반환한다.
        if (community == null || community.isHidden()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "없거나 숨김 처리된 게시글입니다."
            );
        }

        // 로그인 사용자와 작성자가 같으면 반환하고, 다르면 404를 반환한다.
        if (userId.equals(community.userId())) {
            return community;
        } else {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "게시글을 찾을 수 없습니다."
            );
        }
    }

    /**
     * 게시글을 등록하고 저장된 행 수가 1이 아니면 예외를 발생시킨다.
     * */
    @Transactional
    public void createCommunity(Long userId, CommunitySaveRequest request) {
        int insertRow = communityMapper.insert(userId, request);

        // 정상 등록은 1행 다른 결과이면 예외를 발생시켜 롤백한다.
        if (insertRow != 1) {
            throw new IllegalStateException("등록 중 오류가 발생했습니다.");
        }
    }

    /**
     * 로그인한 사용자가 작성한 공개 게시글의 제목과 본문을 수정한다.
     * 게시글이 없거나 숨김 상태이거나 다른 사용자의 글이면 404를 반환한다.
     */
    @Transactional
    public void updateCommunity(Long userId, Long id, @Valid CommunitySaveRequest request) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "수정 중 오류가 발생하였습니다."
            );
        }

        int updateRow = communityMapper.update(userId, id, request);

        if (updateRow == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "커뮤니티 수정중 오류가 발생하였습니다."
            );
        }

        if (updateRow != 1) {
            throw new IllegalStateException("커뮤니티 수정중 오류가 발생하였습니다.");
        }
    }

    /**
     * 로그인한 사용자가 작성한 게시글과 해당 댓글·대댓글을 실제 삭제한다.
     *
     * 게시글이 없거나 숨김 상태이거나 본인 글이 아니면 404를 반환한다.
     * 신고 이력이 있으면 기록 보존을 위해 삭제를 차단하고 409를 반환한다.
     *
     * 처리 순서:
     * 1. 공개 게시글 잠금
     * 2. 작성자와 신고 이력 검사
     * 3. 해당 게시글의 댓글·대댓글 삭제
     * 4. 게시글 삭제
     *
     * 모든 삭제는 하나의 트랜잭션에서 실행한다.
     * 게시글 삭제가 실패하면 앞서 삭제한 댓글도 함께 롤백된다.
     */
    @Transactional
    public void deleteCommunity(Long userId, Long id) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "게시글 ID는 1 이상이어야 합니다."
            );
        }

        /*
         * 댓글 변경 서비스와 동일하게 게시글부터 잠근다.
         * 댓글을 정리하는 도중 새 댓글이 등록되는 상황을 방지한다.
         */
        if (commentMapper.lockVisibleCommunity(id) == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "게시글을 찾을 수 없습니다."
            );
        }

        CommunityDetailResponse community =
                communityMapper.findCommunity(id);

        // 기존 게시글 작성자·숨김 상태 검증을 유지한다.
        if (community == null
                || community.isHidden()
                || !userId.equals(community.userId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "게시글을 찾을 수 없습니다."
            );
        }

        // 댓글을 지우기 전에 기존 신고 이력 보존 정책부터 검사한다.
        if (communityMapper.existsReportHistory(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "신고 이력이 있는 게시글은 삭제할 수 없습니다."
            );
        }

        /*
         * 일반 댓글·대댓글·논리 삭제된 댓글까지 함께 정리한다.
         * 댓글이 없는 게시글은 0건이 정상 결과이므로 1건 여부를 검사하지 않는다.
         */
        commentMapper.deleteByCommunityId(id);

        int deletedRows = communityMapper.delete(userId, id);

        if (deletedRows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "게시글 상태가 변경되어 삭제하지 못했습니다. 다시 조회해 주세요."
            );
        }

        if (deletedRows != 1) {
            throw new IllegalStateException(
                    "게시글 삭제 중 예상하지 못한 처리 건수가 발생했습니다."
            );
        }
    }


    /**
     * 게시글 존재 여부, 숨김 상태, 본인 글 여부와 중복 신고를 검사한 뒤 저장한다.
     * 동시에 접수된 중복 신고도 DB UNIQUE 제약조건을 통해 차단한다.
     */
    @Transactional
    public void createReport(Long userId, Long id, @Valid ReportSaveRequest request) {
        // 게시글 ID로 기존 정보를 조회한다.
        CommunityDetailResponse community = communityMapper.findCommunity(id);

        if (community == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "게시글을 찾을 수 없습니다."
            );
        }

        // 숨김 처리된 게시글은 추가 신고를 받지 않는다.
        if (community.isHidden()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "숨김 처리된 게시글은 신고할 수 없습니다."
            );
        }

        // 로그인한 사용자와 작성자가 같으면 신고를 거절한다.
        if (community.userId().equals(userId))  {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "본인이 작성한 게시글은 신고할 수 없습니다."
            );
        }

        // 처리 상태와 관계없이 같은 사용자가 같은 게시글을 다시 신고하는 것을 차단한다.
        if (communityMapper.existsReport(userId, id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "기존 신고가 존재합니다."
            );
        }

        try {
            int insertRow = communityMapper.insertReport(id, userId, request);

            if (insertRow != 1) {
                throw new IllegalStateException("신고 등록 중 오류가 발생했습니다.");
            }
        } catch (DuplicateKeyException e) {
            // 사전 검사 이후 동시에 들어온 중복 신고도 409로 응답한다.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 신고한 게시글입니다.",
                    e
            );
        }
    }
}
