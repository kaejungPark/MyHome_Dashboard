package com.myhome.homeItem;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface HomeItemMapper {
    /**
     * 현재 사용자의 물품 목록을 최신 등록 순서로 조회한다.
     * 컬럼 순서는 HomeItemResponse의 생성자 인자 순서에 맞춘다.
     */
    @Select("""
            SELECT
                ID AS id,
                NAME AS name,
                CATEGORY AS category,
                PURCHASE_DATE AS purchaseDate,
                PURCHASE_PRICE AS purchasePrice,
                WARRANTY_END_DATE AS warrantyEndDate,
                MAINTENANCE_CYCLE_DAYS AS maintenanceCycle,
                STATUS AS status,
                MEMO AS memo
            FROM dbo.HOME_ITEM
            WHERE USER_ID = #{userId}
            ORDER BY ID DESC
            """)
    List<HomeItemResponse> findHomeItem(
            @Param("userId") Long userId
    );

    /**
     * 현재 사용자의 물품을 등록하고 저장된 행 수를 반환한다.
     * 선택 입력 항목은 null을 전달할 수 있도록 JDBC 타입을 지정한다.
     * 생성·수정 일시는 DB의 기본값을 사용한다.
     */
    @Insert("""
            INSERT INTO dbo.HOME_ITEM (
                USER_ID,
                NAME,
                CATEGORY,
                PURCHASE_DATE,
                PURCHASE_PRICE,
                WARRANTY_END_DATE,
                MAINTENANCE_CYCLE_DAYS,
                STATUS,
                MEMO
            )
            VALUES (
                #{userId},
                #{request.name},
                #{request.category,jdbcType=NVARCHAR},
                #{request.purchaseDate,jdbcType=DATE},
                #{request.purchasePrice,jdbcType=DECIMAL},
                #{request.warrantyEndDate,jdbcType=DATE},
                #{request.maintenanceCycle,jdbcType=INTEGER},
                #{request.status},
                #{request.memo,jdbcType=NVARCHAR}
            )
            """)
    int insert(
            @Param("userId") Long userId,
            @Param("request") HomeItemSaveRequest request
    );

    /**
     * ID와 사용자 ID가 모두 일치하는 물품을 수정한다.
     * 생성 일시는 유지하고 수정 일시를 현재 UTC 시각으로 갱신한다.
     * 수정된 행 수를 반환한다.
     */
    @Update("""
            UPDATE dbo.HOME_ITEM
            SET NAME = #{request.name},
                CATEGORY = #{request.category,jdbcType=NVARCHAR},
                PURCHASE_DATE = #{request.purchaseDate,jdbcType=DATE},
                PURCHASE_PRICE = #{request.purchasePrice,jdbcType=DECIMAL},
                WARRANTY_END_DATE = #{request.warrantyEndDate,jdbcType=DATE},
                MAINTENANCE_CYCLE_DAYS = #{request.maintenanceCycle,jdbcType=INTEGER},
                STATUS = #{request.status},
                MEMO = #{request.memo,jdbcType=NVARCHAR},
                UPDATED_DT = SYSUTCDATETIME()
            WHERE ID = #{id}
              AND USER_ID = #{userId}
            """)
    int update(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("request") HomeItemSaveRequest request
    );

    /**
     * ID와 사용자 ID가 모두 일치하는 물품을 삭제한다.
     * 삭제된 행 수를 반환하며, 대상이 없으면 0을 반환한다.
     */
    @Delete("""
            DELETE FROM dbo.HOME_ITEM
            WHERE ID = #{id}
              AND USER_ID = #{userId}
            """)
    int delete(
            @Param("id") Long id,
            @Param("userId") Long userId
    );
}
