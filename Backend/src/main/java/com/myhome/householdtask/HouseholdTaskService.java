package com.myhome.householdtask;

import com.myhome.common.response.PageResponse;
import com.myhome.common.validation.PageValidator;
import com.myhome.common.util.PageUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/**
 * 집안일·소모품 관리 항목의 조회·등록·수정·삭제와 완료 처리를 담당한다.
 * 완료하면 이력을 남기고 실제 완료일에 반복 주기를 더해 다음 예정일을 갱신한다.
 * 완료 이력이 있는 항목은 기록 보존을 위해 삭제를 제한한다.
 */
@Service
public class HouseholdTaskService {

    private final HouseholdTaskMapper householdTaskMapper;

    public HouseholdTaskService (HouseholdTaskMapper householdTaskMapper) {
        this.householdTaskMapper =  householdTaskMapper;
    }

    /**
     * 검색 조건과 페이지 정보를 확인한 뒤 본인의 관리 항목을 조회한다.
     * 목록과 동일한 조건으로 전체 건수를 구해 페이지 정보를 반환한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<HouseholdTaskResponse> getTasks(
            Long userId,
            String name,
            String taskType,
            Boolean active,
            int page,
            int size
    ) {
        // 페이지와 페이지 크기를 검증한다.
        PageValidator.validate(page, size);

        // 항목명 앞뒤 공백을 제거한다. 빈 문자열이면 검색 조건을 적용하지 않는다.
        String searchName = name == null ? "" : name.trim();

        // 미선택이면 집안일과 소모품을 모두 조회한다.
        String searchType = taskType == null ? "" : taskType.trim();

        // 전체 조회용 빈 값 또는 집안일·소모품 유형만 허용한다.
        if (!searchType.isEmpty() && !searchType.matches("CHORE|CONSUMABLE")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 유형을 선택해 주세요."
            );
        }

        // 현재 페이지를 조회하기 전에 건너뛸 데이터 수를 계산한다.
        long offset = PageUtils.calculateOffset(page, size);

        // 목록과 동일한 검색 조건으로 전체 관리 항목 수를 조회한다.
        long totalCount = householdTaskMapper.countAll(userId, searchName, searchType, active);

        // 나머지가 있으면 마지막 페이지를 하나 추가한다.
        long totalPages = PageUtils.calculateTotalPages(totalCount, size);

        List<HouseholdTaskResponse> items = offset >= totalCount
                ? List.of()
                : householdTaskMapper.findPage(userId, searchName, searchType, active, offset, size);

        return new PageResponse<>(
                items,
                page,
                size,
                totalCount,
                totalPages
        );
    }

    /**
     * 로그인한 사용자 소유로 관리 항목을 등록한다.
     * 정확히 한 건이 저장되지 않으면 예외를 발생시켜 DB 변경을 취소한다.
     */
    @Transactional
    public void createTask(Long userId, @Valid HouseholdTaskSaveRequest request) {
        int insertRow = householdTaskMapper.insert(userId, request);

        // 정상 등록은 1행이다. 다른 결과이면 예외를 발생시켜 롤백한다.
        if (insertRow != 1) {
            throw new IllegalStateException("등록 중 오류가 발생했습니다.");
        }
    }

    /**
     * 본인 소유 관리 항목의 이름·유형·반복 주기·예정일·사용 여부를 수정한다.
     * 관리 항목의 정보를 변경해도 기존 완료 이력은 변경하지 않는다.
     * 대상이 없거나 다른 사용자 소유이면 404를 반환한다.
     */
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

    /**
     * 본인 소유 항목인지 확인한 뒤 완료 이력이 없는 경우에만 삭제한다.
     * 확인하는 동안 항목을 잠가 완료 처리와 삭제가 동시에 진행되는 것을 막는다.
     * 완료 이력이 있으면 삭제 대신 비활성화를 안내한다.
     */
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

    /**
     * 본인 소유의 활성 항목에 완료 이력을 추가하고 다음 예정일을 갱신한다.
     * 완료일은 마지막 완료일보다 이후여야 하며, 같은 날짜의 중복 완료는 허용하지 않는다.
     * 이력 저장과 예정일 갱신은 하나의 트랜잭션으로 처리하여 함께 성공하거나 취소되도록 한다.
     */
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

        // 기존 예정일이 아닌 실제 완료일을 기준으로 다음 예정일을 계산한다.
        // 예: 11월 2일에 완료하고 반복 주기가 14일이면 다음 예정일은 11월 16일이다.
        LocalDate nextDueDate = completedDate.plusDays(task.cycleDays());

        // 완료일이나 계산된 다음 예정일이 SQL Server DATE 저장 범위를 벗어나면 요청을 거절한다.
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
    public PageResponse<HouseholdTaskHistoryResponse> getHistorys(
            Long userId,
            Long id,
            int page,
            int size
    ) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 항목 번호입니다."
            );
        }

        // 페이지와 페이지 크기를 검증한다.
        PageValidator.validate(page, size);

        // 이력 조회 전에 관리 항목의 소유 여부를 확인한다.
        if (householdTaskMapper.findByIdAndUserId(id, userId) == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "항목을 찾을 수 없습니다."
            );
        }

        // 곱하기 전에 long으로 변환하여 정수 범위 초과를 방지한다.
        long offset = PageUtils.calculateOffset(page, size);

        // 소유 여부를 확인한 관리 항목의 전체 완료 이력 수를 조회한다.
        long totalCount = householdTaskMapper.countAllHistrory(id);

        // 나머지가 있으면 마지막 페이지를 하나 추가한다.
        long totalPages = PageUtils.calculateTotalPages(totalCount, size);

        List<HouseholdTaskHistoryResponse> items = offset >= totalCount
                ? List.of()
                : householdTaskMapper.findPageHistrory(id, offset, size);

        return new PageResponse<>(
                items,
                page,
                size,
                totalCount,
                totalPages
        );

    }
}
