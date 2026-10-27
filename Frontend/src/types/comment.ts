/**
 * 댓글·대댓글 조회 응답이다.
 *
 * 일반 댓글은 parentId가 null이고,
 * 대댓글은 parentId에 부모 댓글 ID가 들어간다.
 *
 * 삭제된 댓글은 서버에서 작성자 정보를 숨기므로
 * userId와 nickname은 null을 허용한다.
 */
export interface CommentResponse {
  id: number // 댓글 또는 대댓글 ID
  communityId: number // 소속 게시글 ID
  userId: number | null // 작성자 ID. 삭제된 댓글이면 null
  nickname: string | null // 작성자 닉네임. 삭제된 댓글이면 null
  parentId: number | null // 부모 댓글 ID. 일반 댓글이면 null
  content: string // 본문. 삭제된 댓글이면 서버의 삭제 안내 문구
  isDeleted: boolean // 논리 삭제 여부
  createdDt: string // UTC 기준 작성 일시
  updatedDt: string // UTC 기준 최종 수정 일시
}

/**
 * 댓글·대댓글 등록 요청이다.
 * 작성자 ID는 서버가 로그인 세션에서 확인하므로 보내지 않는다.
 */
export interface CommentCreateRequest {
  /** 일반 댓글 등록은 null, 대댓글 등록은 부모 댓글 ID */
  parentId: number | null

  /** 공백만 입력할 수 없으며 최대 2,000자 */
  content: string
}

/**
 * 댓글 수정 요청이다.
 * 작성자·소속 게시글·부모 관계는 변경하지 않는다.
 */
export interface CommentUpdateRequest {
  /** 수정할 본문 */
  content: string
}
