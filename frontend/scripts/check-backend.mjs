/**
 * Nhắc trước khi Vite khởi động: backend đã chạy chưa (chạy tự động qua "predev").
 *
 * VÌ SAO CẦN: frontend lên sau chưa đầy 1 giây còn backend Spring Boot mất khoảng 15 giây, nên
 * mở dự án theo thói quen "npm run dev trước" là mọi request /api rơi vào khoảng trống đó và bị
 * từ chối kết nối. Lúc ấy màn hình báo "Không kết nối được tới máy chủ" — đúng bệnh nhưng dễ
 * tưởng code hỏng. Một dòng nhắc ngay lúc chạy lệnh tiết kiệm cả buổi đi tìm nhầm chỗ.
 *
 * Không chặn: dù backend chưa lên vẫn cho Vite chạy (còn làm giao diện tĩnh, còn xem lại UI...),
 * và luôn thoát mã 0 để script kiểm tra không bao giờ làm hỏng lệnh chạy dự án.
 */
import { connect } from 'node:net'

const port = Number(process.argv[2] ?? 8080)

function nhac(lyDo) {
  console.log(`[check-backend] Chưa thấy backend ở cổng ${port} (${lyDo}).`)
  console.log(
    '[check-backend] Mọi API sẽ lỗi tới khi chạy: cd backend && ./mvnw.cmd -DskipTests spring-boot:run',
  )
}

// Chỉ cần bắt tay TCP là biết có ai nghe cổng hay không — không gọi HTTP để khỏi phụ thuộc vào
// một endpoint cụ thể (mọi endpoint đều đòi đăng nhập) và khỏi đợi backend trả lời.
const socket = connect({ host: '127.0.0.1', port })
socket.setTimeout(1000)
socket.on('connect', () => socket.destroy())
socket.on('timeout', () => {
  socket.destroy()
  nhac('quá hạn chờ')
})
socket.on('error', (err) => nhac(err.code || err.message))
