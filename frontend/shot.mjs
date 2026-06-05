import { chromium } from 'playwright'

const url = process.argv[2] || 'http://localhost:5173/dashboard'
const out = process.argv[3] || 'dashboard.png'

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 1024 } })
await page.goto(url, { waitUntil: 'networkidle' })
await page.waitForSelector('text=Bảng điều khiển', { timeout: 15000 })
await page.screenshot({ path: out, fullPage: true })
const errors = []
page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
await browser.close()
console.log('OK ->', out, errors.length ? 'console errors: ' + errors.join(' | ') : 'no console errors')
