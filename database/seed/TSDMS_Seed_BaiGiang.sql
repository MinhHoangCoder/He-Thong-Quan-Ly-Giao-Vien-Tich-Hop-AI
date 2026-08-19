/* =====================================================================
   TSDMS — SEED KHO BÀI GIẢNG (Lesson + LessonFile)
   ---------------------------------------------------------------------
   CHẠY THẾ NÀO
     Mở trong SSMS rồi Execute, hoặc:
       sqlcmd -S localhost -d TSDMS -U tsdms_app -P *** -i TSDMS_Seed_BaiGiang.sql
     ĐÒI 23 môn học + giáo viên đã nạp (TSDMS_Seed_100GiaoVien.sql).
     Chạy lại lần hai tự bỏ qua.

   VÌ SAO CÓ FILE NÀY
     Module Bài giảng đã dựng xong hết — CRUD, upload file, vòng đời
     DRAFT → PUBLISHED → ARCHIVED, phân quyền giáo viên chỉ xem bài đã xuất bản,
     trang riêng cho cổng giáo viên. Nhưng cả kho chỉ có ĐÚNG MỘT bài, tiêu đề
     "abc". Không có gì để phân trang, để lọc theo nhóm môn, để tìm kiếm.

   CÁCH DỰNG
     46 CHỦ ĐỀ (2 chủ đề cho mỗi môn trong 23 môn) × các KHỐI mà môn đó phục vụ.
     Nhân ra khoảng 234 bài. Cùng một chủ đề dạy khối 3 và khối 9 là hai bài
     khác nhau thật sự: khác thời lượng, khác mức độ, khác lưu ý sư phạm — nên
     tách thành hai bài chứ không phải một bài dùng chung.

     Phạm vi khối của từng môn lấy đúng bảng đã dùng ở TSDMS_Seed_PhanCong.sql
     (môn "Bảng tính Excel" không có bài cho lớp 2).

   NỘI DUNG
     Mỗi bài có giáo án rút gọn nhưng có cấu trúc thật: Mục tiêu · Chuẩn bị ·
     Tiến trình chia mốc thời gian · Đánh giá · Lưu ý theo khối. Mở trang xem chi
     tiết ra đọc được, không phải chữ lấp chỗ trống.

     Thời lượng bám khung tiết của trường: 35 phút (khối 1-5), 45 phút (khối 6-9).
     Mức độ suy từ khối: 1-3 BASIC · 4-6 INTERMEDIATE · 7-9 ADVANCED.
     Trạng thái ~80% PUBLISHED · 10% DRAFT · 10% ARCHIVED để cả ba nhánh của vòng
     đời đều có dữ liệu mà thử.

   FILE ĐÍNH KÈM
     Dùng LINK CANVA demo, KHÔNG seed file vật lý — giữ đúng quyết định ở bản vá
     2026-07-11: thư mục uploads/ đã gitignore nên seed đường dẫn file vào DB sẽ
     tạo ra bản ghi trỏ tới file không tồn tại trên máy người khác.
     Chỉ bài PUBLISHED mới có học liệu; bài nháp thì chưa.

   DỌN RÁC CŨ
     · Xóa bài "abc" (bản ghi test duy nhất đang có).
     · 7 file PDF trong backend/uploads/lessons/{6,432,434,435,436} là file mồ côi
       — bài giảng của chúng đã bị xóa từ lâu. File này chỉ dọn phần DB; xóa file
       trên đĩa bằng lệnh ghi ở cuối header:
           rm -rf backend/uploads/lessons/{6,432,434,435,436}

   GỠ RA: chạy database/seed/TSDMS_Rollback_BaiGiang.sql
   ===================================================================== */

USE TSDMS;
GO

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;

/* ---- CHỐT CHẶN ---- */
IF (SELECT COUNT(*) FROM Lesson WHERE IsDeleted = 0) > 5
BEGIN
    PRINT N'>>> Kho bài giảng đã có dữ liệu — bỏ qua, không chạy lại.';
    PRINT N'    Muốn nạp lại: chạy TSDMS_Rollback_BaiGiang.sql trước.';
    RETURN;
END

BEGIN TRY
BEGIN TRANSACTION;

DECLARE @Admin INT = (SELECT Id FROM AppUser WHERE Username = 'admin' AND IsDeleted = 0);
IF @Admin IS NULL THROW 50040, N'Không tìm thấy tài khoản admin.', 1;

DECLARE @Branch INT = (SELECT TOP 1 Id FROM Branch WHERE IsDeleted = 0 ORDER BY Id);
IF @Branch IS NULL THROW 50041, N'Không tìm thấy chi nhánh nào.', 1;

IF NOT EXISTS (SELECT 1 FROM TeacherSubject)
    THROW 50042, N'Chưa có dữ liệu giáo viên ↔ môn học — chạy TSDMS_Seed_100GiaoVien.sql trước.', 1;

DECLARE @n INT;
DECLARE @NL NVARCHAR(2) = NCHAR(13) + NCHAR(10);

/* Link Canva demo dùng chung cho mọi học liệu — đúng một đường dẫn, ai xem cũng
   biết ngay đây là dữ liệu mẫu chứ không phải học liệu thật của trung tâm. */
DECLARE @Canva VARCHAR(500) = 'https://www.canva.com/design/DAHFbYiN5_0/Qh0oroFgBedLjclajD_Sxg/edit';

/* ═══════════════ 0) DỌN RÁC CŨ ═══════════════ */

DELETE lf FROM LessonFile lf
JOIN Lesson l ON l.Id = lf.LessonId
WHERE l.Title = N'abc';
DELETE FROM Lesson WHERE Title = N'abc';
SET @n = @@ROWCOUNT;
PRINT N'0) Xóa ' + CAST(@n AS NVARCHAR(10)) + N' bài giảng test cũ.';

/* ═══════════════ 1) MÔN NÀO DẠY KHỐI NÀO ═══════════════ */

