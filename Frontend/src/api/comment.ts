import { request } from './client'
import type { PageResponse } from '../types/page'
import type { CommentResponse, CommentCreateRequest, CommentUpdateRequest } from '../types/comment'

/**
 * 게시글에 속한 댓글 API의 공통 주소를 만든다.
 * 모든 댓글 요청은 소속 게시글 ID를 함께 전달한다.
 */
function getCommentUrl(communityId: number): string {
  return `/api/community/${communityId}/comments`
}

/**
 * 일반 댓글 목록을 페이징 조회한다.
 *
 * 대댓글은 이 결과에 포함되지 않는다.
 * 삭제된 일반 댓글도 안내 문구를 가진 항목으로 포함된다.
 */
export async function fetchComments(
  communityId: number,
  page: number,
  size: number,
): Promise<PageResponse<CommentResponse>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  })

  const result = await request<PageResponse<CommentResponse>>(
    `${getCommentUrl(communityId)}?${params.toString()}`,
  )

  // 조회 API는 페이지 응답이 필수이므로 null 응답을 오류로 처리한다.
  if (result === null) {
    throw new Error('댓글 목록 응답이 없습니다.')
  }

  return result
}

/**
 * 특정 일반 댓글의 대댓글 목록을 페이징 조회한다.
 *
 * 부모 댓글마다 별도의 페이지 번호를 전달한다.
 * 부모 댓글이 삭제됐어도 기존 대댓글은 조회할 수 있다.
 */
export async function fetchReplies(
  communityId: number,
  parentId: number,
  page: number,
  size: number,
): Promise<PageResponse<CommentResponse>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  })

  const result = await request<PageResponse<CommentResponse>>(
    `${getCommentUrl(communityId)}/${parentId}/replies?${params.toString()}`,
  )

  if (result === null) {
    throw new Error('대댓글 목록 응답이 없습니다.')
  }

  return result
}

/**
 * 댓글 또는 대댓글을 등록한다.
 *
 * parentId가 null이면 일반 댓글을 등록한다.
 * 값이 있으면 해당 일반 댓글에 대댓글을 등록한다.
 *
 * 서버는 등록한 ID를 반환하지 않으므로,
 * 호출한 컴포넌트에서 등록 성공 후 목록을 다시 조회한다.
 */
export async function createComment(
  communityId: number,
  body: CommentCreateRequest,
): Promise<void> {
  await request<null>(getCommentUrl(communityId), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

/**
 * 본인이 작성한 댓글·대댓글의 본문을 수정한다.
 *
 * 화면에서 수정 버튼을 숨기더라도
 * 최종 작성자 권한과 삭제 여부는 서버에서 다시 검사한다.
 */
export async function updateComment(
  communityId: number,
  commentId: number,
  body: CommentUpdateRequest,
): Promise<void> {
  await request<null>(`${getCommentUrl(communityId)}/${commentId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

/**
 * 본인이 작성한 댓글·대댓글을 논리 삭제한다.
 *
 * 부모 댓글을 삭제해도 기존 대댓글은 유지된다.
 * 삭제 확인 창은 이 함수를 호출하기 전에 컴포넌트에서 표시한다.
 */
export async function deleteComment(communityId: number, commentId: number): Promise<void> {
  await request<null>(`${getCommentUrl(communityId)}/${commentId}`, {
    method: 'DELETE',
  })
}
