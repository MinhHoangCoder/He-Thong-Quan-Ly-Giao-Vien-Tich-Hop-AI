

/*1. KHU VỰC */
CREATE TABLE KhuVuc (
    MaKhuVuc INT IDENTITY PRIMARY KEY,
    TenKhuVuc NVARCHAR(100) NOT NULL,
    MoTa NVARCHAR(255),
    CreatedAt DATETIME DEFAULT GETDATE()
);

/*2. CHI NHÁNH */
CREATE TABLE ChiNhanh (
    MaChiNhanh INT IDENTITY PRIMARY KEY,
    TenChiNhanh NVARCHAR(150) NOT NULL,
    DiaChi NVARCHAR(255),
    SoDienThoai VARCHAR(15),
    Email VARCHAR(100),
    TrangThai NVARCHAR(50),
    MaKhuVuc INT NOT NULL,

    FOREIGN KEY (MaKhuVuc) REFERENCES KhuVuc(MaKhuVuc)
);

/*3. TRƯỜNG HỌC */
CREATE TABLE TruongHoc (
    MaTruong INT IDENTITY PRIMARY KEY,
    TenTruong NVARCHAR(200) NOT NULL,
    LoaiTruong NVARCHAR(100),
    DiaChi NVARCHAR(255),
    SoDienThoai VARCHAR(15),
    Email VARCHAR(100),
    NamThanhLap INT CHECK (NamThanhLap <= YEAR(GETDATE())),
    TrangThai NVARCHAR(50), -- thời gian gia hạn
    MaChiNhanh INT NOT NULL,

    FOREIGN KEY (MaChiNhanh) REFERENCES ChiNhanh(MaChiNhanh)
);

/*4. CHỨC VỤ */
CREATE TABLE ChucVu (
    MaChucVu INT IDENTITY PRIMARY KEY,
    TenChucVu NVARCHAR(100),
    PhuCap DECIMAL(18,2)
);

/*5. KHOA BỘ MÔN */
CREATE TABLE KhoaBoMon (
    MaKhoa INT IDENTITY PRIMARY KEY,
    TenKhoa NVARCHAR(150),
    MoTa NVARCHAR(255),
    MaTruong INT NOT NULL,

    FOREIGN KEY (MaTruong) REFERENCES TruongHoc(MaTruong)
);

/*6. GIÁO VIÊN */
CREATE TABLE GiaoVien (
    MaGV INT IDENTITY PRIMARY KEY,
    HoTen NVARCHAR(150) NOT NULL,
    NgaySinh DATE,
    GioiTinh BIT,
    CCCD VARCHAR(20) UNIQUE,
    SoDienThoai VARCHAR(15),
    Email VARCHAR(100) UNIQUE,
    DiaChi NVARCHAR(255),
    NgayVaoLam DATE,
    TrangThai NVARCHAR(50),
    MaTruong INT,
    MaKhoa INT,
    MaChucVu INT,

    FOREIGN KEY (MaTruong) REFERENCES TruongHoc(MaTruong),
    FOREIGN KEY (MaKhoa) REFERENCES KhoaBoMon(MaKhoa),
    FOREIGN KEY (MaChucVu) REFERENCES ChucVu(MaChucVu)
);

/*6. CHẤM CÔNG */
CREATE TABLE ChamCong (
    MaChamCong INT IDENTITY PRIMARY KEY,
    MaGV INT NOT NULL,
    NgayChamCong DATE,
    GioVao TIME,
    GioRa TIME,
    TrangThai NVARCHAR(50),

    FOREIGN KEY (MaGV) REFERENCES GiaoVien(MaGV),
    CHECK (GioVao < GioRa)
);

/*7. BẢNG LƯƠNG */
CREATE TABLE BangLuong (
    MaLuong INT IDENTITY PRIMARY KEY,
    MaGV INT NOT NULL,
    Thang INT CHECK (Thang BETWEEN 1 AND 12),
    Nam INT,
    LuongCoBan DECIMAL(18,2),
    PhuCap DECIMAL(18,2),
    Thuong DECIMAL(18,2),
    KhauTru DECIMAL(18,2),

    FOREIGN KEY (MaGV) REFERENCES GiaoVien(MaGV)
);

/*8. LỊCH LÀM VIỆC */
CREATE TABLE LichLamViec (
    MaLichLam INT IDENTITY PRIMARY KEY,
    MaGV INT NOT NULL,
    LoaiCongViec NVARCHAR(100),
    NgayLam DATE,
    GioBatDau TIME,
    GioKetThuc TIME,
    DiaDiem NVARCHAR(255),
    TrangThai NVARCHAR(50),

    FOREIGN KEY (MaGV) REFERENCES GiaoVien(MaGV),
    CHECK (GioBatDau < GioKetThuc)
);

