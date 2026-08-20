/**
 * Khai báo Chart.js dùng chung cho Bảng điều khiển.
 *
 * Chart.js v4 không tự nạp sẵn thành phần nào — thiếu register là biểu đồ ra khung trắng
 * chứ không báo lỗi, rất mất công dò. Gom việc register về một chỗ để chỉ phải nhớ một lần.
 */
import {
  Chart,
  BarController,
  BarElement,
  LineController,
  LineElement,
  PointElement,
  DoughnutController,
  ArcElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Legend,
} from 'chart.js'

Chart.register(
  BarController,
  BarElement,
  LineController,
  LineElement,
  PointElement,
  DoughnutController,
  ArcElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Legend,
)

/** Màu cho các nhóm/lát của biểu đồ. */
export const MAU_BIEU_DO = [
  '#f97316',
  '#0ea5e9',
  '#8b5cf6',
  '#22c55e',
  '#f59e0b',
  '#ec4899',
  '#14b8a6',
  '#ef4444',
]

/**
 * Màu chữ và lưới của biểu đồ.
 *
 * Chart.js vẽ lên canvas nên không ăn CSS — màu phải truyền bằng tay. Ở đây dùng một tông xám
 * trung tính đọc được trên CẢ nền sáng lẫn nền tối, thay vì đọc biến CSS của theme:
 * getComputedStyle không phải reactive, nên lấy theo theme thì lúc người dùng bật nền tối biểu
 * đồ vẫn giữ màu cũ cho tới khi tải lại trang.
 */
export function mauTheme() {
  return { chu: '#94a3b8', luoi: 'rgba(148, 163, 184, 0.25)' }
}

/** Định dạng số kiểu Việt Nam cho nhãn trục và tooltip. */
export const soVN = (v) => new Intl.NumberFormat('vi-VN').format(Math.round(v))
