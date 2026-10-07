package com.myhome.community;

import com.myhome.common.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CommunityService {

    private final CommunityMapper communityMapper;

    public CommunityService(CommunityMapper communityMapper)  {
        this.communityMapper = communityMapper;
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
        // 페이지와 페이지 크기를 검증한다.
        if (page < 1 || size < 1 || size > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "페이지는 1 이상, 페이지 크기는 1~50이어야 합니다."
            );
        }

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
        long offset = (long) (page - 1) * size;

        // 목록과 동일한 검색 조건으로 전체 게시글 수를 조회한다.
        long totalCount = communityMapper.countAll(type, searchKeyword);

        // 나머지가 있으면 마지막 페이지를 하나 추가한다.
        long totalPages = totalCount / size
                + (totalCount % size == 0 ? 0 : 1);

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
     * 조회수를 1 증가시킨 뒤 게시글 상세 정보를 반환한다.
     * 대상이 없으면 404를 반환하며, 처리 중 예외가 발생하면 조회수 증가도 롤백한다.
     */
    @Transactional
    public CommunityDetailResponse getCommunity(Long id) {
        int updatedRows = communityMapper.increaseViewCount(id);

        // 없는 게시글은 변경된 행이 없으므로 404를 반환한다.
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

        if (community == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "게시글을 찾을 수 없습니다."
            );
        }

        return community;
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
     * 로그인한 사용자가 작성한 게시글의 제목과 본문을 수정한다.
     * 대상이 없거나 다른 사용자의 글이면 404를 반환한다.
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
     * 로그인한 사용자가 작성한 게시글을 삭제한다.
     * 대상이 없거나 다른 사용자의 글이면 404를 반환한다.
     */
    @Transactional
    public void deleteCommunity(Long userId, Long id) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "삭제 중 오류가 발생하였습니다."
            );
        }

        int deleteRow = communityMapper.delete(userId, id);

        if (deleteRow == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "커뮤니티 삭제 중 오류가 발생하였습니다."
            );
        }

        if (deleteRow != 1) {
            throw new IllegalStateException("커뮤니티 삭제 중 오류가 발생하였습니다.");
        }
    }


}