CREATE TABLE #MonKhoi (Code VARCHAR(20) COLLATE DATABASE_DEFAULT PRIMARY KEY, KhoiMin INT, KhoiMax INT);
INSERT INTO #MonKhoi (Code, KhoiMin, KhoiMax) VALUES
 ('TH01', 3, 9), ('TH02', 6, 9), ('TH03', 7, 9),
 ('TA01', 1, 2), ('TA02', 3, 5), ('TA03', 3, 5), ('TA04', 6, 9), ('TA05', 6, 9), ('TA06', 1, 5),
 ('SA01', 3, 7), ('SA02', 4, 9), ('SA03', 3, 8), ('SA04', 1, 5), ('SA05', 6, 9), ('SA06', 1, 6), ('SA07', 6, 9),
 ('KNS01', 1, 9), ('KNS02', 3, 9), ('KNS03', 1, 9), ('KNS04', 6, 9),
 ('KNS05', 3, 9), ('KNS06', 4, 9), ('KNS07', 6, 9);

/* ═══════════════ 2) 46 CHỦ ĐỀ BÀI GIẢNG ═══════════════ */

CREATE TABLE #ChuDe (
    Code     VARCHAR(20) COLLATE DATABASE_DEFAULT,
    Ord      INT,
    Ten      NVARCHAR(200) COLLATE DATABASE_DEFAULT,
    MoTa     NVARCHAR(500) COLLATE DATABASE_DEFAULT,
    MucTieu  NVARCHAR(500) COLLATE DATABASE_DEFAULT,
    HoatDong NVARCHAR(500) COLLATE DATABASE_DEFAULT,
    ThucHanh NVARCHAR(500) COLLATE DATABASE_DEFAULT,
    PRIMARY KEY (Code, Ord)
);

INSERT INTO #ChuDe (Code, Ord, Ten, MoTa, MucTieu, HoatDong, ThucHanh) VALUES
-- ── Tin học ──────────────────────────────────────────────────────────────
('TH01', 1, N'Máy tính và em',
 N'Làm quen các bộ phận của máy tính, cách dùng chuột và bàn phím, tư thế ngồi đúng khi học với máy.',
 N'Gọi tên được các bộ phận chính của máy tính và sử dụng chuột, bàn phím ở mức cơ bản',
 N'Giáo viên chỉ từng bộ phận trên máy thật, học sinh nhắc lại tên và công dụng; hướng dẫn tư thế ngồi và khoảng cách mắt.',
 N'Học sinh luyện kéo thả chuột và gõ tên mình bằng bàn phím trên phần mềm luyện gõ.'),
('TH01', 2, N'Tổ chức dữ liệu trong máy tính',
 N'Thư mục và tệp tin: cách tạo, đổi tên, sao chép, di chuyển và tìm lại thứ mình đã lưu.',
 N'Tự sắp xếp được bài làm của mình vào thư mục và tìm lại được sau đó',
 N'Ví von cây thư mục với ngăn tủ đựng sách; giáo viên thao tác mẫu tạo - đổi tên - sao chép trên máy chiếu.',
 N'Mỗi học sinh tạo thư mục mang tên mình, lưu 2 tệp vào đó rồi dùng ô tìm kiếm để tìm lại.'),
('TH02', 1, N'Soạn thảo và định dạng văn bản',
 N'Gõ, sửa và trình bày một văn bản ngắn: phông chữ, cỡ chữ, căn lề, danh sách gạch đầu dòng.',
 N'Soạn được văn bản một trang có bố cục rõ ràng, đúng quy tắc gõ dấu tiếng Việt',
 N'Phân tích một văn bản trình bày xấu và một văn bản trình bày đẹp để rút ra quy tắc; hướng dẫn thao tác định dạng.',
 N'Học sinh soạn bản tự giới thiệu một trang, áp dụng ít nhất bốn thao tác định dạng đã học.'),
('TH02', 2, N'Thiết kế bài trình chiếu thuyết phục',
 N'Nguyên tắc một ý một trang, chọn hình ảnh, hạn chế chữ và cách trình bày trước lớp.',
 N'Làm được bộ slide 5 trang trình bày một chủ đề quen thuộc và tự thuyết trình',
 N'So sánh hai slide cùng nội dung, một dày đặc chữ một gọn gàng, để học sinh tự rút ra nguyên tắc.',
 N'Nhóm hai bạn làm bộ slide 5 trang về một chủ đề tự chọn và trình bày trong 3 phút.'),
('TH03', 1, N'Nhập liệu và công thức cơ bản',
 N'Ô, hàng, cột, kiểu dữ liệu và các hàm SUM, AVERAGE, MIN, MAX cho bài toán học tập.',
 N'Lập được bảng điểm cá nhân và tính tổng, trung bình bằng công thức thay vì bấm máy tính tay',
 N'Giáo viên dựng bảng điểm mẫu trên máy chiếu, cố tình sửa một ô để học sinh thấy công thức tự tính lại.',
 N'Học sinh lập bảng theo dõi điểm các môn của mình và tính điểm trung bình bằng hàm.'),
('TH03', 2, N'Biểu đồ và xử lý dữ liệu học tập',
 N'Sắp xếp, lọc dữ liệu và chọn loại biểu đồ phù hợp để nói lên điều mình muốn nói.',
 N'Chọn đúng loại biểu đồ cho từng loại dữ liệu và đọc được thông tin từ biểu đồ',
 N'Đưa cùng một bộ số liệu vẽ bằng ba loại biểu đồ khác nhau, cùng phân tích cái nào dễ hiểu nhất và vì sao.',
 N'Học sinh khảo sát nhanh sở thích của lớp, nhập số liệu và vẽ biểu đồ trình bày kết quả.'),
