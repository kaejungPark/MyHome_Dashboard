package com.myhome.householdtask;

import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리 항목과 완료 이력을 조회·저장하는 SQL을 실행한다.
 * 관리 항목은 HOUSEHOLD_TASK, 완료 기록은 HOUSEHOLD_TASK_HISTORY에 저장한다.
 * 하나의 관리 항목에 여러 완료 이력이 연결될 수 있다.
 */
@Mapper
public interface HouseholdTaskMapper {

    /**
     * 로그인한 사용자의 항목 중 이름·유형·사용 여부 조건에 맞는 전체 건수를 조회한다.
     * active가 null이면 사용 여부로 제한하지 않고, false이면 비활성 항목만 조회한다.
     * 페이지 정보가 목록과 일치하도록 findPage와 동일한 검색 조건을 사용한다.
     */
    @Select("""
            <script>
            SELECT COUNT_BIG(*)
            FROM dbo.HOUSEHOLD_TASK t
            WHERE t.USER_ID = #{userId}
             <if test="searchName != null and searchName != ''">
                AND t.NAME LIKE CONCAT('%', #{searchName}, '%')
             </if>
             <if test="searchType != null and searchType != ''">
                AND t.TASK_TYPE = #{searchType}
             </if>
             <if test="active != null">
                 AND t.IS_ACTIVE = #{active}
             </if>
            </script>
    """)
    long countAll(
            @Param("userId") Long userId,
            @Param("searchName") String searchName,
            @Param("searchType") String searchType,
            @Param("active") Boolean active
    );

    /**
     * 사용 중인 항목을 먼저 표시하고, 다음 예정일이 빠른 순서로 조회한다.
     * 사용 여부와 예정일이 같으면 ID 내림차순으로 정렬한다.
     * offset만큼 건너뛴 뒤 size개까지 가져온다.
     */
    @Select("""
            <script>
            SELECT
                t.ID AS id,
                t.NAME AS name,
                t.TASK_TYPE AS taskType,
                t.CYCLE_DAYS AS cycleDays,
                t.NEXT_DUE_DATE AS nextDueDate,
                t.IS_ACTIVE AS active,
                t.MEMO AS memo
            FROM dbo.HOUSEHOLD_TASK t
            WHERE t.USER_ID = #{userId}
             <if test="searchName != null and searchName != ''">
                AND t.NAME LIKE CONCAT('%', #{searchName}, '%')
             </if>
             <if test="searchType != null and searchType != ''">
                AND t.TASK_TYPE = #{searchType}
             </if>
             <if test="active != null">
                 AND t.IS_ACTIVE = #{active}
             </if>
            ORDER BY t.IS_ACTIVE DESC, t.NEXT_DUE_DATE ASC, t.ID DESC
            OFFSET #{offset} ROWS
            FETCH NEXT #{size} ROWS ONLY
            </script>
    """)
    List<HouseholdTaskResponse> findPage(
            @Param("userId") Long userId,
            @Param("searchName") String searchName,
            @Param("searchType") String searchType,
            @Param("active") Boolean active,
            @Param("offset") long offset,
            @Param("size") int size
    );

    /**
     * 관리 항목을 등록하고 저장된 행 수를 반환한다.
     * 선택 입력인 메모는 null을 허용한다.
     * 생성·수정 일시는 DB 기본값을 사용한다.
     */
    @Insert("""
        INSERT INTO dbo.HOUSEHOLD_TASK (
            USER_ID,
            TASK_TYPE,
            NAME,
            CYCLE_DAYS,
            NEXT_DUE_DATE,
            IS_ACTIVE,
            MEMO
        )
        VALUES (
            #{userId},
            #{request.taskType},
            #{request.name},
            #{request.cycleDays},
            #{request.nextDueDate,jdbcType=DATE},
            #{request.active},
            #{request.memo,jdbcType=NVARCHAR}
        )
        """)
    int insert(
            @Param("userId") Long userId,
            @Param("request") HouseholdTaskSaveRequest request);

    /**
     * 관리 항목 ID와 사용자 ID가 모두 일치하는 항목만 수정한다.
     * 생성 일시는 유지하고 수정 일시를 현재 시각으로 갱신한다.
     */
    @Update("""
        UPDATE dbo.HOUSEHOLD_TASK
        SET TASK_TYPE = #{request.taskType},
            NAME = #{request.name},
            CYCLE_DAYS = #{request.cycleDays},
            NEXT_DUE_DATE = #{request.nextDueDate,jdbcType=DATE},
            IS_ACTIVE = #{request.active},
            MEMO = #{request.memo,jdbcType=NVARCHAR},
            UPDATED_DT = SYSUTCDATETIME()
        WHERE ID = #{id}
          AND USER_ID = #{userId}
        """)
    int update(
            @Param("userId") Long userId,
            @Param("id") Long id,
            @Param("request") HouseholdTaskSaveRequest request
    );

    /**
     * 관리 항목에 완료 이력이 하나라도 있는지 확인한다.
     * 이력이 있는 항목의 삭제를 제한하는 데 사용한다.
     */
    @Select("""
        SELECT CASE WHEN EXISTS (
            SELECT 1
            FROM dbo.HOUSEHOLD_TASK_HISTORY
            WHERE TASK_ID = #{id}
        ) THEN 1 ELSE 0 END
        """)
    boolean existsHistory(@Param("id") Long id);

