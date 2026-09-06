// Script chụp ảnh giao diện cho BÁO CÁO ĐỒ ÁN.
// Chạy: (đứng trong thư mục frontend, app FE+BE đang chạy)
//   node capture-report-shots.mjs
// Ảnh lưu vào: <repo-root>/report-screenshots/*.png — đúng thư mục báo cáo lấy ảnh vào.
//
// LƯU Ý: ba màn dưới đây đã bị gỡ khỏi ứng dụng nên phần chụp chúng đang tạm tắt —
// Ma trận quyền (/settings/roles), Trợ lý AI (/ai-assistant), Portal nhân viên (/staff).
// Bật lại nếu các trang đó quay về; đánh số ảnh giữ nguyên để không phải sửa báo cáo.
import { chromium } from 'playwright'
import fs from 'fs'

const BASE = 'http://localhost:5173'
const PASS = 'Tsdms@123'
const OUT = '../report-screenshots'
fs.mkdirSync(OUT, { recursive: true })

async function login(page, user) {
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[autocomplete="username"]', user)
  await page.fill('input[type="password"]', PASS)
  await page.click('button.btn[type="submit"]')
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(1800)
}

async function shot(page, path, file) {
  await page.goto(BASE + path, { waitUntil: 'networkidle' })
  await page.waitForTimeout(2200) // chờ count-up / chart / data load
  await page.screenshot({ path: `${OUT}/${file}.png`, fullPage: true })
  console.log('  saved', file)
}

const browser = await chromium.launch()

// 1) Trang công khai
console.log('[public]')
let ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
let page = await ctx.newPage()
await shot(page, '/', '01-home')
await shot(page, '/login', '02-login')
await ctx.close()

// 2) Vai trò ADMIN
console.log('[admin]')
ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
page = await ctx.newPage()
await login(page, 'admin')
await shot(page, '/dashboard', '03-admin-dashboard')
await shot(page, '/dashboard/teacher', '04-teacher-list')
await shot(page, '/assignments', '05-assignments')
await shot(page, '/schedule', '06-schedule')
await shot(page, '/attendance', '07-attendance')
await shot(page, '/payroll', '08-payroll')
await shot(page, '/admin/lessons', '09-lessons')
await shot(page, '/admin/subject-categories', '10-subject-categories')
// await shot(page, '/settings/roles', '11-role-matrix')   // trang da go
// await shot(page, '/ai-assistant', '12-ai-assistant')     // trang da go
await shot(page, '/settings', '13-settings')
await ctx.close()

// 3) Vai trò GIÁO VIÊN
console.log('[teacher]')
ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
page = await ctx.newPage()
await login(page, 'teacher')
await shot(page, '/teacher', '14-teacher-home')
await shot(page, '/teacher/lessons', '15-teacher-lessons')
await ctx.close()

// 4) Vai trò NHÂN VIÊN (nhân sự) — portal /staff đã gỡ, tạm tắt cả khối
// console.log('[staff]')
// ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
// page = await ctx.newPage()
// await login(page, 'nhansu')
// await shot(page, '/staff', '16-staff-home')
// await ctx.close()

await browser.close()
console.log('DONE -> report-screenshots/')