-- ── Tiếng Anh ────────────────────────────────────────────────────────────
('TA01', 1, N'Chào hỏi và giới thiệu bản thân',
 N'Mẫu câu chào hỏi, hỏi tên và nói tên mình qua bài hát, trò chơi và tranh minh họa.',
 N'Nghe hiểu và nói được các mẫu câu chào hỏi, giới thiệu tên trong tình huống quen thuộc',
 N'Hát bài chào hỏi kèm động tác, giáo viên làm mẫu rồi mời từng cặp học sinh thực hiện.',
 N'Trò chơi vòng tròn: mỗi bạn chào và giới thiệu tên với bạn bên cạnh, không lặp lại mẫu câu của bạn trước.'),
('TA01', 2, N'Màu sắc, con số và đồ vật quanh em',
 N'Từ vựng màu sắc, số đếm 1-20 và tên đồ dùng học tập gắn với đồ vật thật trong lớp.',
 N'Gọi tên được màu sắc, đếm được 1-20 và nói tên đồ dùng học tập bằng tiếng Anh',
 N'Giáo viên giơ đồ vật thật trong lớp, học sinh gọi tên màu và tên đồ vật theo nhịp vỗ tay.',
 N'Trò chơi tìm đồ vật: giáo viên gọi tên màu, học sinh chạy tới chỉ đúng đồ vật màu đó trong lớp.'),
('TA02', 1, N'Gia đình và bạn bè',
 N'Từ vựng về các thành viên gia đình và mẫu câu giới thiệu người thân, mô tả ngoại hình đơn giản.',
 N'Giới thiệu được các thành viên trong gia đình mình bằng 4-5 câu liền mạch',
 N'Dùng cây gia đình vẽ sẵn để dạy từ vựng, sau đó giáo viên kể về gia đình mình làm mẫu.',
 N'Học sinh vẽ cây gia đình của mình và giới thiệu với bạn cùng bàn bằng tiếng Anh.'),
('TA02', 2, N'Trường học của em',
 N'Tên các phòng học, môn học, đồ dùng và mẫu câu hỏi đáp về thời khóa biểu.',
 N'Hỏi và trả lời được về môn học yêu thích, vị trí các phòng trong trường',
 N'Đi tham quan ảo quanh trường qua tranh, mỗi điểm dừng học một từ vựng và một mẫu câu.',
 N'Phỏng vấn chéo: mỗi học sinh hỏi 3 bạn về môn học yêu thích rồi báo cáo kết quả trước lớp.'),
('TA03', 1, N'Sở thích và hoạt động hằng ngày',
 N'Động từ chỉ hoạt động, trạng từ tần suất và cách kể lại một ngày của mình.',
 N'Kể được lịch sinh hoạt một ngày bằng 5-6 câu có dùng trạng từ tần suất',
 N'Dựng thời gian biểu bằng tranh trên bảng, cả lớp cùng đọc và thay đổi giờ để luyện mẫu câu.',
 N'Học sinh viết và nói lịch một ngày của mình, bạn cùng bàn nghe rồi kể lại.'),
('TA03', 2, N'Thức ăn và đồ uống',
 N'Từ vựng món ăn, đồ uống, mẫu câu gọi món và nói về món mình thích hoặc không thích.',
 N'Đóng vai gọi món tại quán ăn và nói được sở thích ăn uống của mình',
 N'Bày thực đơn tranh lên bảng, giáo viên đóng vai người phục vụ để làm mẫu hội thoại.',
 N'Đóng vai theo cặp: một bạn làm khách, một bạn làm người phục vụ, đổi vai sau 3 phút.'),
('TA04', 1, N'Cộng đồng nơi em sống',
 N'Từ vựng về địa điểm công cộng, dịch vụ và mẫu câu chỉ đường, mô tả khu phố.',
 N'Mô tả được nơi mình sống và chỉ đường tới một địa điểm bằng tiếng Anh',
 N'Dùng bản đồ khu phố giả định, giáo viên chỉ đường mẫu rồi học sinh dò theo trên bản đồ của mình.',
 N'Theo cặp: một bạn hỏi đường tới địa điểm bí mật, bạn kia chỉ đường cho tới khi đoán ra.'),
('TA04', 2, N'Môi trường và lối sống xanh',
 N'Từ vựng về ô nhiễm, tái chế, tiết kiệm năng lượng và cách nêu ý kiến, đề xuất giải pháp.',
 N'Nêu được ý kiến về một vấn đề môi trường và đề xuất giải pháp bằng 6-8 câu',
 N'Xem một đoạn phim ngắn không lời về rác thải, học sinh mô tả những gì thấy bằng từ vựng vừa học.',
 N'Nhóm 4 bạn xây dựng và trình bày một kế hoạch xanh cho lớp học trong 2 phút.'),
('TA05', 1, N'Du lịch và trải nghiệm văn hóa',
 N'Từ vựng về phương tiện, chỗ ở, điểm tham quan và cách kể lại một chuyến đi.',
 N'Kể lại được một chuyến đi đã trải qua hoặc lên kế hoạch cho một chuyến đi giả định',
 N'Giáo viên kể một chuyến đi của mình kèm ảnh, học sinh nghe và ghi lại các từ khóa nghe được.',
 N'Nhóm lập kế hoạch chuyến đi 3 ngày với ngân sách cho trước rồi thuyết trình bằng tiếng Anh.'),
('TA05', 2, N'Công nghệ trong đời sống',
 N'Từ vựng về thiết bị, ứng dụng, mạng xã hội và cách nêu lợi ích, hạn chế của công nghệ.',
 N'Trình bày được cả mặt lợi và mặt hại của một công nghệ quen thuộc',
 N'Khảo sát nhanh cả lớp dùng thiết bị gì nhiều nhất, lấy số liệu đó làm ngữ liệu cho bài.',
 N'Tranh biện ngắn theo cặp: một bạn bảo vệ, một bạn phản đối việc dùng điện thoại trong trường.'),