    /**
     * 관리 항목 ID와 사용자 ID가 모두 일치하는 항목만 삭제한다.
     * 조건에 맞는 항목이 없으면 0을 반환한다.
     */
    @Delete("""
        DELETE FROM dbo.HOUSEHOLD_TASK
        WHERE ID = #{id}
          AND USER_ID = #{userId}
        """)
    int delete(
            @Param("userId") Long userId,
            @Param("id") Long id
    );

    /**
     * 관리 항목 ID와 사용자 ID가 일치하는 항목을 조회하고 갱신 잠금을 유지한다.
     * 완료 처리나 삭제 전에 사용하여 같은 항목에 대한 처리가 충돌하지 않도록 한다.
     * 서비스의 트랜잭션 안에서 호출하며, 항목이 없으면 null을 반환한다.
     */
    @Select("""
        SELECT
            t.ID AS id,
            t.NAME AS name,
            t.TASK_TYPE AS taskType,
            t.CYCLE_DAYS AS cycleDays,
            t.NEXT_DUE_DATE AS nextDueDate,
            t.IS_ACTIVE AS active,
            t.MEMO AS memo
        FROM dbo.HOUSEHOLD_TASK t WITH (UPDLOCK, HOLDLOCK)
        WHERE t.ID = #{id}
          AND t.USER_ID = #{userId}
        """)
    HouseholdTaskResponse findByIdAndUserIdForUpdate(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    /**
     * 관리 항목의 가장 최근 완료일을 조회한다.
     * 완료 이력이 없으면 null을 반환한다.
     */
    @Select("""
        SELECT MAX(COMPLETED_DATE)
        FROM dbo.HOUSEHOLD_TASK_HISTORY
        WHERE TASK_ID = #{id}
        """)
    LocalDate findLastCompletedDate(@Param("id") Long id);

    /**
     * 관리 항목의 실제 완료일과 이번 작업 메모를 저장한다.
     * 소유 여부와 완료 가능 여부는 서비스에서 먼저 확인한다.
     */
    @Insert("""
        INSERT INTO dbo.HOUSEHOLD_TASK_HISTORY (
            TASK_ID,
            COMPLETED_DATE,
            MEMO
        )
        VALUES (
            #{id},
            #{completedDate,jdbcType=DATE},
            #{memo,jdbcType=NVARCHAR}
        )
        """)
    int insertHistory(
            @Param("id") Long id,
            @Param("completedDate") LocalDate completedDate,
            @Param("memo") String memo
    );

    /**
     * 본인 관리 항목의 다음 예정일과 수정 일시를 갱신한다.
     * 항목명·반복 주기·관리 메모는 변경하지 않는다.
     */
    @Update("""
        UPDATE dbo.HOUSEHOLD_TASK
        SET NEXT_DUE_DATE = #{nextDueDate,jdbcType=DATE},
            UPDATED_DT = SYSUTCDATETIME()
        WHERE ID = #{id}
          AND USER_ID = #{userId}
        """)
    int updateNextDueDate(
            @Param("userId") Long userId,
            @Param("id") Long id,
            @Param("nextDueDate") LocalDate nextDueDate
    );

    /**
     * 본인 관리 항목을 조회한다.
     * 읽기 전용 확인에 사용하며 갱신 잠금을 걸지 않는다.
     */
    @Select("""
        SELECT
            ID AS id,
            NAME AS name,
            TASK_TYPE AS taskType,
            CYCLE_DAYS AS cycleDays,
            NEXT_DUE_DATE AS nextDueDate,
            IS_ACTIVE AS active,
            MEMO AS memo
        FROM dbo.HOUSEHOLD_TASK
        WHERE ID = #{id}
          AND USER_ID = #{userId}
        """)
    HouseholdTaskResponse findByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    /**
     * 지정한 관리 항목에 연결된 완료 이력의 전체 건수를 조회한다.
     * 사용자 ID를 직접 받지 않으므로 서비스에서 항목의 소유 여부를 먼저 확인해야 한다.
     */
    @Select("""
        SELECT COUNT_BIG(*)
        FROM dbo.HOUSEHOLD_TASK_HISTORY
        WHERE TASK_ID = #{id}
    """)
    long countAllHistrory(@Param("id") Long id);

    /**
     * 지정한 관리 항목의 완료 이력을 페이지 단위로 조회한다.
     * 완료일이 최신인 기록부터 표시하고, 같은 완료일이면 ID 내림차순으로 정렬한다.
     * 서비스에서 본인 소유 항목임을 확인한 뒤 호출한다.
     */
    @Select("""
        SELECT
            ID AS id,
            TASK_ID AS taskId,
            COMPLETED_DATE AS completedDate,
            MEMO AS memo,
            CREATED_DT AS createdDt
        FROM dbo.HOUSEHOLD_TASK_HISTORY
        WHERE TASK_ID = #{id}
        ORDER BY COMPLETED_DATE DESC, ID DESC
        OFFSET #{offset} ROWS
        FETCH NEXT #{size} ROWS ONLY
    """)
    List<HouseholdTaskHistoryResponse> findPageHistrory(
            @Param("id") Long id,
            @Param("offset") long offset,
            @Param("size") int size
    );
}
