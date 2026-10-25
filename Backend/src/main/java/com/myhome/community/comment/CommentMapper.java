package com.myhome.community.comment;

import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 댓글·대댓글의 조회와 데이터 변경을 담당한다.
 *
 * 일반 댓글은 PARENT_ID가 NULL이며,
 * 대댓글은 PARENT_ID에 일반 댓글의 ID를 저장한다.
 *
 * 댓글 삭제는 IS_DELETED를 변경하는 논리 삭제다.
 * 게시글 자체를 삭제할 때만 해당 댓글들을 실제로 삭제한다.
 * existsVisibleCommunity()	목록 조회 전 게시글 존재·공개 여부 확인
 * lockVisibleCommunity()	등록·수정·삭제 전 게시글 잠금
 * findById()	작성자·부모 관계·삭제 상태 검증
 * countRoots()	일반 댓글 페이지 계산
 * findRootPage()	일반 댓글 목록 조회
 * countReplies()	특정 댓글의 대댓글 페이지 계산
 * findReplyPage()	특정 댓글의 대댓글 목록 조회
 * insert()	댓글·대댓글 등록
 * update()	본인 댓글 내용 수정
 * softDelete()	본인 댓글 삭제 표시
 * deleteByCommunityId()	게시글 삭제 전 전체 댓글 정리
 */
@Mapper
public interface CommentMapper {

    /**
     * 공개 게시글의 행에 갱신 잠금을 걸고 ID를 반환한다.
     *
     * 댓글 등록·수정·삭제와 게시글 삭제가 동시에 실행될 때
     * 같은 게시글의 변경 처리를 순서대로 진행하기 위해 사용한다.
     *
     * 반드시 서비스의 쓰기 트랜잭션 안에서 호출해야 한다.
     * 잠금은 트랜잭션이 끝나면 해제된다.
     *
     * @return 공개 게시글 ID, 없거나 숨김 상태이면 null
     */
    @Select("""
        SELECT ID
        FROM dbo.COMMUNITY WITH (UPDLOCK, ROWLOCK)
        WHERE ID = #{communityId}
          AND IS_HIDDEN = 0
        """)
    @Options(
            useCache = false,
            flushCache = Options.FlushCachePolicy.TRUE
    )
    Long lockVisibleCommunity(
            @Param("communityId") Long communityId
    );

    /**
     * 댓글 목록 조회가 가능한 공개 게시글인지 확인한다.
     *
     * 댓글이 0건인 게시글과 존재하지 않는 게시글을 구분하기 위해
     * 서비스에서 목록 조회 전에 사용한다.
     *
     * 댓글 조회만으로 게시글 조회수를 증가시키지는 않는다.
     */
    @Select("""
        SELECT CAST(
            CASE WHEN EXISTS (
                SELECT 1
                FROM dbo.COMMUNITY
                WHERE ID = #{communityId}
                  AND IS_HIDDEN = 0
            ) THEN 1 ELSE 0 END
            AS BIT
        )
        """)
    boolean existsVisibleCommunity(
            @Param("communityId") Long communityId
    );

    /**
     * 특정 게시글에 속한 댓글의 내부 검증 정보를 조회한다.
     *
     * 작성자, 부모 관계, 삭제 상태를 서비스에서 검사할 때 사용한다.
     * 삭제된 댓글도 실제 작성자 ID를 유지하여 반환한다.
     *
     * 게시글 ID와 댓글 ID를 함께 검사하므로
     * 다른 게시글의 댓글 ID를 전달하면 null을 반환한다.
     *
     * 게시글의 공개 여부와 수정 권한은 서비스에서 별도로 검사한다.
     */
    @Select("""
        SELECT
            ID AS id,
            COMMUNITY_ID AS communityId,
            USER_ID AS userId,
            PARENT_ID AS parentId,
            IS_DELETED AS isDeleted
        FROM dbo.COMMUNITY_COMMENT
        WHERE ID = #{commentId}
          AND COMMUNITY_ID = #{communityId}
        """)
    Comment findById(
            @Param("communityId") Long communityId,
            @Param("commentId") Long commentId
    );

