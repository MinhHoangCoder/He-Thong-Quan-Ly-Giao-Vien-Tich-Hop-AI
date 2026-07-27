/* =====================================================================
   V17 — GIÁO VIÊN PHẢI XÁC NHẬN THÌ LỊCH DẠY MỚI CÓ HIỆU LỰC.

   Trước V17 phân công tạo ra là chạy luôn (ACTIVE + buổi APPROVED) nên lịch
   nhảy thẳng sang màn giáo viên dù họ chưa đồng ý. Nay phiếu sinh ra ở
   PENDING (Chờ xác nhận) và buổi dạy ở PENDING; chỉ khi giáo viên bấm Xác
   nhận (hoặc admin ép duyệt) mới chuyển ACTIVE + APPROVED — lúc đó lịch,
   chấm công, lương, thống kê mới nhìn thấy.

   Vòng đời Status của Assignment:
     PENDING  ──xác nhận / ép duyệt──▶ ACTIVE ──▶ COMPLETED
        │  └──quá hạn──▶ EXPIRED ──(admin sửa & gửi lại)──▶ PENDING
        └─────GV từ chối──▶ REJECTED ──(admin sửa & gửi lại)──▶ PENDING
     ACTIVE / PENDING ──admin hủy──▶ CANCELLED
   ===================================================================== */

/* CK cũ chỉ cho ACTIVE|COMPLETED|CANCELLED — phải nới trước khi ghi trạng thái mới. */
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Assignment_Status')
    ALTER TABLE Assignment DROP CONSTRAINT CK_Assignment_Status;

ALTER TABLE Assignment
    ADD CONSTRAINT CK_Assignment_Status CHECK
        (Status IN ('PENDING', 'ACTIVE', 'REJECTED', 'EXPIRED', 'COMPLETED', 'CANCELLED'));

/* Hạn giáo viên phải trả lời = sớm hơn của (lúc tạo + 48 giờ) và (giờ bắt đầu buổi dạy
   ĐẦU TIÊN). Vế thứ hai là bắt buộc: phân công gấp trong ngày mà để hạn 48 giờ thì hạn
   rơi vào lúc buổi học đã dạy xong. Kiểu DATETIME2 (không múi giờ) cho khớp
   Schedule.StartTime — cùng quy ước "giờ treo tường" của toàn hệ thống. */
IF COL_LENGTH('Assignment', 'ConfirmDeadline') IS NULL
    ALTER TABLE Assignment ADD ConfirmDeadline DATETIME2(0) NULL;

IF COL_LENGTH('Assignment', 'ConfirmedAt') IS NULL
    ALTER TABLE Assignment ADD ConfirmedAt DATETIME2(0) NULL;

/* Ai bấm duyệt (→ AppUser): giáo viên tự xác nhận, hoặc admin ép duyệt. */
IF COL_LENGTH('Assignment', 'ConfirmedByUserId') IS NULL
    ALTER TABLE Assignment ADD ConfirmedByUserId INT NULL;

/* TEACHER = giáo viên tự xác nhận | ADMIN = admin ép duyệt thay. Giữ lại để phân biệt
   lịch nào thực sự được giáo viên đồng ý, lịch nào do trung tâm quyết. */
IF COL_LENGTH('Assignment', 'ConfirmSource') IS NULL
    ALTER TABLE Assignment ADD ConfirmSource VARCHAR(10) NULL;

/* Lý do giáo viên từ chối — hiện thẳng trên dòng phân công để admin xếp lại người khác. */
IF COL_LENGTH('Assignment', 'RejectionReason') IS NULL
    ALTER TABLE Assignment ADD RejectionReason NVARCHAR(500) NULL;

/* Ghi chú tùy chọn khi admin ép duyệt (không bắt buộc nhập). */
IF COL_LENGTH('Assignment', 'ApprovalNote') IS NULL
    ALTER TABLE Assignment ADD ApprovalNote NVARCHAR(500) NULL;

/* Dữ liệu CŨ: mọi phiếu đang ACTIVE coi như đã được duyệt từ trước, đánh dấu nguồn ADMIN
   để không bị tác vụ quét hết hạn đụng tới. */
EXEC('
    UPDATE Assignment
       SET ConfirmedAt = COALESCE(ConfirmedAt, CreatedAt),
           ConfirmSource = COALESCE(ConfirmSource, ''ADMIN'')
     WHERE Status = ''ACTIVE'' AND ConfirmSource IS NULL;
');

/* Màn phân công lọc + đếm theo trạng thái; tác vụ nền quét phiếu PENDING quá hạn. */
IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
         WHERE name = 'IX_Assignment_Status_Deadline'
           AND object_id = OBJECT_ID('Assignment'))
    CREATE INDEX IX_Assignment_Status_Deadline ON Assignment(Status, ConfirmDeadline);
