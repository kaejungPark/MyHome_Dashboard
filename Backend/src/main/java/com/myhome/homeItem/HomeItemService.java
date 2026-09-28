package com.myhome.homeItem;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class HomeItemService {

    private final HomeItemMapper homeItemMapper;
    private final Long userId;
    private static final String DATE_RANGE_MESSAGE = "구매일은 보증 종료일보다 빠를 수 없습니다.";

    public HomeItemService(HomeItemMapper homeItemMapper, @Value("${app.user--id}") Long userId) {
        this.homeItemMapper = homeItemMapper;
        this.userId = userId;
    }


    /**
     * 설정된 사용자의 물품을 조회한다.
     */
    @Transactional(readOnly = true)
    public List<HomeItemResponse> getHomeItem() {
        return homeItemMapper.findHomeItem(userId);
    }

    /**
     * 물품을 저장한다.
     * 처리 중 예외가 발생하면 이번 저장을 취소한다.
     */
    @Transactional
    public void createHomeItem(HomeItemSaveRequest request) {
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
    public void updateHomeItem(Long id, HomeItemSaveRequest request) {
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
    public void deleteHomeItem(Long id) {
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