('TA06', 1, N'Âm đầu và âm cuối cơ bản',
 N'Nhận diện và phát âm các phụ âm đầu, phụ âm cuối thường bị bỏ sót trong tiếng Anh.',
 N'Phát âm rõ phụ âm cuối và phân biệt được các cặp từ khác nhau ở âm cuối',
 N'Dùng cặp từ tối thiểu (ship/sheep, cat/cap), giáo viên đọc, học sinh giơ thẻ chọn từ nghe được.',
 N'Luyện đọc theo chuỗi: mỗi bạn đọc một từ trong danh sách, đọc sai âm cuối thì cả nhóm cùng sửa.'),
('TA06', 2, N'Nguyên âm đôi và trọng âm từ',
 N'Nguyên âm đôi thường gặp và quy tắc đặt trọng âm cho từ hai, ba âm tiết.',
 N'Đặt đúng trọng âm cho từ quen thuộc và phát âm được nguyên âm đôi',
 N'Vỗ tay theo âm tiết để học sinh cảm nhận nhịp, giáo viên nhấn mạnh âm tiết mang trọng âm.',
 N'Trò chơi phân loại: học sinh xếp thẻ từ vào ba cột theo vị trí trọng âm.'),
-- ── STEM - AI ────────────────────────────────────────────────────────────
('SA01', 1, N'Nhân vật, sân khấu và khối lệnh đầu tiên',
 N'Giao diện Scratch, cách thêm nhân vật, đổi phông nền và ghép khối lệnh chuyển động.',
 N'Tạo được chương trình đầu tiên cho nhân vật di chuyển và nói theo ý mình',
 N'Giáo viên chiếu màn hình, kéo thả từng khối và cố tình ghép sai để cả lớp cùng tìm lỗi.',
 N'Mỗi học sinh tạo một cảnh nhân vật đi từ trái sang phải rồi chào người xem.'),
('SA01', 2, N'Vòng lặp, điều kiện và trò chơi nhỏ',
 N'Khối lặp, khối điều kiện, biến đếm điểm và cách ghép lại thành một trò chơi bắt vật.',
 N'Dùng được vòng lặp và điều kiện để làm một trò chơi có tính điểm',
 N'Phân tích luật chơi của một trò chơi quen thuộc thành các khối lệnh trước khi lập trình.',
 N'Nhóm hai bạn hoàn thiện trò chơi bắt vật có đếm điểm và có điều kiện kết thúc.'),
('SA02', 1, N'Làm quen Leanbot và điều khiển cơ bản',
 N'Các bộ phận của Leanbot, cách kết nối, nạp chương trình và điều khiển tiến lùi, rẽ trái phải.',
 N'Kết nối được robot và lập trình cho robot đi theo một lộ trình đơn giản',
 N'Giáo viên hướng dẫn quy trình kết nối theo từng bước, nhấn mạnh việc kiểm tra pin và cổng kết nối.',
 N'Nhóm lập trình cho robot đi hết một hình vuông rồi quay về điểm xuất phát.'),
('SA02', 2, N'Cảm biến dò đường và tránh vật cản',
 N'Nguyên lý cảm biến hồng ngoại, cảm biến siêu âm và cách viết điều kiện xử lý tín hiệu.',
 N'Lập trình được robot bám vạch và dừng lại khi gặp vật cản',
 N'Cho robot chạy thử trên sa bàn, dừng lại phân tích vì sao robot lệch vạch để rút ra vai trò của điều kiện.',
 N'Nhóm hiệu chỉnh chương trình cho robot đi hết đường đua có vạch cong và một vật cản.'),
('SA03', 1, N'Lắp ráp và điều khiển Vincibot',
 N'Quy trình lắp ráp an toàn, nhận biết linh kiện và các lệnh điều khiển đầu tiên.',
 N'Lắp được mô hình cơ bản và điều khiển robot theo lệnh mình viết',
 N'Phát sơ đồ lắp, các nhóm lắp song song, giáo viên đi từng bàn kiểm tra điểm nối dễ sai.',
 N'Nhóm lắp xong mô hình rồi lập trình cho robot chạy một đoạn và phát ra âm thanh.'),
('SA03', 2, N'Lập trình chuỗi hành động theo kịch bản',
 N'Phân rã một nhiệm vụ dài thành các bước nhỏ và ghép thành chuỗi lệnh có thứ tự.',
 N'Chia được một nhiệm vụ phức tạp thành các bước và lập trình cho robot thực hiện đủ',
 N'Cùng viết kịch bản bằng lời trên bảng trước, rồi mới chuyển từng câu thành khối lệnh.',
 N'Nhóm lập trình cho robot diễn lại một tình huống ngắn do nhóm tự nghĩ.'),
('SA04', 1, N'Lắp mô hình đầu tiên với Spike Essential',
 N'Nhận biết các chi tiết, cách ghép chắc chắn và giữ gìn bộ kit sau khi học.',
 N'Lắp được mô hình theo hướng dẫn và cất bộ kit đúng chỗ sau buổi học',
 N'Giáo viên lắp mẫu từng bước trên máy chiếu, học sinh làm theo và giơ tay khi xong mỗi bước.',
 N'Nhóm hoàn thành mô hình theo hướng dẫn rồi kiểm tra xem mô hình có chắc chắn không.'),
('SA04', 2, N'Kể chuyện bằng mô hình chuyển động',
 N'Ghép chuyển động của mô hình với một câu chuyện ngắn do học sinh tự nghĩ.',
 N'Dùng được mô hình chuyển động để kể lại một câu chuyện ngắn trước lớp',
 N'Đọc một truyện ngắn quen thuộc, cùng bàn xem chi tiết nào có thể biến thành chuyển động.',
 N'Nhóm dựng và trình diễn câu chuyện của mình trong 2 phút.'),
('SA05', 1, N'Mô phỏng robot trên máy tính',
 N'Môi trường mô phỏng, cách đặt robot, chạy thử và đọc kết quả mà không cần thiết bị thật.',
 N'Chạy được chương trình trên môi trường mô phỏng và giải thích kết quả thu được',
 N'So sánh chạy mô phỏng và chạy thật để học sinh hiểu vì sao cần thử trên máy trước.',
 N'Học sinh lập trình cho robot mô phỏng đi qua ba điểm mốc cho trước.'),
