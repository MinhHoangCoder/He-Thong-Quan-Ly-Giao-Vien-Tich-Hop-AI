// Chụp kiểm tra trang HỒ SƠ (cần backend + vite dev đang chạy).
import { chromium } from 'playwright'

const out = '../claude-context'
const browser = await chromium.launch()
const errors = []

async function loginAndShoot(theme, account, homeUrl, steps) {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  page.on('console', (m) => m.type() === 'error' && errors.push(`${account}/${theme}: ${m.text()}`))
  await page.addInitScript((t) => {
    localStorage.setItem('tsdms.ui', JSON.stringify({ theme: t, fontSize: 'normal', motion: true }))
  }, theme)

  await page.goto('http://localhost:5173/login', { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: account, exact: true }).click()
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await page.waitForURL('**' + homeUrl, { timeout: 20000 })

  for (const s of steps) {
    await page.goto('http://localhost:5173' + s.path, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(1500)
    if (s.click) {
      await page.getByText(s.click).first().click()
      await page.waitForTimeout(400)
    }
    await page.screenshot({ path: `${out}/${s.file}`, fullPage: true })
    console.log('ok', s.file)
  }
  await page.close()
}

await loginAndShoot('dark', 'teacher', '/teacher', [
  { path: '/teacher/profile', file: 'profile-teacher-dark.png' },
  { path: '/teacher/settings', file: 'settings-perms-teacher-dark.png', click: 'Xem chi tiết' },
])
await loginAndShoot('light', 'teacher', '/teacher', [
  { path: '/teacher/profile', file: 'profile-teacher-light.png' },
])
await loginAndShoot('dark', 'admin', '/dashboard', [
  { path: '/profile', file: 'profile-admin-dark.png' },
])

await browser.close()
console.log(errors.length ? 'console errors:\n' + errors.join('\n') : 'no console errors')