    /**
     * 공개 게시글의 일반 댓글 수를 조회한다.
     *
     * 대댓글은 포함하지 않는다.
     * 삭제된 일반 댓글도 삭제 안내 자리와 대댓글 연결을 유지하므로 포함한다.
     *
     * findRootPage와 동일한 조건으로 조회한다.
     */
    @Select("""
        SELECT COUNT_BIG(*)
        FROM dbo.COMMUNITY_COMMENT c
        JOIN dbo.COMMUNITY p ON p.ID = c.COMMUNITY_ID
        WHERE c.COMMUNITY_ID = #{communityId}
          AND c.PARENT_ID IS NULL
          AND p.IS_HIDDEN = 0
        """)
    long countRoots(
            @Param("communityId") Long communityId
    );

    /**
     * 공개 게시글의 일반 댓글을 작성순으로 페이징 조회한다.
     *
     * 삭제된 댓글은 원문과 작성자 정보를 응답에 포함하지 않는다.
     * 삭제 여부만 프론트에 전달하고 원문을 숨기는 방식이 아니라,
     * SQL 조회 단계부터 원문 대신 안내 문구를 반환한다.
     *
     * 동일한 작성 시각에도 순서가 일정하도록 ID를 함께 정렬한다.
     * 작성자 조인으로 댓글이 목록에서 사라지지 않도록 LEFT JOIN을 사용한다.
     */
    @Select("""
        SELECT
            c.ID AS id,
            c.COMMUNITY_ID AS communityId,
            CASE
                WHEN c.IS_DELETED = 1 THEN NULL
                ELSE c.USER_ID
            END AS userId,
            CASE
                WHEN c.IS_DELETED = 1 THEN NULL
                ELSE u.NICKNAME
            END AS nickname,
            c.PARENT_ID AS parentId,
            CASE
                WHEN c.IS_DELETED = 1 THEN N'삭제된 댓글입니다.'
                ELSE c.CONTENT
            END AS content,
            c.IS_DELETED AS isDeleted,
            c.CREATED_DT AS createdDt,
            c.UPDATED_DT AS updatedDt
        FROM dbo.COMMUNITY_COMMENT c
        JOIN dbo.COMMUNITY p ON p.ID = c.COMMUNITY_ID
        LEFT JOIN dbo.[USER] u ON u.ID = c.USER_ID
        WHERE c.COMMUNITY_ID = #{communityId}
          AND c.PARENT_ID IS NULL
          AND p.IS_HIDDEN = 0
        ORDER BY c.CREATED_DT ASC, c.ID ASC
        OFFSET #{offset} ROWS
        FETCH NEXT #{size} ROWS ONLY
        """)
    List<CommentResponse> findRootPage(
            @Param("communityId") Long communityId,
            @Param("offset") long offset,
            @Param("size") int size
    );

    /**
     * 특정 일반 댓글에 속한 대댓글 수를 조회한다.
     *
     * 부모 댓글이 같은 게시글의 일반 댓글인지도 조회 조건으로 확인한다.
     * 부모 댓글이 삭제됐어도 기존 대댓글은 조회할 수 있다.
     * 삭제된 대댓글은 삭제 안내 자리로 표시하므로 건수에 포함한다.
     *
     * findReplyPage와 동일한 조건으로 조회한다.
     */
    @Select("""
        SELECT COUNT_BIG(*)
        FROM dbo.COMMUNITY_COMMENT c
        JOIN dbo.COMMUNITY p ON p.ID = c.COMMUNITY_ID
        JOIN dbo.COMMUNITY_COMMENT parent
          ON parent.ID = c.PARENT_ID
         AND parent.COMMUNITY_ID = c.COMMUNITY_ID
         AND parent.PARENT_ID IS NULL
        WHERE c.COMMUNITY_ID = #{communityId}
          AND c.PARENT_ID = #{parentId}
          AND p.IS_HIDDEN = 0
        """)
    long countReplies(
            @Param("communityId") Long communityId,
            @Param("parentId") Long parentId
    );

