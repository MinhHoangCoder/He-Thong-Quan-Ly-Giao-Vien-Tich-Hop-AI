import { chromium } from 'playwright'

const out = '../claude-context'
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const errors = []
page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))

await page.goto('http://localhost:5173/dashboard', { waitUntil: 'networkidle' })
await page.waitForSelector('text=Bảng điều khiển', { timeout: 15000 })
await page.waitForTimeout(1200) // chờ count-up chạy xong
await page.screenshot({ path: `${out}/preview-dashboard.png`, fullPage: true })

await page.goto('http://localhost:5173/', { waitUntil: 'networkidle' })
await page.waitForTimeout(400)
await page.screenshot({ path: `${out}/preview-home.png`, fullPage: true })

await browser.close()
console.log(errors.length ? 'console errors: ' + errors.join(' | ') : 'no console errors')
