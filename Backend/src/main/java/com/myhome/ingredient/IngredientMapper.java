package com.myhome.ingredient;

import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 식재료 정보를 조회·저장하는 SQL을 실행한다.
 * 조회와 변경 조건에 사용자 ID를 포함하여 사용자별 데이터를 구분한다.
 */
@Mapper
public interface IngredientMapper {

    /**
     * 검색 조건에 맞는 본인 식재료의 전체 건수를 조회한다.
     * 이름은 입력한 글자가 포함된 항목을 찾고, 보관 장소와 상태는 정확히 일치하는 값을 찾는다.
     * 페이지 수가 목록과 일치하도록 findPage와 동일한 검색 조건을 사용한다.
     */
    @Select("""
        <script>
         SELECT COUNT_BIG(*)
         FROM dbo.INGREDIENT i
         WHERE i.USER_ID = #{userId}
           <if test="searchKeyword != null and searchKeyword != ''">
               AND i.NAME LIKE CONCAT('%', #{searchKeyword}, '%')
           </if>
           <if test="location != null and location != ''">
               AND i.STORAGE_LOCATION = #{location}
           </if>
           <if test="statusType != null and statusType != ''">
               AND i.STATUS = #{statusType}
           </if>
         </script>
    """)
    long countAll(
            @Param("userId") Long userId,
            @Param("searchKeyword") String searchKeyword,
            @Param("location") String location,
            @Param("statusType") String statusType);

    /**
     * 검색 조건에 맞는 식재료를 등록일 최신순으로 조회한다.
     * 등록일이 같으면 ID 내림차순으로 정렬하여 조회 순서를 일정하게 유지한다.
     * offset만큼 건너뛴 뒤 size개까지 가져온다.
     * 소비기한이 가까운 순서가 아닌 등록 순서로 표시하는 목록이다.
     */
    @Select("""
        <script>
        SELECT
            i.ID AS id,
            i.NAME AS name,
            i.QUANTITY AS quantity,
            i.UNIT AS unit,
            i.STORAGE_LOCATION AS storageLocation,
            i.PURCHASE_DATE AS purchaseDate,
            i.EXPIRATION_DATE AS expirationDate,
            i.STATUS AS status,
            i.MEMO AS memo
        FROM dbo.INGREDIENT i
        WHERE i.USER_ID = #{userId}
            <if test="searchKeyword != null and searchKeyword != ''">
                AND i.NAME LIKE CONCAT('%', #{searchKeyword}, '%')
            </if>
            <if test="location != null and location != ''">
                AND i.STORAGE_LOCATION = #{location}
            </if>
            <if test="statusType != null and statusType != ''">
                AND i.STATUS = #{statusType}
            </if>
            ORDER BY i.CREATED_DT DESC, i.ID DESC
            OFFSET #{offset} ROWS
            FETCH NEXT #{size} ROWS ONLY
        </script>
        """)
    List<IngredientResponse> findPage(
            @Param("userId") Long userId,
            @Param("searchKeyword") String searchKeyword,
            @Param("location") String location,
            @Param("statusType") String statusType,
            @Param("offset") long offset,
            @Param("size") int size
    );

    /**
     * 식재료를 등록하고 저장된 행 수를 반환한다.
     * 선택 입력인 날짜와 메모는 null을 허용한다.
     * 생성·수정 일시는 DB 기본값을 사용한다.
     */
    @Insert("""
            INSERT INTO dbo.INGREDIENT (
                USER_ID,
                NAME,
                QUANTITY,
                UNIT,
                STORAGE_LOCATION,
                PURCHASE_DATE,
                EXPIRATION_DATE,
                STATUS,
                MEMO
            )
            VALUES (
                #{userId},
                #{request.name},
                #{request.quantity},
                #{request.unit},
                #{request.storageLocation,jdbcType=VARCHAR},
                #{request.purchaseDate,jdbcType=DATE},
                #{request.expirationDate,jdbcType=DATE},
                #{request.status},
                #{request.memo,jdbcType=NVARCHAR}
            )
            """)
    int insert(
            @Param("userId") Long userId,
            @Param("request") IngredientSaveRequest request);

    /**
     * 식재료 ID와 사용자 ID가 모두 일치하는 항목만 수정한다.
     * 생성 일시는 유지하고 수정 일시를 현재 UTC 시각으로 갱신한다.
     */
    @Update("""
            UPDATE dbo.INGREDIENT
            SET NAME = #{request.name},
                QUANTITY = #{request.quantity},
                UNIT = #{request.unit},
                STORAGE_LOCATION = #{request.storageLocation,jdbcType=VARCHAR},
                PURCHASE_DATE = #{request.purchaseDate,jdbcType=DATE},
                EXPIRATION_DATE = #{request.expirationDate,jdbcType=DATE},
                STATUS = #{request.status},
                MEMO = #{request.memo,jdbcType=NVARCHAR},
                UPDATED_DT = SYSUTCDATETIME()
            WHERE ID = #{id}
              AND USER_ID = #{userId}
            """)
    int update(
            @Param("userId") Long userId,
            @Param("id") Long id,
            @Param("request") IngredientSaveRequest request
    );

    /**
     * 식재료 ID와 사용자 ID가 모두 일치하는 항목만 삭제한다.
     * 조건에 맞는 항목이 없으면 0을 반환한다.
     */
    @Delete("""
            DELETE FROM dbo.INGREDIENT
            WHERE ID = #{id}
              AND USER_ID = #{userId}
            """)
    int delete(
            @Param("userId") Long userId,
            @Param("id") Long id
    );


}