('SA05', 2, N'Giải bài toán mê cung bằng thuật toán',
 N'Ý tưởng bám tường, thử và quay lui; cách diễn đạt thuật toán trước khi viết chương trình.',
 N'Trình bày được thuật toán bằng lời rồi cài đặt để robot thoát khỏi mê cung',
 N'Vẽ mê cung lên bảng, một học sinh đóng vai robot đi theo lệnh của cả lớp để lộ ra chỗ thuật toán sai.',
 N'Nhóm cài đặt thuật toán và cho robot mô phỏng thoát mê cung trong số bước ít nhất có thể.'),
('SA06', 1, N'Chế tạo từ vật liệu tái chế',
 N'Biến chai lọ, bìa carton thành đồ chơi hoặc dụng cụ học tập, kèm ý thức tái sử dụng.',
 N'Làm được một sản phẩm hữu ích từ vật liệu bỏ đi và giải thích ý tưởng của mình',
 N'Bày các vật liệu lên bàn, cả lớp cùng nghĩ xem mỗi thứ có thể biến thành cái gì.',
 N'Nhóm chế tạo sản phẩm trong thời gian quy định rồi giới thiệu công dụng.'),
('SA06', 2, N'Mạch điện đơn giản và đồ chơi tự làm',
 N'Pin, dây dẫn, bóng đèn, công tắc và quy tắc an toàn khi làm việc với điện.',
 N'Lắp được mạch điện kín thắp sáng bóng đèn và nêu được quy tắc an toàn',
 N'Giáo viên lắp mạch mẫu rồi tháo một đầu dây để học sinh thấy vì sao mạch phải kín.',
 N'Nhóm lắp mạch có công tắc và gắn vào một sản phẩm đồ chơi tự làm.'),
('SA07', 1, N'AI là gì và AI học như thế nào',
 N'Phân biệt phần mềm thông thường với AI, và ý tưởng máy học từ dữ liệu qua ví dụ đời thường.',
 N'Nêu được ba ví dụ AI trong đời sống và giải thích AI học từ dữ liệu như thế nào',
 N'Chơi trò đoán con vật: cả lớp đóng vai máy học, giáo viên đưa dần dữ liệu để lớp đoán chính xác hơn.',
 N'Học sinh liệt kê các ứng dụng AI mình từng dùng và chỉ ra AI đó học từ dữ liệu gì.'),
('SA07', 2, N'Nhận dạng hình ảnh với công cụ không cần lập trình',
 N'Huấn luyện mô hình nhận dạng ảnh bằng công cụ trực quan và quan sát ảnh hưởng của dữ liệu.',
 N'Tự huấn luyện được một mô hình nhận dạng đơn giản và nhận ra vì sao nó đoán sai',
 N'Huấn luyện thử với rất ít ảnh để mô hình đoán sai, rồi thêm ảnh cho học sinh thấy dữ liệu quan trọng thế nào.',
 N'Nhóm huấn luyện mô hình phân biệt hai loại đồ vật và thử nghiệm để tìm trường hợp mô hình sai.'),
-- ── Kĩ năng sống ─────────────────────────────────────────────────────────
('KNS01', 1, N'Lắng nghe và đặt câu hỏi',
 N'Dấu hiệu của việc lắng nghe thật sự và cách đặt câu hỏi để hiểu người khác hơn.',
 N'Nhắc lại đúng ý bạn vừa nói và đặt được câu hỏi làm rõ',
 N'Đóng vai hai tình huống trò chuyện, một bên lắng nghe một bên lơ đãng, cả lớp nhận xét khác biệt.',
 N'Theo cặp: một bạn kể trong 2 phút, bạn kia nghe rồi kể lại và đặt hai câu hỏi.'),
('KNS01', 2, N'Đứng trước lớp trình bày ý tưởng',
 N'Cách mở đầu, giữ nhịp nói, dùng ánh mắt và xử lý khi hồi hộp.',
 N'Trình bày được một ý tưởng trong 2 phút với bố cục mở - thân - kết rõ ràng',
 N'Giáo viên trình bày mẫu hai lần, một lần lúng túng một lần tự tin, để học sinh chỉ ra khác biệt.',
 N'Mỗi học sinh trình bày 2 phút về chủ đề tự chọn, nhóm góp ý theo một điểm tốt và một điểm cần sửa.'),
('KNS02', 1, N'Phân vai và chia việc trong nhóm',
 N'Các vai trong nhóm, cách chia việc theo thế mạnh và cam kết thời hạn.',
 N'Nhận và hoàn thành được vai trò của mình trong một nhiệm vụ nhóm',
 N'Giao cùng một nhiệm vụ cho hai nhóm, một nhóm có phân vai một nhóm không, rồi so sánh kết quả.',
 N'Nhóm nhận nhiệm vụ, tự phân vai và ghi bảng phân công trước khi bắt tay làm.'),
('KNS02', 2, N'Xử lý bất đồng và ra quyết định chung',
 N'Cách nêu ý kiến trái chiều mà không làm tổn thương nhau, và các cách chốt quyết định nhóm.',
 N'Nêu được ý kiến khác biệt một cách tôn trọng và tham gia chốt quyết định chung',
 N'Đưa tình huống nhóm cãi nhau chọn chủ đề, cả lớp thử vài cách chốt và bàn cách nào công bằng nhất.',
 N'Nhóm giải quyết một tình huống bất đồng cho sẵn và trình bày cách nhóm đã chốt.'),