/*9. PHỤ HUYNH */
CREATE TABLE PhuHuynh (
    MaPhuHuynh INT IDENTITY PRIMARY KEY,
    HoTen NVARCHAR(150),
    SoDienThoai VARCHAR(15),
    Email VARCHAR(100),
    DiaChi NVARCHAR(255)
);

/*10. HỌC SINH */
CREATE TABLE HocSinh (
    MaHS INT IDENTITY PRIMARY KEY,
    HoTen NVARCHAR(150),
    NgaySinh DATE,
    GioiTinh BIT,
    DiaChi NVARCHAR(255),
    TrangThai NVARCHAR(50),
    MaTruong INT,

    FOREIGN KEY (MaTruong) REFERENCES TruongHoc(MaTruong)
);

/*11. LIÊN KẾT PHỤ HUYNH - HỌC SINH */
CREATE TABLE HocSinh_PhuHuynh (
    MaHS INT,
    MaPhuHuynh INT,
    MoiQuanHe NVARCHAR(50),

    PRIMARY KEY (MaHS, MaPhuHuynh),
    FOREIGN KEY (MaHS) REFERENCES HocSinh(MaHS),
    FOREIGN KEY (MaPhuHuynh) REFERENCES PhuHuynh(MaPhuHuynh)
);

/*12. LỚP HỌC */
CREATE TABLE LopHoc (
    MaLop INT IDENTITY PRIMARY KEY,
    TenLop NVARCHAR(100),
    Khoi NVARCHAR(50),
    NamHoc NVARCHAR(20),
    MaTruong INT,
    GVCN INT,

    FOREIGN KEY (MaTruong) REFERENCES TruongHoc(MaTruong),
    FOREIGN KEY (GVCN) REFERENCES GiaoVien(MaGV)
);

/*13. LIÊN KẾT LỚP HỌC - HỌC SINH */
CREATE TABLE HocSinh_Lop (
    MaHS INT,
    MaLop INT,
    NamHoc NVARCHAR(20),

    PRIMARY KEY (MaHS, MaLop, NamHoc),
    FOREIGN KEY (MaHS) REFERENCES HocSinh(MaHS),
    FOREIGN KEY (MaLop) REFERENCES LopHoc(MaLop)
);

/*14. MÔN HỌC */
CREATE TABLE MonHoc (
    MaMon INT IDENTITY PRIMARY KEY,
    TenMon NVARCHAR(150),
    LoaiMonHoc NVARCHAR(50),
    SoTiet INT,
    SoTinChi INT NULL,
    MaKhoa INT,
    MaTruong INT,

    FOREIGN KEY (MaKhoa) REFERENCES KhoaBoMon(MaKhoa),
    FOREIGN KEY (MaTruong) REFERENCES TruongHoc(MaTruong)
);

/*15. PHÒNG HỌC */
CREATE TABLE PhongHoc (
    MaPhong INT IDENTITY PRIMARY KEY,
    TenPhong NVARCHAR(100),
    SucChua INT,
    LoaiPhong NVARCHAR(100),
    MaTruong INT,

    FOREIGN KEY (MaTruong) REFERENCES TruongHoc(MaTruong)
);

/*16. PHÂN CÔNG GIẢNG DẠY */
CREATE TABLE PhanCongGiangDay (
    MaPhanCong INT IDENTITY PRIMARY KEY,
    MaGV INT,
    MaMon INT,
    MaLop INT,
    HocKy INT,
    NamHoc NVARCHAR(20),

    FOREIGN KEY (MaGV) REFERENCES GiaoVien(MaGV),
    FOREIGN KEY (MaMon) REFERENCES MonHoc(MaMon),
    FOREIGN KEY (MaLop) REFERENCES LopHoc(MaLop)
);

/*17. LỊCH HỌC */
CREATE TABLE LichHoc (
    MaLichHoc INT IDENTITY PRIMARY KEY,
    MaGV INT,
    MaMon INT,
    MaLop INT,
    MaPhong INT,
    ThuHoc NVARCHAR(20),
    TietBatDau INT,
    TietKetThuc INT,
    HocKy INT,
    NamHoc NVARCHAR(20),

    FOREIGN KEY (MaGV) REFERENCES GiaoVien(MaGV),
    FOREIGN KEY (MaMon) REFERENCES MonHoc(MaMon),
    FOREIGN KEY (MaLop) REFERENCES LopHoc(MaLop),
    FOREIGN KEY (MaPhong) REFERENCES PhongHoc(MaPhong),

    UNIQUE(MaGV, ThuHoc, TietBatDau, TietKetThuc),
    UNIQUE(MaLop, ThuHoc, TietBatDau, TietKetThuc),
    UNIQUE(MaPhong, ThuHoc, TietBatDau, TietKetThuc)
);

