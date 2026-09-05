/* =====================================================================
   V39 — HỦY PHÂN CÔNG CÓ NGÀY HIỆU LỰC + ĐƠN XIN NGHỈ CỦA GIÁO VIÊN.

   Bối cảnh: hủy phân công trước đây là chuyện "tất cả hoặc không" — bấm
   Hủy là cả phiếu chết ngay và rơi thẳng vào thùng rác. Thực tế điều phối
   thì khác: giáo viên xin nghỉ TỪ MỘT NGÀY nào đó, phần trước ngày ấy vẫn
   phải dạy và vẫn phải được tính công.

   HAI CỘT THEN CHỐT — vì sao cần cả hai:

     · CancelEffectiveDate = ngày ĐẦU TIÊN không dạy nữa. Buổi trước ngày
       này giữ nguyên (bằng chứng chấm công/lương), buổi từ ngày này trở đi
       chuyển CANCELLED.

     · OriginalEndDate = ngày kết thúc GỐC trước khi bị thu hẹp. Bắt buộc
       phải thu hẹp Assignment.EndDate về (CancelEffectiveDate - 1), vì
       chính cặp StartDate/EndDate là thứ luật chống trùng lịch dùng để nói
       "khung Thứ+Tiết này đã có người" (TeacherTimeConflictChecker). Để
       nguyên EndDate cũ thì hủy xong khung giờ VẪN bị coi là đang bị chiếm
       và admin không xếp được giáo viên thay vào đúng lớp đó — lỗi còn
       nặng hơn cái đang định sửa. Thu hẹp rồi thì phải có chỗ nhớ mốc gốc,
       nếu không "Bỏ hủy" không biết trả EndDate về đâu.

   KHÔNG nới CK_Assignment_Status: phiếu kết thúc sớm vẫn là ACTIVE dưới DB
   (nó còn buổi phải dạy tới ngày X). Nhãn "Đã kết thúc sớm" do tầng service
   tính tại chỗ từ CancelEffectiveDate — cùng cách màn hình đang tính nhãn
   "Hết hạn" cho phiếu chờ quá hạn (Assignment.isExpiredPending).
   ===================================================================== */

/* ---------- 1) Dấu vết hủy trên phiếu phân công ---------- */
IF COL_LENGTH('dbo.Assignment', 'CancelEffectiveDate') IS NULL
    ALTER TABLE Assignment ADD
        CancelEffectiveDate DATE          NULL,  -- ngày đầu tiên KHÔNG dạy nữa
        OriginalEndDate     DATE          NULL,  -- EndDate trước khi bị thu hẹp
        CancelReason        NVARCHAR(500) NULL,  -- bắt buộc nhập ở tầng service
        CancelledAt         DATETIME2(3)  NULL,
        CancelledByUserId   INT           NULL;
GO

IF OBJECT_ID('FK_Assignment_CancelledBy', 'F') IS NULL
    ALTER TABLE Assignment ADD CONSTRAINT FK_Assignment_CancelledBy
        FOREIGN KEY (CancelledByUserId) REFERENCES AppUser(Id);
GO

/* ---------- 2) Đơn xin nghỉ dạy do GIÁO VIÊN gửi ----------
   Vì sao là bảng riêng chứ không thêm cột vào Assignment: một phiếu có thể
   bị xin nghỉ, bị từ chối, rồi xin lại — nhét vào phiếu là mỗi lần xin sau
   ghi đè lần trước, mất đúng phần lịch sử cần để đối chiếu. Bảng riêng còn
   cho phép admin xem hàng đợi "đơn đang chờ" mà không phải quét cả bảng
   phân công.

   Đơn ĐƯỢC DUYỆT không tự nó hủy gì cả: nó gọi đúng luồng hủy có ngày hiệu
   lực ở trên, nên mọi đường (admin bấm tay / duyệt đơn) chạy qua cùng một
   đoạn mã và không thể lệch nhau. */
IF OBJECT_ID('dbo.AssignmentLeaveRequest', 'U') IS NULL
CREATE TABLE AssignmentLeaveRequest (
    Id              INT IDENTITY PRIMARY KEY,
    AssignmentId    INT           NOT NULL,
    TeacherId       INT           NOT NULL,   -- lưu kèm để lọc đơn của một GV không phải join
    EffectiveDate   DATE          NOT NULL,   -- xin nghỉ TỪ ngày này
    Reason          NVARCHAR(500) NOT NULL,
    Status          VARCHAR(20)   NOT NULL
                    CONSTRAINT DF_AssignmentLeaveRequest_Status DEFAULT 'PENDING'
                    CONSTRAINT CK_AssignmentLeaveRequest_Status
                        CHECK (Status IN ('PENDING', 'APPROVED', 'REJECTED')),
    DecisionNote    NVARCHAR(500) NULL,       -- lý do admin từ chối / ghi chú khi duyệt
    DecidedByUserId INT           NULL,
    DecidedAt       DATETIME2(3)  NULL,
    CreatedAt       DATETIME2(3)  NOT NULL
                    CONSTRAINT DF_AssignmentLeaveRequest_CreatedAt DEFAULT SYSUTCDATETIME(),
    CreatedBy       INT           NULL,
    UpdatedAt       DATETIME2(3)  NULL,
    UpdatedBy       INT           NULL,
    CONSTRAINT FK_AssignmentLeaveRequest_Assignment
        FOREIGN KEY (AssignmentId)    REFERENCES Assignment(Id),
    CONSTRAINT FK_AssignmentLeaveRequest_Teacher
        FOREIGN KEY (TeacherId)       REFERENCES Teacher(Id),
    CONSTRAINT FK_AssignmentLeaveRequest_DecidedBy
        FOREIGN KEY (DecidedByUserId) REFERENCES AppUser(Id)
);
GO

/* Một phiếu chỉ được có ĐÚNG MỘT đơn đang chờ: bấm nhầm hai lần thì admin
   nhận hai thông báo cho cùng một việc, duyệt cái thứ hai sau khi cái thứ
   nhất đã hủy phiếu là duyệt vào khoảng không. Index lọc (filtered) nên đơn
   đã duyệt/từ chối không vướng ràng buộc, xin lại được bình thường. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_AssignmentLeaveRequest_Pending')
    CREATE UNIQUE INDEX UX_AssignmentLeaveRequest_Pending
        ON AssignmentLeaveRequest(AssignmentId) WHERE Status = 'PENDING';
GO

/* Hai màn hình đọc bảng này: "đơn của tôi" (theo GV) và hàng đợi của admin
   (theo trạng thái). */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_AssignmentLeaveRequest_Teacher')
    CREATE INDEX IX_AssignmentLeaveRequest_Teacher
        ON AssignmentLeaveRequest(TeacherId, Status);
GO