('KNS03', 1, N'Quy tắc năm ngón tay và vùng riêng tư',
 N'Ai được phép tiếp xúc tới mức nào, và những vùng trên cơ thể không ai được chạm vào.',
 N'Nói được vùng riêng tư của mình và ai là người an toàn có thể tìm đến',
 N'Dùng hình bàn tay năm ngón, mỗi ngón một nhóm người, cả lớp cùng điền và thảo luận.',
 N'Học sinh vẽ bàn tay của mình, điền tên người ở từng mức và giữ lại làm cẩm nang.'),
('KNS03', 2, N'Nhận diện tình huống nguy hiểm và cách cầu cứu',
 N'Dấu hiệu cảnh báo, cách nói không, cách thoát khỏi tình huống và tìm người trợ giúp.',
 N'Nhận ra dấu hiệu nguy hiểm và biết ít nhất ba cách tìm trợ giúp',
 N'Phân tích các tình huống cho sẵn, cả lớp phân loại an toàn - cần cảnh giác - nguy hiểm.',
 N'Đóng vai theo nhóm: thực hành nói không và tìm người lớn tin cậy trong tình huống được giao.'),
('KNS04', 1, N'Lập kế hoạch tuần và thứ tự ưu tiên',
 N'Ma trận quan trọng - khẩn cấp và cách dựng thời khóa biểu cá nhân khả thi.',
 N'Lập được kế hoạch tuần của mình và giải thích được thứ tự ưu tiên đã chọn',
 N'Liệt kê việc của một tuần điển hình lên bảng, cả lớp cùng xếp vào bốn ô ưu tiên.',
 N'Học sinh lập kế hoạch tuần tới của chính mình và cam kết theo dõi trong 7 ngày.'),
('KNS04', 2, N'Chi tiêu có kế hoạch và tiết kiệm',
 N'Phân biệt cần và muốn, ghi chép chi tiêu và đặt mục tiêu tiết kiệm ngắn hạn.',
 N'Phân loại được khoản chi cần - muốn và lập mục tiêu tiết kiệm cho một món đồ',
 N'Đưa danh sách 15 khoản chi, cả lớp tranh luận khoản nào là cần khoản nào là muốn.',
 N'Học sinh lập bảng chi tiêu một tháng giả định với số tiền cho trước và mục tiêu tiết kiệm.'),
-- ── Kĩ năng số ───────────────────────────────────────────────────────────
('KNS05', 1, N'Dấu chân số và danh tính trên mạng',
 N'Những gì mình để lại khi lên mạng, ai xem được và hậu quả lâu dài của một bài đăng.',
 N'Kể được dấu chân số của mình gồm những gì và biết kiểm tra thiết lập riêng tư',
 N'Tìm thử thông tin công khai của một nhân vật giả định để học sinh thấy người lạ ghép được bao nhiêu.',
 N'Học sinh rà lại thiết lập riêng tư trên một tài khoản của mình và ghi lại thay đổi đã làm.'),
('KNS05', 2, N'Ứng xử văn minh trên không gian mạng',
 N'Quy tắc giao tiếp trên mạng, nhận diện bắt nạt trực tuyến và cách phản ứng.',
 N'Nhận diện được hành vi bắt nạt trực tuyến và biết cách báo cáo, hỗ trợ bạn',
 N'Đọc các đoạn bình luận có thật đã ẩn danh, cả lớp phân loại chấp nhận được hay không.',
 N'Nhóm xây dựng bộ quy tắc ứng xử trực tuyến của lớp và dán lên bảng tin.'),
('KNS06', 1, N'Mật khẩu mạnh và bảo vệ tài khoản',
 N'Cách đặt mật khẩu khó đoán, không dùng lại mật khẩu và bật xác thực hai bước.',
 N'Tự đặt được mật khẩu mạnh và nêu được vì sao không nên dùng chung một mật khẩu',
 N'Chiếu vài mật khẩu phổ biến và thời gian dò ra chúng để học sinh thấy mức độ rủi ro.',
 N'Học sinh tự đặt một mật khẩu theo quy tắc vừa học và kiểm tra độ mạnh bằng công cụ.'),
('KNS06', 2, N'Nhận biết lừa đảo trực tuyến và tin giả',
 N'Dấu hiệu tin nhắn lừa đảo, đường dẫn giả mạo và cách kiểm chứng một thông tin.',
 N'Chỉ ra được dấu hiệu đáng ngờ của một tin nhắn và biết ba cách kiểm chứng thông tin',
 N'Đưa 6 tin nhắn thật giả lẫn lộn, cả lớp bỏ phiếu rồi cùng phân tích từng dấu hiệu.',
 N'Nhóm nhận một tin đồn cho sẵn và tìm bằng chứng xác nhận hoặc bác bỏ trong 10 phút.'),
('KNS07', 1, N'Dùng trợ lý AI để học chứ không để làm hộ',
 N'Ranh giới giữa nhờ AI hỗ trợ và để AI làm thay, cùng cách đặt câu hỏi cho AI hiệu quả.',
 N'Phân biệt được cách dùng AI hỗ trợ học tập và cách dùng khiến mình không học được gì',
 N'Cho hai học sinh cùng giải một bài, một bạn nhờ AI làm hộ một bạn nhờ AI gợi ý, rồi so sánh kết quả kiểm tra lại.',
 N'Học sinh dùng AI để tự đặt câu hỏi ôn tập cho mình rồi tự trả lời mà không xem đáp án.'),
('KNS07', 2, N'Kiểm chứng thông tin do AI tạo ra',
 N'AI có thể nói sai một cách rất tự tin; cách đối chiếu nguồn và trích dẫn có trách nhiệm.',
 N'Phát hiện được thông tin sai trong câu trả lời của AI và biết đối chiếu nguồn gốc',
 N'Chiếu một câu trả lời AI có lỗi sai cài sẵn, cả lớp cùng truy xem sai chỗ nào và vì sao khó nhận ra.',
 N'Nhóm hỏi AI một câu về chủ đề đang học rồi kiểm chứng từng ý bằng sách giáo khoa.');

