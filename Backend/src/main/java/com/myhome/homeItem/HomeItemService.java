package com.myhome.homeItem;

import com.myhome.common.response.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.myhome.common.validation.PageValidator;
import com.myhome.common.util.PageUtils;
import java.util.List;

@Service
public class HomeItemService {

    private final HomeItemMapper homeItemMapper;
    private static final String DATE_RANGE_MESSAGE = "구매일은 보증 종료일보다 빠를 수 없습니다.";

    public HomeItemService(HomeItemMapper homeItemMapper) {
        this.homeItemMapper = homeItemMapper;
    }


    /**
     * 로그인한 사용자의 물품을 조회한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<HomeItemResponse> getHomeItem(
            Long userId,
            String name,
            String category,
            String status,
            int page,
            int size
    ) {
        // 페이지와 페이지 크기를 검증한다.
        PageValidator.validate(page, size);

        // 물품명 앞뒤 공백을 제거한다. 빈 문자열이면 검색 조건을 적용하지 않는다.
        String searchName = name == null ? "" : name.trim();

        // 분류 앞뒤 공백을 제거한다. 빈 문자열이면 검색 조건을 적용하지 않는다.
        String searchCategory = category == null ? "" : category.trim();

        // 미선택이면 전체 상태를 조회한다.
        String statusType = status == null ? "" : status.trim();

        // 예외 처리
        if (!statusType.isEmpty() && !statusType.matches("IN_USE|STORED|DISPOSED")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 상태를 선택해 주세요."
            );
        }

        // 곱하기 전에 long으로 변환하여 정수 범위 초과를 방지한다.
        long offset = PageUtils.calculateOffset(page, size);

        // 목록과 동일한 검색 조건으로 전체 게시글 수를 조회한다.
        long totalCount = homeItemMapper.countAll(userId, searchName, searchCategory, statusType);

        // 나머지가 있으면 마지막 페이지를 하나 추가한다.
        long totalPages = PageUtils.calculateTotalPages(totalCount, size);

        List<HomeItemResponse> items = offset >= totalCount
                ? List.of()
                : homeItemMapper.findPage(userId, searchName, searchCategory, statusType, offset, size);

        return new PageResponse<>(
                items,
                page,
                size,
                totalCount,
                totalPages
        );
    }

    /**
     * 물품을 저장한다.
     * 처리 중 예외가 발생하면 이번 저장을 취소한다.
     */
    @Transactional
    public void createHomeItem(Long userId, HomeItemSaveRequest request) {
        int insertRow = homeItemMapper.insert(userId, request);

        // 정상 등록은 1행이다. 다른 결과이면 예외를 발생시켜 롤백한다.
        if (insertRow != 1) {
            throw new IllegalStateException("등록 중 오류가 발생했습니다.");
        }
    }

    /**
     * 물품을 수정한다.
     * 처리 중 예외가 발생하면 이번 저장을 취소한다.
     */
    @Transactional
    public void updateHomeItem(Long userId, Long id, HomeItemSaveRequest request) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "수정 중 오류가 발생하였습니다."
            );
        }

        int updateRow = homeItemMapper.update(id, userId, request);

        if (updateRow == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "물품 수정중 오류가 발생하였습니다."
            );
        }

        if (updateRow != 1) {
            throw new IllegalStateException("물품 수정중 오류가 발생하였습니다.");
        }
    }

    /**
     * 물품을 삭제한다.
     * 대상이 없거나 다른 사용자 소유이면 404를 반환한다.
     */
    @Transactional
    public void deleteHomeItem(Long userId, Long id) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "삭제 중 오류가 발생하였습니다."
            );
        }

        int deleteRow = homeItemMapper.delete(id, userId);

        if (deleteRow == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "물품 삭제 중 오류가 발생하였습니다."
            );
        }

        if (deleteRow != 1) {
            throw new IllegalStateException("물품 삭제 중 오류가 발생하였습니다.");
        }
    }
}
