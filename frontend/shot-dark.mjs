// Chụp kiểm tra dark mode: đăng nhập THẬT bằng tài khoản demo (backend phải chạy),
// đặt theme qua localStorage 'tsdms.ui' trước khi trang load.
import { chromium } from 'playwright'

const out = '../claude-context'
const browser = await chromium.launch()
const errors = []

// Đăng nhập bằng chip demo (điền sẵn user + mật khẩu Tsdms@123) rồi chụp lần lượt các trang
async function loginAndShoot(theme, account, homeUrl, targets) {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  page.on('console', (m) => m.type() === 'error' && errors.push(`${account}/${theme}: ${m.text()}`))
  await page.addInitScript((t) => {
    localStorage.setItem('tsdms.ui', JSON.stringify({ theme: t, fontSize: 'normal', motion: true }))
  }, theme)

  await page.goto('http://localhost:5173/login', { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: account, exact: true }).click()
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await page.waitForURL('**' + homeUrl, { timeout: 15000 })

  for (const [path, file] of targets) {
    await page.goto('http://localhost:5173' + path, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(1800) // chờ count-up + API xong
    await page.screenshot({ path: `${out}/${file}`, fullPage: true })
    console.log('ok', file)
  }
  await page.close()
}

await loginAndShoot('dark', 'teacher', '/teacher', [['/teacher', 'gv-dash-dark.png']])
await loginAndShoot('light', 'teacher', '/teacher', [['/teacher', 'gv-dash-light.png']])
await loginAndShoot('dark', 'admin', '/dashboard', [
  ['/dashboard', 'admin-dash-dark.png'],
  ['/schedule', 'schedule-dark.png'],
  ['/assignments', 'assign-dark.png'],
  ['/payroll', 'payroll-dark.png'],
  ['/attendance', 'attendance-dark.png'],
])
await loginAndShoot('light', 'admin', '/dashboard', [['/schedule', 'schedule-light.png']])

await browser.close()
console.log(errors.length ? 'console errors:\n' + errors.join('\n') : 'no console errors')
