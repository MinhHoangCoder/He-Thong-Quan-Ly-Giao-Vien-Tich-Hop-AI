# claude-context/ — Ngữ cảnh cá nhân cho Claude

Đây là folder **chỉ tồn tại trên máy bạn** (đã gitignore). Mọi file bạn bỏ vào đây
**không bị đẩy lên git**, trừ chính file `README.md` này.

## Dùng để làm gì?
Bỏ vào đây bất cứ thứ gì bạn muốn Claude đọc để hiểu ngữ cảnh, nhưng **không nên**
nằm trong lịch sử repo, ví dụ:

- Ảnh chụp màn hình mẫu (template tham khảo, lỗi giao diện…).
- Yêu cầu đề tài, file Word/PDF của giảng viên.
- Ghi chú riêng, ý tưởng nháp, danh sách việc cần làm cá nhân.
- Dữ liệu/ví dụ thật mà bạn không muốn public.

## Phân biệt với 2 chỗ khác
| Vị trí | Ai ghi | Có lên git? | Mục đích |
|---|---|---|---|
| `claude-context/` (folder này) | **Bạn** | ❌ Không | Tài liệu bạn đưa cho Claude đọc, riêng tư |
| `docs/dev-notes/` | **Claude** | ✅ Có | Ghi chú giải thích tính năng (FE & BE) để bạn học |
| `.claude/.../memory/` | **Claude** | ❌ Không | Bộ nhớ bền vững của Claude giữa các phiên |

> Khi cần Claude xem một file ở đây, chỉ cần nói: "đọc claude-context/ten-file".
