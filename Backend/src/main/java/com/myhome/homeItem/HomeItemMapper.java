package com.myhome.homeItem;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface HomeItemMapper {

    /**
     * 로그인한 사용자의 물품 중 물품명·물품 분류·상태 조건에 맞는 전체 개수를 조회한다.
     * 검색어와 필터가 비어 있으면 해당 조건을 적용하지 않는다.
     */
    @Select("""
            <script>
            SELECT COUNT_BIG(*)
            FROM dbo.HOME_ITEM h
            WHERE h.USER_ID = #{userId}
             <if test="searchName != null and searchName != ''">
                AND h.NAME LIKE CONCAT('%', #{searchName}, '%')
             </if>
             <if test="searchCategory != null and searchCategory != ''">
                AND h.CATEGORY LIKE CONCAT('%', #{searchCategory}, '%')
             </if>
             <if test="statusType != null and statusType != ''">
                AND h.STATUS = #{statusType}
             </if>
            </script>
    """)
    long countAll(
            @Param("userId") Long userId,
            @Param("searchName") String searchName,
            @Param("searchCategory") String searchCategory,
            @Param("statusType") String statusType
    );

    /**
     * 현재 사용자의 물품을 등록일과 ID 기준 최신순으로 조회한다.
     */
    @Select("""
            <script>
            SELECT
                h.ID AS id,
                h.NAME AS name,
                h.CATEGORY AS category,
                h.PURCHASE_DATE AS purchaseDate,
                h.PURCHASE_PRICE AS purchasePrice,
                h.WARRANTY_END_DATE AS warrantyEndDate,
                h.MAINTENANCE_CYCLE_DAYS AS maintenanceCycle,
                h.STATUS AS status,
                h.MEMO AS memo
            FROM dbo.HOME_ITEM h
            WHERE h.USER_ID = #{userId}
             <if test="searchName != null and searchName != ''">
                AND h.NAME LIKE CONCAT('%', #{searchName}, '%')
             </if>
             <if test="searchCategory != null and searchCategory != ''">
                AND h.CATEGORY LIKE CONCAT('%', #{searchCategory}, '%')
             </if>
             <if test="statusType != null and statusType != ''">
                AND h.STATUS = #{statusType}
             </if>
            ORDER BY h.CREATED_DT DESC, h.ID DESC
            OFFSET #{offset} ROWS
            FETCH NEXT #{size} ROWS ONLY
            </script>
            """)
    List<HomeItemResponse> findPage(
            @Param("userId") Long userId,
            @Param("searchName") String searchName,
            @Param("searchCategory") String searchCategory,
            @Param("statusType") String statusType,
            @Param("offset") long offset,
            @Param("size") int size
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