    /**
     * 특정 일반 댓글의 대댓글을 작성순으로 페이징 조회한다.
     *
     * 일반 댓글과 대댓글에 각각 페이지·크기를 적용하므로
     * 대댓글이 많은 경우에도 한 번에 전체를 조회하지 않는다.
     *
     * 부모 댓글의 삭제 여부는 조회를 막는 조건으로 사용하지 않는다.
     * 삭제된 부모 아래에 이미 등록된 대댓글은 계속 표시할 수 있다.
     */
    @Select("""
        SELECT
            c.ID AS id,
            c.COMMUNITY_ID AS communityId,
            CASE
                WHEN c.IS_DELETED = 1 THEN NULL
                ELSE c.USER_ID
            END AS userId,
            CASE
                WHEN c.IS_DELETED = 1 THEN NULL
                ELSE u.NICKNAME
            END AS nickname,
            c.PARENT_ID AS parentId,
            CASE
                WHEN c.IS_DELETED = 1 THEN N'삭제된 댓글입니다.'
                ELSE c.CONTENT
            END AS content,
            c.IS_DELETED AS isDeleted,
            c.CREATED_DT AS createdDt,
            c.UPDATED_DT AS updatedDt
        FROM dbo.COMMUNITY_COMMENT c
        JOIN dbo.COMMUNITY p ON p.ID = c.COMMUNITY_ID
        JOIN dbo.COMMUNITY_COMMENT parent
          ON parent.ID = c.PARENT_ID
         AND parent.COMMUNITY_ID = c.COMMUNITY_ID
         AND parent.PARENT_ID IS NULL
        LEFT JOIN dbo.[USER] u ON u.ID = c.USER_ID
        WHERE c.COMMUNITY_ID = #{communityId}
          AND c.PARENT_ID = #{parentId}
          AND p.IS_HIDDEN = 0
        ORDER BY c.CREATED_DT ASC, c.ID ASC
        OFFSET #{offset} ROWS
        FETCH NEXT #{size} ROWS ONLY
        """)
    List<CommentResponse> findReplyPage(
            @Param("communityId") Long communityId,
            @Param("parentId") Long parentId,
            @Param("offset") long offset,
            @Param("size") int size
    );

    /**
     * 일반 댓글 또는 대댓글을 등록한다.
     *
     * parentId가 null이면 일반 댓글이다.
     * 값이 있으면 같은 게시글의 삭제되지 않은 일반 댓글인지 확인한다.
     *
     * 서비스에서 먼저 게시글 잠금과 부모 검증을 수행하고,
     * SQL에서도 공개 여부와 부모 조건을 다시 검사한다.
     *
     * 작성자 ID는 클라이언트 본문이 아닌 로그인 세션에서 전달받는다.
     *
     * @return 등록 성공 시 1, 게시글·부모 조건 불일치 시 0
     */
    @Insert("""
        INSERT INTO dbo.COMMUNITY_COMMENT (
            COMMUNITY_ID,
            USER_ID,
            PARENT_ID,
            CONTENT,
            IS_DELETED,
            CREATED_DT,
            UPDATED_DT
        )
        SELECT
            p.ID,
            #{userId},
            #{parentId,jdbcType=BIGINT},
            #{content,jdbcType=NVARCHAR},
            0,
            SYSUTCDATETIME(),
            SYSUTCDATETIME()
        FROM dbo.COMMUNITY p
        WHERE p.ID = #{communityId}
          AND p.IS_HIDDEN = 0
          AND (
              #{parentId,jdbcType=BIGINT} IS NULL
              OR EXISTS (
                  SELECT 1
                  FROM dbo.COMMUNITY_COMMENT parent
                  WHERE parent.ID = #{parentId,jdbcType=BIGINT}
                    AND parent.COMMUNITY_ID = p.ID
                    AND parent.PARENT_ID IS NULL
                    AND parent.IS_DELETED = 0
              )
          )
        """)
    int insert(
            @Param("communityId") Long communityId,
            @Param("userId") Long userId,
            @Param("parentId") Long parentId,
            @Param("content") String content
    );

