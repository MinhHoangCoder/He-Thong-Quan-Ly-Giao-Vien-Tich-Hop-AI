/**
 * Giải phóng cổng dev server trước khi Vite khởi động (chạy tự động qua "predev").
 *
 * VÌ SAO CẦN: vite.config.js đặt strictPort: true — 5173 bận thì Vite dừng hẳn chứ không né
 * sang cổng khác (backend chỉ cho CORS origin http://localhost:5173). Mà đóng tab terminal
 * trong VS Code chỉ giết powershell.exe, tiến trình node của Vite thành mồ côi và tiếp tục
 * giữ cổng — nên lần chạy sau luôn báo "Port 5173 is already in use".
 *
 * Tệ hơn: lần chạy sau XÓA node_modules/.vite TRƯỚC rồi mới báo lỗi cổng, nên server cũ đang
 * chạy cũng hỏng theo (trang trắng, network trả 504 Outdated Optimize Dep). Tức là gặp lỗi này
 * thì kiểu gì cũng phải kill rồi chạy lại — script chỉ làm hộ đúng việc đó.
 *
 * Không tìm thấy hay giết không được thì IM LẶNG bỏ qua: Vite sẽ tự báo lỗi cổng như cũ, chứ
 * script dọn dẹp không được phép chặn lệnh chạy dự án.
 */
import { execSync } from 'node:child_process'

const port = Number(process.argv[2] ?? 5173)

/**
 * PID đang LẮNG NGHE trên cổng. Windows có thể trả 2 dòng (IPv4 + IPv6) cho cùng một tiến trình
 * nên phải khử trùng lặp.
 *
 * KHÔNG dùng cờ "-p tcp": nó chỉ liệt kê TCP trên IPv4, trong khi Vite bind vào localhost —
 * trên Windows localhost phân giải ra ::1 (IPv6) nên listener nằm ở nhóm TCPv6 và lọc kiểu đó
 * là không thấy gì cả. Dòng UDP không có cột trạng thái nên tự bị điều kiện length >= 5 loại.
 */
function timPid(cong) {
  if (process.platform === 'win32') {
    // Cột netstat: TCP | địa chỉ nội bộ | địa chỉ ngoài | trạng thái | PID
    const dong = execSync('netstat -ano', { encoding: 'utf8' }).split('\n')
    const pid = dong
      .map((d) => d.trim().split(/\s+/))
      .filter((c) => c.length >= 5 && c[3] === 'LISTENING' && c[1].endsWith(`:${cong}`))
      .map((c) => c[4])
    return [...new Set(pid)].filter((p) => p !== '0')
  }
  const out = execSync(`lsof -ti tcp:${cong} -sTCP:LISTEN`, { encoding: 'utf8' })
  return out.split('\n').filter(Boolean)
}

try {
  const dsPid = timPid(port)
  for (const pid of dsPid) {
    execSync(process.platform === 'win32' ? `taskkill /F /PID ${pid}` : `kill -9 ${pid}`, {
      stdio: 'ignore',
    })
    console.log(`[free-port] Đã dọn tiến trình cũ (PID ${pid}) đang giữ cổng ${port}.`)
  }
} catch {
  // Không có tiến trình nào nghe cổng thì netstat/lsof cũng ném lỗi — đó là trường hợp BÌNH
  // THƯỜNG nhất, không cần nói gì.
}