/*18. LOẠI ĐIỂM */
CREATE TABLE LoaiDiem (
    MaLoaiDiem INT IDENTITY PRIMARY KEY,
    TenLoai NVARCHAR(100),
    HeSo INT
);

/*19. CHI TIẾT ĐIỂM */
CREATE TABLE ChiTietDiem (
    MaChiTietDiem INT IDENTITY PRIMARY KEY,
    MaHS INT,
    MaMon INT,
    MaLoaiDiem INT,
    Diem DECIMAL(4,2) CHECK (Diem BETWEEN 0 AND 10),
    HocKy INT,
    NamHoc NVARCHAR(20),

    FOREIGN KEY (MaHS) REFERENCES HocSinh(MaHS),
    FOREIGN KEY (MaMon) REFERENCES MonHoc(MaMon),
    FOREIGN KEY (MaLoaiDiem) REFERENCES LoaiDiem(MaLoaiDiem)
);

/*20. KỲ THI */
CREATE TABLE KyThi (
    MaKyThi INT IDENTITY PRIMARY KEY,
    TenKyThi NVARCHAR(150),
    HocKy INT,
    NamHoc NVARCHAR(20),
    NgayBatDau DATE,
    NgayKetThuc DATE,
    MaTruong INT,

    FOREIGN KEY (MaTruong) REFERENCES TruongHoc(MaTruong)
);

/*21. KẾT QUẢ KỲ THI */
CREATE TABLE KetQuaKyThi (
    MaKetQua INT IDENTITY PRIMARY KEY,
    MaKyThi INT,
    MaHS INT,
    MaMon INT,
    Diem DECIMAL(4,2) CHECK (Diem BETWEEN 0 AND 10),
    XepLoai NVARCHAR(50),

    FOREIGN KEY (MaKyThi) REFERENCES KyThi(MaKyThi),
    FOREIGN KEY (MaHS) REFERENCES HocSinh(MaHS),
    FOREIGN KEY (MaMon) REFERENCES MonHoc(MaMon)
);

/*22. HỌC PHÍ */
CREATE TABLE HocPhi (
    MaHocPhi INT IDENTITY PRIMARY KEY,
    MaHS INT,
    HocKy INT,
    NamHoc NVARCHAR(20),
    SoTien DECIMAL(18,2),
    HanDong DATE,
    TrangThai NVARCHAR(50),

    FOREIGN KEY (MaHS) REFERENCES HocSinh(MaHS)
);

/*23. THANH TOÁN HỌC PHÍ */
CREATE TABLE ThanhToanHocPhi (
    MaThanhToan INT IDENTITY PRIMARY KEY,
    MaHocPhi INT,
    SoTienThanhToan DECIMAL(18,2),
    NgayThanhToan DATE,
    PhuongThucThanhToan NVARCHAR(100),

    FOREIGN KEY (MaHocPhi) REFERENCES HocPhi(MaHocPhi)
);

/*24. TÀI KHOẢN */
CREATE TABLE TaiKhoan (
    MaTK INT IDENTITY PRIMARY KEY,
    TenDangNhap VARCHAR(50) UNIQUE NOT NULL,
    MatKhau VARCHAR(255) NOT NULL,
    LoaiNguoiDung NVARCHAR(50),
    NguoiDungID INT,
    TrangThai NVARCHAR(50)
);

CREATE TABLE VaiTro (
    MaVaiTro INT IDENTITY PRIMARY KEY,
    TenVaiTro NVARCHAR(100) NOT NULL
);

CREATE TABLE TaiKhoan_VaiTro (
    MaTK INT,
    MaVaiTro INT,

    PRIMARY KEY (MaTK, MaVaiTro),
    FOREIGN KEY (MaTK) REFERENCES TaiKhoan(MaTK),
    FOREIGN KEY (MaVaiTro) REFERENCES VaiTro(MaVaiTro)
);


CREATE INDEX IX_HocSinh_HoTen ON HocSinh(HoTen);
CREATE INDEX IX_GiaoVien_HoTen ON GiaoVien(HoTen);
CREATE INDEX IX_MonHoc_TenMon ON MonHoc(TenMon);