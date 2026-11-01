package com.myhome.homeItem;

import com.myhome.common.response.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.myhome.common.validation.PageValidator;
import com.myhome.common.util.PageUtils;
import java.util.List;

/**
 * 물품 관리의 조회 조건 검증과 등록·수정·삭제 처리를 담당한다.
 * DB 변경 결과를 확인하여 정상적으로 처리되지 않은 경우 예외를 발생시킨다.
 */
@Service
public class HomeItemService {

    private final HomeItemMapper homeItemMapper;
    private static final String DATE_RANGE_MESSAGE = "보증 종료일은 구매일보다 빠를 수 없습니다.";
    public HomeItemService(HomeItemMapper homeItemMapper) {
        this.homeItemMapper = homeItemMapper;
    }

    /**
     * 검색 조건과 페이지 정보를 확인한 뒤 로그인한 사용자의 물품을 조회한다.
     * 목록과 동일한 조건으로 전체 건수를 구해 페이지 정보를 계산한다.
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

        // 상태를 선택하지 않으면 전체를 조회하고, 선택한 경우에는 허용된 상태인지 확인한다.
        String statusType = status == null ? "" : status.trim();

        // 사용 중·보관 중·처분 이외의 상태값은 허용하지 않는다.
        if (!statusType.isEmpty() && !statusType.matches("IN_USE|STORED|DISPOSED")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 상태를 선택해 주세요."
            );
        }

        // 현재 페이지를 조회하기 전에 건너뛸 데이터 수를 계산한다.
        long offset = PageUtils.calculateOffset(page, size);

        // 목록과 동일한 검색 조건으로 전체 물품 수를 조회한다.
        long totalCount = homeItemMapper.countAll(userId, searchName, searchCategory, statusType);

        // 전체 건수와 페이지 크기로 필요한 페이지 수를 계산한다.
        long totalPages = PageUtils.calculateTotalPages(totalCount, size);

        // 검색 결과가 없거나 요청한 페이지가 범위를 벗어나면 빈 목록을 반환한다.
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
     * 로그인한 사용자 소유로 물품을 저장한다.
     * 정확히 한 건이 저장되지 않으면 예외를 발생시켜 이번 DB 변경을 취소한다.
     */
    @Transactional
    public void createHomeItem(Long userId, HomeItemSaveRequest request) {
        // DB에 저장하기 전에 구매일과 보증 종료일의 순서를 확인한다.
        validateDateRange(request);

        int insertRow = homeItemMapper.insert(userId, request);

        // 정상 등록은 1행이다. 다른 결과이면 예외를 발생시켜 롤백한다.
        if (insertRow != 1) {
            throw new IllegalStateException("등록 중 오류가 발생했습니다.");
        }
    }

    /**
     * 물품 ID가 올바른지 확인한 뒤 본인의 물품 정보를 수정한다.
     * 대상이 없거나 다른 사용자 소유이면 404를 반환한다.
     */
    @Transactional
    public void updateHomeItem(Long userId, Long id, HomeItemSaveRequest request) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "수정 중 오류가 발생하였습니다."
            );
        }

        // 수정할 날짜도 등록할 때와 동일한 기준으로 검사한다.
        validateDateRange(request);

        int updateRow = homeItemMapper.update(id, userId, request);

        // 물품 ID와 사용자 ID가 모두 일치해야 수정되므로 대상이 없거나 본인 소유가 아니면 0건이다.
        if (updateRow == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "물품 수정중 오류가 발생하였습니다."
            );
        }

        // 한 건 수정 요청에서 예상과 다른 처리 건수가 반환되면 예외를 발생시킨다.
        if (updateRow != 1) {
            throw new IllegalStateException("물품 수정중 오류가 발생하였습니다.");
        }
    }

    /**
     * 로그인한 사용자가 소유한 물품을 DB에서 삭제한다.
     * 대상이 없거나 다른 사용자 소유이면 404를 반환한다.
     * 삭제 대신 기록을 남기려면 물품 상태를 처분으로 변경해야 한다.
     */
    @Transactional
    public void deleteHomeItem(Long userId, Long id) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "삭제 중 오류가 발생하였습니다."
            );
        }

        int deleteRow = homeItemMapper.delete(id, userId);

        // 삭제된 행이 없으면 대상이 없거나 본인 소유가 아닌 것으로 처리한다.
        if (deleteRow == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "물품 삭제 중 오류가 발생하였습니다."
            );
        }

        if (deleteRow != 1) {
            throw new IllegalStateException("물품 삭제 중 오류가 발생하였습니다.");
        }
    }

    /**
     * 구매일과 보증 종료일이 모두 입력된 경우 날짜 순서를 검사한다.
     * 선택 입력이므로 둘 중 하나라도 없으면 검사하지 않는다.
     */
    private void validateDateRange(HomeItemSaveRequest request) {
        if (request.purchaseDate() != null
                && request.warrantyEndDate() != null
                && request.warrantyEndDate().isBefore(request.purchaseDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    DATE_RANGE_MESSAGE
            );
        }
    }
}
