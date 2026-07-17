// Kiểm chứng trang HỒ SƠ có sửa liên hệ tại chỗ + Cài đặt đã bỏ tab Hồ sơ
// (cần backend + vite dev đang chạy). Sửa SĐT thật rồi KHÔI PHỤC giá trị gốc.
import { chromium } from 'playwright'

const out = '../claude-context'
const API = 'http://localhost:8080/api/v1'

// Lấy hồ sơ gốc của teacher qua API để khôi phục sau test
async function api(path, opts = {}, token) {
  const res = await fetch(API + path, {
    ...opts,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  })
  if (!res.ok) throw new Error(`${path} -> ${res.status}`)
  return res.json()
}
const login = await api('/auth/login', {
  method: 'POST',
  body: JSON.stringify({ username: 'teacher', password: 'Tsdms@123' }),
})
const original = await api('/me/profile', {}, login.accessToken)
console.log('SĐT gốc của teacher:', original.phone ?? '(trống)')

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const errors = []
page.on('pageerror', (e) => errors.push(`[pageerror] ${e.message}`))
await page.addInitScript(() => {
  localStorage.setItem('tsdms.ui', JSON.stringify({ theme: 'dark', fontSize: 'normal', motion: true }))
})

// Đăng nhập teacher qua UI
await page.goto('http://localhost:5173/login', { waitUntil: 'domcontentloaded' })
await page.getByRole('button', { name: 'teacher', exact: true }).click()
await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
await page.waitForURL('**/teacher', { timeout: 20000 })

/* ── Test 1: sửa SĐT tại chỗ trên trang Hồ sơ ── */
await page.goto('http://localhost:5173/teacher/profile', { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(1200)
console.log('Nút "Cài đặt tài khoản":', (await page.getByText('Cài đặt tài khoản').count()) ? 'CO' : 'THIEU')

await page.getByLabel('Sửa email / số điện thoại').click()
await page.screenshot({ path: `${out}/profile-edit-mode-dark.png`, fullPage: true })

// SĐT sai -> blur -> phải hiện lỗi
const phoneInput = page.getByPlaceholder('VD: 0901234567')
await phoneInput.fill('123')
await phoneInput.blur()
await page.waitForTimeout(300)
console.log('Bao loi SĐT sai:', (await page.getByText('SĐT phải bắt đầu bằng 0').count()) ? 'CO' : 'THIEU')

// SĐT hợp lệ -> Lưu -> thông báo + quay về chế độ xem
await phoneInput.fill('0912345678')
await page.getByRole('button', { name: 'Lưu thay đổi' }).click()
await page.waitForTimeout(1500)
console.log('Thong bao da luu:', (await page.getByText('Đã lưu thông tin liên hệ').count()) ? 'CO' : 'THIEU')
console.log('View hien SĐT moi:', (await page.getByText('0912345678').count()) ? 'CO' : 'THIEU')
await page.screenshot({ path: `${out}/profile-after-save-dark.png`, fullPage: true })

/* ── Test 2: Cài đặt không còn tab Hồ sơ, có link về Hồ sơ ── */
await page.goto('http://localhost:5173/teacher/settings', { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(1200)
const tabs = await page.locator('.st-tabs button').allTextContents()
console.log('Cac tab Cai dat:', tabs.map((t) => t.trim()).join(' | '))
await page.screenshot({ path: `${out}/settings-no-profile-tab-dark.png`, fullPage: true })
await page.getByText('Xem hồ sơ đầy đủ').click()
await page.waitForURL('**/teacher/profile', { timeout: 10000 })
console.log('Link "Xem hồ sơ đầy đủ" ve dung:', page.url())
await page.close()

/* ── Test 3: admin (actor NONE) — không có ô SĐT, chỉ sửa email ── */
const page2 = await browser.newPage({ viewport: { width: 1440, height: 900 } })
await page2.addInitScript(() => {
  localStorage.setItem('tsdms.ui', JSON.stringify({ theme: 'dark', fontSize: 'normal', motion: true }))
})
await page2.goto('http://localhost:5173/login', { waitUntil: 'domcontentloaded' })
await page2.getByRole('button', { name: 'admin', exact: true }).click()
await page2.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
await page2.waitForURL('**/dashboard', { timeout: 20000 })
await page2.goto('http://localhost:5173/profile', { waitUntil: 'domcontentloaded' })
await page2.waitForTimeout(1200)
await page2.getByLabel('Sửa email / số điện thoại').click()
await page2.waitForTimeout(300)
console.log('Admin co o SĐT?', (await page2.getByPlaceholder('VD: 0901234567').count()) ? 'CO (SAI)' : 'KHONG (DUNG)')
console.log('Admin co o email?', (await page2.getByPlaceholder('ban@vidu.vn').count()) ? 'CO (DUNG)' : 'THIEU (SAI)')
await page2.screenshot({ path: `${out}/profile-admin-edit-dark.png`, fullPage: true })
await page2.close()
await browser.close()

// Khôi phục SĐT gốc của teacher qua API
await api('/me/profile', {
  method: 'PUT',
  body: JSON.stringify({ email: original.email, phone: original.phone ?? '' }),
}, login.accessToken)
console.log('Da khoi phuc SĐT goc:', original.phone ?? '(trống)')
console.log(errors.length ? 'PAGEERROR:\n' + errors.join('\n') : 'khong co pageerror')
