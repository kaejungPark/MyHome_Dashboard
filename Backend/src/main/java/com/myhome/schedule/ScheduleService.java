package com.myhome.schedule;

import com.myhome.common.response.PageResponse;
import com.myhome.common.validation.DateValidator;
import com.myhome.common.validation.MonthValidator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.myhome.common.validation.PageValidator;
import com.myhome.common.util.PageUtils;
import java.time.LocalDate;
import java.util.List;

@Service
public class ScheduleService {

    private final ScheduleMapper scheduleMapper;
    private static final String DATE_RANGE_MESSAGE = "마감일은 시작일보다 빠를 수 없습니다.";

    public ScheduleService(ScheduleMapper scheduleMapper) {
        this.scheduleMapper = scheduleMapper;
    }

    /**
     * 로그인한 사용자의 일정만 조회한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<ScheduleResponse> getSchedul(
            Long userId,
            String month,
            int page,
            int size
    ) {
        // 페이지 번호와 페이지 크기를 검증한다.
        PageValidator.validate(page, size);

        LocalDate monthValid = MonthValidator.parseMonth(month).atDay(1);

        // 곱하기 전에 long으로 변환하여 정수 범위 초과를 방지한다.
        long offset = PageUtils.calculateOffset(page, size);

        // 목록과 동일한 조건으로 전체 일정 수를 조회한다.
        long totalCount = scheduleMapper.countAll(userId, monthValid);

        // 나머지가 있으면 마지막 페이지를 하나 추가한다.
        long totalPages = PageUtils.calculateTotalPages(totalCount, size);

        // 조회 결과가 없거나 요청한 페이지가 범위를 넘으면 빈 목록을 반환한다.
        List<ScheduleResponse> items = offset >= totalCount
                ? List.of()
                : scheduleMapper.findPage(userId, monthValid, offset, size);

        return new PageResponse<>(
                items,
                page,
                size,
                totalCount,
                totalPages
        );

    }

    /**
     * 일정을 저장한다.
     * 처리 중 예외가 발생하면 이번 저장을 취소한다.
     */
    @Transactional
    public void createSchedule(Long userId, ScheduleSaveRequest request) {

        // 저장 전에 시작일과 마감일의 순서를 검증한다.
        DateValidator.validateDateRange(request.startDate(), request.dueDate(), DATE_RANGE_MESSAGE);

        int insertRow = scheduleMapper.insert(userId, request);

        // 정상 등록은 1행이다. 다른 결과이면 예외를 발생시켜 롤백한다.
        if (insertRow != 1) {
            throw new IllegalStateException("등록 중 오류가 발생했습니다.");
        }

    }

    /**
     * 일정을 수정한다.
     * 처리 중 예외가 발생하면 이번 저장을 취소한다.
     */
    @Transactional
    public void updateSchedule(Long userId, Long id, ScheduleSaveRequest request) {

        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "수정 중 오류가 발생하였습니다."
            );
        }

        // 저장 전에 시작일과 마감일의 순서를 검증한다.
        DateValidator.validateDateRange(request.startDate(), request.dueDate(), DATE_RANGE_MESSAGE);

        int updateRow =  scheduleMapper.update(id, userId, request);

        if (updateRow == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "일정 수정중 오류가 발생하였습니다."
            );
        }

        if (updateRow != 1) {
            throw new IllegalStateException("일정 수정중 오류가 발생하였습니다.");
        }
    }


    /**
     * 일정을 삭제한다.
     * 대상이 없거나 다른 사용자 소유이면 404를 반환한다.
     */
    @Transactional
    public void deleteSchedule(Long userId, Long id) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "삭제 중 오류가 발생하였습니다."
            );
        }

        int deletedRows = scheduleMapper.delete(id, userId);

        if (deletedRows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "일정 삭제 중 오류가 발생하였습니다."
            );
        }

        if (deletedRows != 1) {
            throw new IllegalStateException("일정 삭제 중 오류가 발생하였습니다.");
        }
    }
}