/* ═══════════════ 3) MẪU DÙNG CHUNG THEO NHÓM MÔN ═══════════════
   Chuẩn bị, khởi động và củng cố giống nhau trong cùng một nhóm môn — viết riêng
   cho từng chủ đề chỉ làm file dài ra mà không thêm thông tin gì. */

CREATE TABLE #MauNhom (
    NhomMon  NVARCHAR(100) COLLATE DATABASE_DEFAULT PRIMARY KEY,
    ChuanBi  NVARCHAR(500) COLLATE DATABASE_DEFAULT,
    KhoiDong NVARCHAR(500) COLLATE DATABASE_DEFAULT,
    CungCo   NVARCHAR(500) COLLATE DATABASE_DEFAULT,
    DanhGia  NVARCHAR(500) COLLATE DATABASE_DEFAULT
);
INSERT INTO #MauNhom (NhomMon, ChuanBi, KhoiDong, CungCo, DanhGia) VALUES
 (N'Tin học',
  N'Phòng máy đã bật sẵn và đăng nhập được; tệp mẫu chép vào máy từng học sinh; máy chiếu.',
  N'Hỏi nhanh về bài trước và nêu tình huống có thật cần dùng máy tính để giải quyết.',
  N'Cả lớp nhắc lại các bước thao tác; giao bài luyện thêm ở nhà nếu có máy.',
  N'Chấm theo sản phẩm học sinh lưu trên máy: đúng thao tác, đặt tên tệp đúng quy ước, hoàn thành đúng giờ.'),
 (N'Tiếng Anh',
  N'Thẻ từ vựng, loa phát audio, tranh minh họa và phiếu bài tập in sẵn.',
  N'Hát hoặc chơi trò chơi từ vựng 3 phút để làm nóng và ôn từ buổi trước.',
  N'Nhắc lại mẫu câu chính; giao nhiệm vụ nói ở nhà và ghi âm gửi lại.',
  N'Quan sát trong hoạt động cặp đôi: phát âm rõ, dùng đúng mẫu câu, dám nói không sợ sai.'),
 (N'STEM - AI',
  N'Bộ kit đủ cho mỗi nhóm, pin đã sạc, máy tính cài sẵn phần mềm, sa bàn hoặc khu vực thử.',
  N'Cho học sinh xem sản phẩm hoàn chỉnh trước để biết mình sẽ làm được gì sau buổi học.',
  N'Các nhóm trình bày sản phẩm 1 phút; kiểm đếm và cất thiết bị đúng vị trí.',
  N'Chấm theo nhóm: sản phẩm chạy được, cả nhóm đều tham gia, giữ gìn thiết bị.'),
 (N'Kĩ năng sống',
  N'Giấy A0, bút màu, phiếu tình huống in sẵn, không gian đủ rộng để chia nhóm.',
  N'Trò chơi phá băng ngắn để cả lớp thoải mái trước khi bước vào chủ đề.',
  N'Mỗi học sinh nói một câu về điều mình sẽ làm khác đi sau buổi học.',
  N'Đánh giá theo mức độ tham gia và chất lượng phần chia sẻ, không chấm điểm đúng sai.'),
 (N'Kĩ năng số',
  N'Máy tính hoặc điện thoại có mạng, phiếu tình huống, máy chiếu để phân tích chung.',
  N'Nêu một vụ việc có thật gần đây liên quan tới chủ đề để học sinh thấy nó không xa lạ.',
  N'Chốt lại nguyên tắc chính; khuyến khích học sinh kể lại cho người nhà nghe.',
  N'Đánh giá qua phần phân tích tình huống: chỉ đúng dấu hiệu, đề xuất được cách xử lý hợp lý.');

/* ═══════════════ 4) SINH BÀI GIẢNG ═══════════════ */

CREATE TABLE #Bai (
    Id        INT IDENTITY(1,1) PRIMARY KEY,
    SubjectId INT,
    TeacherId INT,
    Khoi      INT,
    Ten       NVARCHAR(300) COLLATE DATABASE_DEFAULT,
    MoTa      NVARCHAR(2000) COLLATE DATABASE_DEFAULT,
    NoiDung   NVARCHAR(MAX) COLLATE DATABASE_DEFAULT,
    ThoiLuong INT,
    MucDo     VARCHAR(20) COLLATE DATABASE_DEFAULT,
    TrangThai VARCHAR(20) COLLATE DATABASE_DEFAULT
);

;WITH Khoi AS (
    SELECT TOP (9) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS n FROM sys.all_objects
),
/* Người soạn: một giáo viên dạy được chính môn đó, xoay theo khối để không dồn
   hết bài của một môn cho một người. */
