package com.myhome.schedule;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class ScheduleService {

    private final ScheduleMapper scheduleMapper;
    private final Long userId;

    public ScheduleService(ScheduleMapper scheduleMapper, @Value("${app.user-id}") Long userId) {
        this.scheduleMapper = scheduleMapper;
        this.userId = userId;
    }

    /**
     * 설정된 사용자의 일정만 조회한다.
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedul() {
        return scheduleMapper.findSchedule(userId);
    }

    /**
     * 일정을 저장한다.
     * 처리 중 예외가 발생하면 이번 저장을 취소한다.
     */
    @Transactional
    public void createSchedule(ScheduleSaveRequest request) {

        // 저장 전에 시작일과 마감일의 순서를 검증한다.
        validateDates(request.startDate(), request.dueDate());

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
    public void updateSchedule(Long id, ScheduleSaveRequest request) {

        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "수정 중 오류가 발생하였습니다."
            );
        }

        validateDates(request.startDate(), request.dueDate());

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
     * 마감일이 시작일보다 빠른지 검증한다.
     * 시작일과 마감일이 같은 경우는 허용한다.
     */
    private void validateDates(LocalDate startDate, LocalDate dueDate) {
        if (dueDate.isBefore(startDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "마감일은 시작일보다 빠를 수 없습니다."
            );
        }
    }


    /**
     * 일정을 삭제한다.
     * 대상이 없거나 다른 사용자 소유이면 404를 반환한다.
     */
    @Transactional
    public void deleteSchedule(Long id) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "올바르지 않은 ID입니다."
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
