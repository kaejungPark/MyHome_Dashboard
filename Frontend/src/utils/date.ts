/**
 * 한국 시간 기준 오늘 날짜를 YYYY-MM-DD 형식으로 반환한다.
 * 사용자 기기의 시간대와 관계없이 Asia/Seoul을 기준으로 계산한다.
 * 날짜를 고정하지 않고 호출할 때마다 현재 날짜를 구한다.
 */
export function today(): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date())

  const year = parts.find((part) => part.type === 'year')!.value
  const month = parts.find((part) => part.type === 'month')!.value
  const day = parts.find((part) => part.type === 'day')!.value

  return `${year}-${month}-${day}`
}

/**
 * 한국 시간 기준 현재 월을 YYYY-MM 형식으로 반환한다.
 * today()에서 연도와 월 부분만 추출한다.
 */
export function currentMonth(): string {
  return today().slice(0, 7)
}