GvMon AS (
    SELECT ts.SubjectId, ts.TeacherId,
           ROW_NUMBER() OVER (PARTITION BY ts.SubjectId ORDER BY ts.TeacherId) - 1 AS rn,
           COUNT(*)     OVER (PARTITION BY ts.SubjectId)                         AS cnt
    FROM TeacherSubject ts
    JOIN Teacher t ON t.Id = ts.TeacherId AND t.IsDeleted = 0 AND t.Status = 'ACTIVE'
)
INSERT INTO #Bai (SubjectId, TeacherId, Khoi, Ten, MoTa, NoiDung, ThoiLuong, MucDo, TrangThai)
SELECT s.Id,
       g.TeacherId,
       k.n,
       cd.Ten + N' — Lớp ' + CAST(k.n AS NVARCHAR(2)),
       cd.MoTa,
       /* Giáo án rút gọn: 5 phần, mốc thời gian bám đúng độ dài tiết học. */
       N'## Mục tiêu' + @NL
     + N'- ' + cd.MucTieu + N'.' + @NL
     + N'- Mức độ yêu cầu phù hợp với học sinh lớp ' + CAST(k.n AS NVARCHAR(2)) + N'.' + @NL + @NL
     + N'## Chuẩn bị' + @NL
     + N'- ' + mn.ChuanBi + @NL + @NL
     + N'## Tiến trình (' + CAST(CASE WHEN k.n <= 5 THEN 35 ELSE 45 END AS NVARCHAR(3)) + N' phút)' + @NL
     + N'1. Khởi động (5 phút): ' + mn.KhoiDong + @NL
     + N'2. Hình thành kiến thức (' + CAST(CASE WHEN k.n <= 5 THEN 10 ELSE 15 END AS NVARCHAR(3)) + N' phút): '
         + cd.HoatDong + @NL
     + N'3. Thực hành (' + CAST(CASE WHEN k.n <= 5 THEN 15 ELSE 20 END AS NVARCHAR(3)) + N' phút): '
         + cd.ThucHanh + @NL
     + N'4. Củng cố và giao việc (5 phút): ' + mn.CungCo + @NL + @NL
     + N'## Đánh giá' + @NL
     + N'- ' + mn.DanhGia + @NL + @NL
     + N'## Lưu ý khi dạy lớp ' + CAST(k.n AS NVARCHAR(2)) + @NL
     + N'- ' + CASE
           WHEN k.n <= 2 THEN N'Học sinh chưa đọc trôi chảy: ra lệnh bằng lời và làm mẫu, tránh phát phiếu nhiều chữ. Đổi hoạt động sau mỗi 8-10 phút.'
           WHEN k.n <= 5 THEN N'Chia nhỏ mỗi thao tác thành một bước và kiểm tra cả lớp làm xong mới sang bước tiếp theo.'
           WHEN k.n <= 7 THEN N'Học sinh bắt đầu ngại phát biểu trước lớp: ưu tiên hoạt động cặp đôi và nhóm nhỏ trước khi gọi trình bày.'
           ELSE N'Tăng phần tự chủ: giao mục tiêu và để học sinh tự chọn cách làm, giáo viên chỉ hỗ trợ khi nhóm bế tắc.'
       END,
       CASE WHEN k.n <= 5 THEN 35 ELSE 45 END,
       CASE WHEN k.n <= 3 THEN 'BASIC' WHEN k.n <= 6 THEN 'INTERMEDIATE' ELSE 'ADVANCED' END,
       /* ~80% đã xuất bản, 10% còn nháp, 10% lưu trữ — cả ba nhánh vòng đời đều
          có dữ liệu để thử bộ lọc trạng thái. */
       CASE (s.Id * 10 + k.n * 3 + cd.Ord) % 10
            WHEN 8 THEN 'DRAFT' WHEN 9 THEN 'ARCHIVED' ELSE 'PUBLISHED' END
FROM #ChuDe cd
JOIN Subject   s  ON s.Code = cd.Code AND s.IsDeleted = 0
JOIN #MonKhoi  mk ON mk.Code = cd.Code
JOIN Khoi      k  ON k.n BETWEEN mk.KhoiMin AND mk.KhoiMax
LEFT JOIN SubjectCategory sc ON sc.Id = s.CategoryId
JOIN #MauNhom  mn ON mn.NhomMon = ISNULL(sc.Name, N'Kĩ năng sống')
JOIN GvMon     g  ON g.SubjectId = s.Id AND g.rn = (k.n + cd.Ord) % g.cnt;

SELECT @n = COUNT(*) FROM #Bai;
PRINT N'4) Đã dựng ' + CAST(@n AS NVARCHAR(10)) + N' bài giảng từ 46 chủ đề × các khối.';

/* ═══════════════ 5) GHI VÀO DB ═══════════════ */

CREATE TABLE #MapBai (Seq INT PRIMARY KEY, LessonId INT);

MERGE INTO Lesson AS tgt
USING (SELECT * FROM #Bai) AS src
ON 1 = 0
WHEN NOT MATCHED THEN
    INSERT (SubjectId, TeacherId, BranchId, Title, Description, Content, GradeLevel,
            Duration, DifficultyLevel, Status, CreatedAt, CreatedBy)
    VALUES (src.SubjectId, src.TeacherId, @Branch, src.Ten, src.MoTa, src.NoiDung,
            N'Lớp ' + CAST(src.Khoi AS NVARCHAR(2)),
            src.ThoiLuong, src.MucDo, src.TrangThai,
            /* Soạn rải rác trong năm học vừa qua, không phải đẻ ra cùng một giây. */
            DATEADD(DAY, -(src.Id % 300), SYSUTCDATETIME()), @Admin)
OUTPUT src.Id, inserted.Id INTO #MapBai (Seq, LessonId);

PRINT N'5) Đã ghi ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' bài giảng vào kho.';

/* ═══════════════ 6) HỌC LIỆU ĐÍNH KÈM ═══════════════ */

INSERT INTO LessonFile (LessonId, FileName, FileUrl, FileType, CreatedAt, CreatedBy)
SELECT m.LessonId, N'Slide - ' + b.Ten, @Canva, 'canva', SYSUTCDATETIME(), @Admin
FROM #MapBai m
JOIN #Bai b ON b.Id = m.Seq
WHERE b.TrangThai = 'PUBLISHED';

PRINT N'6) Đã gắn ' + CAST(@@ROWCOUNT AS NVARCHAR(10)) + N' học liệu (link Canva demo) cho các bài đã xuất bản.';

DROP TABLE #MonKhoi; DROP TABLE #ChuDe; DROP TABLE #MauNhom; DROP TABLE #Bai; DROP TABLE #MapBai;

COMMIT TRANSACTION;
PRINT N'';
PRINT N'>>> XONG. NHỚ xóa file mồ côi trên đĩa:  rm -rf backend/uploads/lessons/{6,432,434,435,436}';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    DROP TABLE IF EXISTS #MonKhoi; DROP TABLE IF EXISTS #ChuDe; DROP TABLE IF EXISTS #MauNhom;
    DROP TABLE IF EXISTS #Bai; DROP TABLE IF EXISTS #MapBai;
    PRINT N'!!! LỖI — đã rollback toàn bộ, DB giữ nguyên.';
    THROW;
END CATCH
GO