    /**
     * 본인이 작성한 삭제되지 않은 댓글의 내용만 수정한다.
     *
     * 소속 게시글·작성자·부모 댓글은 변경하지 않는다.
     * 서비스의 권한 검사와 별개로 SQL에서도 작성자 조건을 확인한다.
     * 숨김 처리된 게시글의 댓글은 수정하지 않는다.
     *
     * @return 수정 성공 시 1, 대상 또는 조건 불일치 시 0
     */
    @Update("""
        UPDATE c
        SET CONTENT = #{content,jdbcType=NVARCHAR},
            UPDATED_DT = SYSUTCDATETIME()
        FROM dbo.COMMUNITY_COMMENT c
        JOIN dbo.COMMUNITY p ON p.ID = c.COMMUNITY_ID
        WHERE c.ID = #{commentId}
          AND c.COMMUNITY_ID = #{communityId}
          AND c.USER_ID = #{userId}
          AND c.IS_DELETED = 0
          AND p.IS_HIDDEN = 0
        """)
    int update(
            @Param("communityId") Long communityId,
            @Param("commentId") Long commentId,
            @Param("userId") Long userId,
            @Param("content") String content
    );

    /**
     * 본인이 작성한 댓글을 논리 삭제한다.
     *
     * 행을 유지하므로 기존 대댓글의 부모 연결이 끊어지지 않는다.
     * 원문은 DB에 남아 있지만 일반 조회에서는 삭제 안내 문구로 대체한다.
     *
     * 이미 삭제된 댓글이나 숨김 게시글의 댓글은 변경하지 않는다.
     *
     * @return 삭제 처리 성공 시 1, 대상 또는 조건 불일치 시 0
     */
    @Update("""
        UPDATE c
        SET IS_DELETED = 1,
            DELETED_DT = SYSUTCDATETIME(),
            UPDATED_DT = SYSUTCDATETIME()
        FROM dbo.COMMUNITY_COMMENT c
        JOIN dbo.COMMUNITY p ON p.ID = c.COMMUNITY_ID
        WHERE c.ID = #{commentId}
          AND c.COMMUNITY_ID = #{communityId}
          AND c.USER_ID = #{userId}
          AND c.IS_DELETED = 0
          AND p.IS_HIDDEN = 0
        """)
    int softDelete(
            @Param("communityId") Long communityId,
            @Param("commentId") Long commentId,
            @Param("userId") Long userId
    );

    /**
     * 게시글 자체를 삭제할 때 해당 댓글·대댓글을 모두 실제 삭제한다.
     *
     * 일반 댓글 삭제 API에서는 호출하지 않는다.
     * 게시글 작성자·숨김 상태·신고 이력을 검사한 뒤에만 호출한다.
     *
     * 게시글 잠금, 댓글 삭제, 게시글 삭제를 같은 트랜잭션으로 묶어야 한다.
     * 반환값 0은 댓글이 없는 게시글일 수 있으므로 오류가 아니다.
     *
     * 모든 부모·자식 댓글이 같은 게시글에 속한다는 관계를 전제로 한다.
     */
    @Delete("""
        DELETE FROM dbo.COMMUNITY_COMMENT
        WHERE COMMUNITY_ID = #{communityId}
        """)
    int deleteByCommunityId(
            @Param("communityId") Long communityId
    );
}