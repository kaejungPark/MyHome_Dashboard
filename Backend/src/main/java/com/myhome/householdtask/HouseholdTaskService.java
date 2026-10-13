package com.myhome.householdtask;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class HouseholdTaskService {

    private final HouseholdTaskMapper householdTaskMapper;

    public HouseholdTaskService (HouseholdTaskMapper householdTaskMapper) {
        this.householdTaskMapper =  householdTaskMapper;
    }

    @Transactional(readOnly = true)
    public List<HouseholdTaskResponse> getTasks(Long userId) {
        return householdTaskMapper.findTasks(userId);
    }

    @Transactional
    public void createTask(Long userId, @Valid HouseholdTaskSaveRequest request) {
        int insertRow = householdTaskMapper.insert(userId, request);

        // 정상 등록은 1행이다. 다른 결과이면 예외를 발생시켜 롤백한다.
        if (insertRow != 1) {
            throw new IllegalStateException("등록 중 오류가 발생했습니다.");
        }
    }

    @Transactional
    public void updateTask(Long userId, Long id, @Valid HouseholdTaskSaveRequest request) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "올바르지 않은 항목 번호입니다."
            );
        }

        int updateRow = householdTaskMapper.update(userId, id, request);

        if (updateRow == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "항목을 찾을 수 없습니다."
            );
        }

        if (updateRow != 1) {
            throw new IllegalStateException("항목 수정중 오류가 발생하였습니다.");
        }
    }

    @Transactional
    public void deleteTask(Long userId, Long id) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "올바르지 않은 항목 번호입니다."
            );
        }

        // 본인 항목을 확인하고, 완료 처리와 삭제가 동시에 진행되지 않도록 잠근다.
        HouseholdTaskResponse task =
                householdTaskMapper.findByIdAndUserIdForUpdate(id, userId);

        if (task == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "항목을 찾을 수 없습니다."
            );
        }

        // 완료 이력 보존을 위해 삭제 대신 비활성화를 안내한다.
        if (householdTaskMapper.existsHistory(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "완료 이력이 있는 항목은 삭제할 수 없습니다. 비활성화해 주세요."
            );
        }

        int deleteRow = householdTaskMapper.delete(userId, id);

        if (deleteRow == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "항목을 찾을 수 없습니다."
            );
        }

        if (deleteRow != 1) {
            throw new IllegalStateException("항목 삭제 중 오류가 발생하였습니다.");
        }
    }

    @Transactional
    public void createComplet(Long userId, Long id, @Valid HouseholdTaskCompleteRequest request) {
        // 관리 항목 ID가 올바른지 검증한다.
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "올바르지 않은 항목 번호입니다."
            );
        }

        // 본인 항목을 조회하고 완료 처리 동안 갱신 잠금을 유지한다.
        HouseholdTaskResponse task = householdTaskMapper.findByIdAndUserIdForUpdate(id, userId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "항목을 찾을 수 없습니다.");
        }

        // 비활성 항목은 완료 처리할 수 없다.
        if (!Boolean.TRUE.equals(task.active())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "비활성 항목은 완료 처리할 수 없습니다."
            );
        }

        // 항목 잠금을 유지한 상태에서 기존 완료일을 확인한다.
        LocalDate lastCompletedDate = householdTaskMapper.findLastCompletedDate(id);
        LocalDate completedDate = request.completedDate();

        if (lastCompletedDate != null) {
            if (completedDate.isEqual(lastCompletedDate)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "해당 날짜에 이미 완료한 항목입니다."
                );
            }

            // 다음 예정일이 이전 완료 기록 기준보다 과거로 되돌아가는 것을 막는다.
            if (completedDate.isBefore(lastCompletedDate)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "마지막 완료일보다 이전 날짜로는 완료 처리할 수 없습니다."
                );
            }
        }

        // 실제 완료일에 저장된 반복 주기를 더해 다음 예정일을 계산한다.
        LocalDate nextDueDate = completedDate.plusDays(task.cycleDays());

        // SQL Server DATE의 저장 범위를 벗어나는 날짜는 반환.
        if (completedDate.isBefore(LocalDate.of(1, 1, 1))
                || nextDueDate.isAfter(LocalDate.of(9999, 12, 31))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "완료일 또는 반복 주기가 지원하는 날짜 범위를 벗어납니다."
            );
        }

        // 이번 완료일과 메모를 이력으로 저장한다.
        int insertedRows = householdTaskMapper.insertHistory(
                id, completedDate, request.memo()
        );

        if (insertedRows != 1) {
            throw new IllegalStateException("완료 이력 저장에 실패했습니다.");
        }

        // 본인 항목의 다음 예정일을 갱신한다. 기존 관리 메모는 유지한다.
        int updatedRows = householdTaskMapper.updateNextDueDate(
                userId, id, nextDueDate
        );
        if (updatedRows != 1) {
            throw new IllegalStateException("다음 예정일 갱신에 실패했습니다.");
        }

    }

    /**
     * 본인 관리 항목의 완료 이력을 최신순으로 조회한다.
     * 항목이 없거나 다른 사용자 소유이면 404를 반환한다.
     * 비활성 항목도 기존 완료 이력은 조회할 수 있다.
     */
    @Transactional(readOnly = true)
    public List<HouseholdTaskHistoryResponse> getHistorys(Long userId, Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 항목 번호입니다."
            );
        }

        // 이력 조회 전에 관리 항목의 소유 여부를 확인한다.
        if (householdTaskMapper.findByIdAndUserId(id, userId) == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "항목을 찾을 수 없습니다."
            );
        }

        return householdTaskMapper.findHistories(id);
    }
}
