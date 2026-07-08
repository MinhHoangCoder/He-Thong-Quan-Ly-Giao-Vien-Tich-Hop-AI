// ============================================================================
// Danh mục MODULE nội bộ của trung tâm + QUYỀN (permission) yêu cầu cho mỗi module.
// Dùng CHUNG cho: sidebar (StaffLayout) và trang chủ phòng ban (StaffHomePage).
//
// Ý tưởng: token đăng nhập mang sẵn `perms` (RBAC). Menu/thẻ chỉ hiện module mà user
// CÓ quyền tương ứng → mỗi phòng ban tự thấy đúng phần của mình, không phải code riêng.
// to:'#' = trang feature CHƯA làm (thành viên khác build sau), vẫn liệt kê để thấy cấu trúc.
// ============================================================================

/** Nhãn tiếng Việt cho các role phòng ban (greeting + badge). */
export const DEPARTMENT_LABELS = {
  ACCOUNTANT: 'Kế toán',
  HR: 'Nhân sự',
  ACADEMIC: 'Đào tạo',
  SALES: 'Tuyển sinh',
}

/** Mỗi module: section (nhóm sidebar), perm (quyền cần có), label, icon, to, desc. */
export const STAFF_MODULES = [
  {
    section: 'Giảng dạy',
    perm: 'ASSIGNMENT_VIEW',
    label: 'Phân công giảng dạy',
    icon: 'assignment',
    to: '#',
    desc: 'Phân công giáo viên cho lớp / trường',
  },
  {
    section: 'Giảng dạy',
    perm: 'SCHEDULE_VIEW',
    label: 'Lịch dạy',
    icon: 'schedule',
    to: '#',
    desc: 'Xếp & theo dõi lịch dạy',
  },
  {
    section: 'Giảng dạy',
    perm: 'LESSON_VIEW',
    label: 'Kho bài giảng',
    icon: 'subject',
    to: '/staff/lessons',
    desc: 'Quản lý bài giảng',
  },
  {
    section: 'Giảng dạy',
    perm: 'LESSON_MANAGE',
    label: 'Nhóm môn học',
    icon: 'subject',
    to: '/staff/subject-categories',
    desc: 'Quản lý nhóm/danh mục môn học',
  },
  {
    section: 'Nhân sự',
    perm: 'TEACHER_VIEW',
    label: 'Hồ sơ giáo viên',
    icon: 'teacher',
    to: '#',
    desc: 'Thông tin & hồ sơ giáo viên',
  },
  {
    section: 'Nhân sự',
    perm: 'CONTRACT_VIEW',
    label: 'Hợp đồng & chứng chỉ',
    icon: 'subject',
    to: '#',
    desc: 'HĐ lao động + chứng chỉ GV',
  },
  {
    section: 'Học vụ',
    perm: 'CLASS_VIEW',
    label: 'Lớp học',
    icon: 'subject',
    to: '#',
    desc: 'Quản lý lớp học',
  },
  {
    section: 'Học vụ',
    perm: 'STUDENT_VIEW',
    label: 'Học sinh',
    icon: 'teacher',
    to: '#',
    desc: 'Hồ sơ học sinh',
  },
  {
    section: 'Học vụ',
    perm: 'EVALUATION_VIEW',
    label: 'Đánh giá giáo viên',
    icon: 'evaluation',
    to: '#',
    desc: 'Đánh giá chất lượng giảng dạy',
  },
  {
    section: 'Vận hành',
    perm: 'ATTENDANCE_VIEW',
    label: 'Chấm công',
    icon: 'attendance',
    to: '#',
    desc: 'Theo dõi & ghi chấm công',
  },
  {
    section: 'Tài chính',
    perm: 'PAYROLL_VIEW',
    label: 'Bảng lương',
    icon: 'payroll',
    to: '#',
    desc: 'Tính & duyệt lương giáo viên',
  },
  {
    section: 'Tài chính',
    perm: 'SERVICE_CONTRACT_VIEW',
    label: 'Hợp đồng dịch vụ',
    icon: 'subject',
    to: '#',
    desc: 'HĐ dịch vụ với trường khách hàng',
  },
  {
    section: 'Tài chính',
    perm: 'REPORT_REVENUE_VIEW',
    label: 'Doanh thu',
    icon: 'payroll',
    to: '#',
    desc: 'Dashboard doanh thu theo năm học',
  },
  {
    section: 'Kinh doanh',
    perm: 'SCHOOL_VIEW',
    label: 'Trường khách hàng',
    icon: 'school',
    to: '#',
    desc: 'Quản lý trường khách hàng',
  },
  {
    section: 'Báo cáo',
    perm: 'REPORT_OPERATION_VIEW',
    label: 'Báo cáo vận hành',
    icon: 'dashboard',
    to: '#',
    desc: 'Báo cáo hoạt động chung',
  },
]

/** Lọc danh sách module mà user (theo perms) được phép thấy. */
export function accessibleModules(perms = []) {
  const set = new Set(perms)
  return STAFF_MODULES.filter((m) => set.has(m.perm))
}

/**
 * Dựng cấu trúc nav cho PortalShell: nhóm theo `section`, chỉ gồm module user có quyền.
 * Luôn có nhóm "Tổng quan" dẫn về trang chủ phòng ban (/staff).
 */
export function buildStaffNav(perms = []) {
  const nav = [
    { title: 'Tổng quan', items: [{ label: 'Trang chủ', icon: 'dashboard', to: '/staff' }] },
  ]
  const bySection = new Map()
  for (const m of accessibleModules(perms)) {
    if (!bySection.has(m.section)) bySection.set(m.section, [])
    bySection.get(m.section).push({ label: m.label, icon: m.icon, to: m.to })
  }
  for (const [title, items] of bySection) nav.push({ title, items })
  // "Cài đặt" không nằm trong sidebar: vào qua dropdown avatar trên topbar (PortalShell).
  return nav
}
