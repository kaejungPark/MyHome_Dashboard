// 체험 데이터 전용 코드다. 실제 서버에 저장하지 않는다.
type ScheduleType = 'GENERAL' | 'PAYMENT' | 'RENEWAL'
type ItemStatus = 'IN_USE' | 'STORED' | 'DISPOSED'

// 한국 날짜를 YYYY-MM-DD로 반환한다.
function koreaToday(): string {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}

// 날짜 계산은 UTC로 통일해 시간대에 따른 날짜 오차를 방지한다.
function addDays(dateText: string, days: number): string {
  const date = new Date(`${dateText}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

/**
 * 호출할 때마다 독립적인 체험 데이터를 만든다.
 * 월별 화면은 조회 월을 전달하고, 기본값은 한국 시간의 이번 달이다.
 * 각 월에 동일한 예시를 보여주는 체험용 구성이다.
 */
export function createDemoData(selectedMonth?: string) {
  const today = koreaToday()
  const month = selectedMonth ?? today.slice(0, 7)

  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) {
    throw new Error('올바른 조회 월이 아닙니다.')
  }

  // 샘플 ID는 실제 DB의 ID와 구분하기 위해 음수를 사용한다.
  const categories = [
    { id: -1, name: '식비' },
    { id: -2, name: '생활용품' },
    { id: -3, name: '주거비' },
  ]

  const expenses = [
    {
      id: -101,
      categoryId: -1,
      categoryName: '식비',
      title: '주말 장보기',
      amount: 35000,
      expenseDate: `${month}-01`,
      expenseType: 'VARIABLE',
      paymentMethod: '카드',
      memo: '일주일 식재료 구매',
    },
    {
      id: -102,
      categoryId: -1,
      categoryName: '식비',
      title: '점심 식사',
      amount: 15000,
      expenseDate: `${month}-01`,
      expenseType: 'VARIABLE',
      paymentMethod: '카드',
      memo: '',
    },
    {
      id: -103,
      categoryId: -2,
      categoryName: '생활용품',
      title: '세제와 휴지',
      amount: 25000,
      expenseDate: `${month}-01`,
      expenseType: 'VARIABLE',
      paymentMethod: '카드',
      memo: '생활용품 보충',
    },
    {
      id: -104,
      categoryId: -3,
      categoryName: '주거비',
      title: '관리비',
      amount: 75000,
      expenseDate: `${month}-01`,
      expenseType: 'FIXED',
      paymentMethod: '계좌이체',
      memo: '이번 달 관리비',
    },
  ]

  const schedules = [
    {
      id: -201,
      title: '관리비 납부 확인',
      scheduleType: 'PAYMENT' as ScheduleType,
      startDate: today,
      dueDate: addDays(today, 3),
      amount: 75000,
      completed: false,
      memo: '납부 내역 확인하기',
    },
    {
      id: -202,
      title: '인터넷 약정 갱신',
      scheduleType: 'RENEWAL' as ScheduleType,
      startDate: today,
      dueDate: addDays(today, 10),
      amount: null,
      completed: false,
      memo: '요금제와 갱신 혜택 확인',
    },
    {
      id: -203,
      title: '분리수거',
      scheduleType: 'GENERAL' as ScheduleType,
      startDate: today,
      dueDate: today,
      amount: null,
      completed: true,
      memo: '완료한 일정 예시',
    },
  ]

  const items = [
    {
      id: -301,
      name: '전자레인지',
      category: '가전',
      purchaseDate: addDays(today, -350),
      purchasePrice: 120000,
      warrantyEndDate: addDays(today, 15),
      maintenanceCycle: 30,
      status: 'IN_USE' as ItemStatus,
      memo: '내부 청소는 한 달에 한 번',
    },
    {
      id: -302,
      name: '무선 청소기',
      category: '가전',
      purchaseDate: addDays(today, -340),
      purchasePrice: 180000,
      warrantyEndDate: addDays(today, 25),
      maintenanceCycle: 14,
      status: 'IN_USE' as ItemStatus,
      memo: '필터 세척하기',
    },
    {
      id: -303,
      name: '선풍기',
      category: '가전',
      purchaseDate: addDays(today, -100),
      purchasePrice: 60000,
      warrantyEndDate: addDays(today, 265),
      maintenanceCycle: 30,
      status: 'STORED' as ItemStatus,
      memo: '계절용품 보관 중',
    },
  ]

  const recurringExpenses = [
    {
      id: -401,
      categoryId: -3,
      categoryName: '주거비',
      title: '월세',
      amount: 350000,
      paymentDay: 25,
      paymentMethod: '계좌이체',
      startMonth: month,
      endMonth: null,
      active: true,
      memo: '매월 25일 납부',
    },
    {
      id: -402,
      categoryId: -3,
      categoryName: '주거비',
      title: '인터넷 요금',
      amount: 30000,
      paymentDay: 15,
      paymentMethod: '자동이체',
      startMonth: month,
      endMonth: null,
      active: true,
      memo: '매월 자동 납부',
    },
  ]

  const payments = [
    {
      id: -401,
      title: '월세 납부',
      amount: 300000,
      paymentDate: addDays(today, 3),
    },
  ]

  // 고정 지출은 실제 생활비 합계에 중복해서 더하지 않는다.
  const totalAmount = expenses.reduce((sum, item) => sum + item.amount, 0)
  const budgetAmount = 600000
  const remainingAmount = budgetAmount - totalAmount
  const usageRate = (totalAmount / budgetAmount) * 100

  const monthlyItems = recurringExpenses
    .map((item) => ({
      id: item.id,
      categoryId: item.categoryId,
      categoryName: item.categoryName,
      title: item.title,
      amount: item.amount,
      paymentDate: `${month}-${String(item.paymentDay).padStart(2, '0')}`,
      paymentMethod: item.paymentMethod,
      paid: false,
      expenseId: null,
      paidDate: null,
    }))
    .sort((a, b) => a.paymentDate.localeCompare(b.paymentDate))

  const monthlyPayment = {
    month,
    totalAmount: monthlyItems.reduce((sum, item) => sum + item.amount, 0),
    items: monthlyItems,
  }

  const budget = {
    month,
    configured: true,
    budgetAmount,
    spentAmount: totalAmount,
    remainingAmount,
  }

  // 전월 비교 화면을 위한 샘플 금액이다.
  const previousMonthAmount = 200000
  const changeAmount = totalAmount - previousMonthAmount
  // 전월 샘플 금액이 200,000원으로 고정되어 있으므로 바로 계산한다.
  const changeRate = Number(((changeAmount / previousMonthAmount) * 100).toFixed(2))

  const statistics = {
    month,
    totalAmount,
    configured: true,
    budgetAmount,
    remainingAmount,
    usageRate,
    categories: categories
      .map((category) => {
        const amount = expenses
          .filter((expense) => expense.categoryId === category.id)
          .reduce((sum, expense) => sum + expense.amount, 0)

        return {
          categoryId: category.id,
          categoryName: category.name,
          amount,
          percentage: totalAmount === 0 ? 0 : Math.round((amount / totalAmount) * 10000) / 100,
        }
      })
      .sort((a, b) => b.amount - a.amount),
    previousMonthAmount,
    changeAmount,
    changeRate,
  }

  const endDate = addDays(today, 30)
  // 고정 지출 미납 안내는 오늘부터 7일 뒤까지 표시한다.
  const paymentEndDate = addDays(today, 7)

  const dashboard = {
    today,
    month,
    totalAmount,
    configured: true,
    budgetAmount,
    remainingAmount,
    usageRate,
    recurringAmount: monthlyPayment.totalAmount,
    schedules: schedules
      .filter((item) => !item.completed && item.dueDate >= today && item.dueDate <= endDate)
      .sort((a, b) => a.dueDate.localeCompare(b.dueDate))
      .slice(0, 5)
      .map(({ id, title, scheduleType, dueDate }) => ({
        id,
        title,
        scheduleType,
        dueDate,
      })),
    items: items
      .filter(
        (item) =>
          item.status !== 'DISPOSED' &&
          item.warrantyEndDate >= today &&
          item.warrantyEndDate <= endDate,
      )
      .sort((a, b) => a.warrantyEndDate.localeCompare(b.warrantyEndDate))
      .slice(0, 5)
      .map(({ id, name, warrantyEndDate }) => ({
        id,
        name,
        warrantyEndDate,
      })),
    payments: payments
      .filter((item) => item.paymentDate >= today && item.paymentDate <= paymentEndDate)
      .sort((a, b) => a.paymentDate.localeCompare(b.paymentDate) || a.id - b.id),
  }

  return {
    categories,
    expenses,
    schedules,
    items,
    recurringExpenses,
    monthlyPayment,
    budget,
    statistics,
    dashboard,
  }
}

// 전체 샘플 데이터의 타입을 반환 구조에서 자동으로 만든다.
export type DemoData = ReturnType<typeof createDemoData>
