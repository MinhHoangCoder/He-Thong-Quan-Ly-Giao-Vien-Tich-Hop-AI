// Nhãn tiếng Việt dùng chung cho ROLE và mã PERMISSION.
// Lý do tồn tại: mã kỹ thuật (SCHEDULE_VIEW, ACCOUNTANT...) hiển thị thẳng lên UI
// thì người vận hành không đọc được — mọi chỗ cần "dịch" đều import từ đây,
// tránh mỗi trang tự chế một bảng map lệch nhau.

// Từ Flyway V33 hệ thống chỉ còn HAI tác nhân. Các nhãn phòng ban (Kế toán, Nhân sự,
// Đào tạo, Tuyển sinh) và EMPLOYEE đã bỏ cùng lúc với role — giữ lại nhãn của thứ không
// tồn tại chỉ khiến người đọc tưởng vẫn còn.
export const ROLE_LABELS = {
  ADMIN: 'Quản trị viên',
  TEACHER: 'Giáo viên',
}

export function roleLabel(role) {
  return ROLE_LABELS[role] || role
}

// Mã quyền theo quy ước MODULE_ACTION (khớp seed Flyway V3).
const PERM_ACTIONS = { VIEW: 'Xem', MANAGE: 'Quản lý', APPROVE: 'Duyệt' }
const PERM_MODULES = {
  USER: 'Tài khoản người dùng',
  ROLE: 'Phân quyền',
  TEACHER: 'Hồ sơ giáo viên',
  CONTRACT: 'Hợp đồng giáo viên',
  SERVICE_CONTRACT: 'Hợp đồng dịch vụ trường',
  ASSIGNMENT: 'Phân công giảng dạy',
  SCHEDULE: 'Lịch dạy',
  ATTENDANCE: 'Chấm công',
  PAYROLL: 'Bảng lương',
  CLASS: 'Lớp học',
  STUDENT: 'Học sinh',
  SCHOOL: 'Trường khách hàng',
  EVALUATION: 'Đánh giá giáo viên',
  LESSON: 'Kho bài giảng',
  REPORT_REVENUE: 'Báo cáo doanh thu',
  REPORT_OPERATION: 'Báo cáo vận hành',
}

/** Tách "SCHEDULE_VIEW" -> { module: 'Lịch dạy', action: 'Xem' }. Mã lạ giữ nguyên. */
export function permParts(code) {
  const i = code.lastIndexOf('_')
  const moduleKey = i > 0 ? code.slice(0, i) : code
  const actionKey = i > 0 ? code.slice(i + 1) : ''
  return {
    module: PERM_MODULES[moduleKey] || moduleKey,
    action: PERM_ACTIONS[actionKey] || actionKey,
  }
}

/** "SCHEDULE_VIEW" -> "Xem lịch dạy" (một dòng, cho danh sách phẳng). */
export function permLabel(code) {
  const { module, action } = permParts(code)
  if (!action) return module
  return `${action} ${module.charAt(0).toLowerCase()}${module.slice(1)}`
}

/**
 * Gom danh sách mã quyền theo module để vẽ dạng bảng:
 * ['SCHEDULE_VIEW','SCHEDULE_MANAGE'] -> [{ module: 'Lịch dạy', actions: ['Xem','Quản lý'] }].
 * Giữ nguyên thứ tự xuất hiện — token phát quyền theo thứ tự seed nên nhóm nào
 * quan trọng thường đứng trước.
 */
export function permGroups(codes) {
  const groups = new Map()
  for (const code of codes) {
    const { module, action } = permParts(code)
    if (!groups.has(module)) groups.set(module, [])
    const actions = groups.get(module)
    if (action && !actions.includes(action)) actions.push(action)
  }
  return [...groups.entries()].map(([module, actions]) => ({ module, actions }))
}
