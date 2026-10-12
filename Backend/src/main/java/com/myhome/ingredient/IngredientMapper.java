package com.myhome.ingredient;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface IngredientMapper {

    /**
     * 현재 사용자의 식재료를 등록일과 ID 기준 최신순으로 조회한다.
     */
    @Select("""
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
        ORDER BY i.CREATED_DT DESC, i.ID DESC
        """)
    List<IngredientResponse> findIngredients(@Param("userId") Long userId);

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
