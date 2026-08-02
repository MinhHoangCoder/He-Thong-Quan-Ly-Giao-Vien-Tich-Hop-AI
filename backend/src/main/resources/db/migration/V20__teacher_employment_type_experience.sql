/* =====================================================================
   TSDMS - V20: Teacher — doi bo gia tri EmploymentType + them TeachingExperience
   ---------------------------------------------------------------------
   1) EmploymentType: 'FULL_TIME','PART_TIME','CONTRACT'
                   -> 'CO_HUU' (co huu), 'THINH_GIANG' (thinh giang)
      Nghiep vu trung tam chi phan biet 2 loai nay. Gia tri 'CONTRACT' bo han:
      da ra soat toan bo du an, KHONG cho nao dung toi (khong dropdown nao cho
      chon, khong logic backend nao re nhanh theo no, seed V1 chi dat FULL_TIME)
      - xem dev-note 2026-08-02. Dong du lieu cu lo co 'CONTRACT' thi don ve
      'THINH_GIANG' (gan nghia hon 'CO_HUU': deu la khong bien che tron thoi gian).

   2) Them cot TeachingExperience NVARCHAR(500) NULL - ghi chu nhanh ve kinh
      nghiem giang day, van ban tu do, khong bat buoc.

   LUU Y:
   - KHONG dung toi Employee.EmploymentType: do la loai hinh cua NHAN VIEN trung
     tam (FULL_TIME/PART_TIME, dat o V10) va module xep ca lam viec dang doi
     FULL_TIME de generate lich. Doi nham la gay module do.
   - Danh so V20 chu KHONG phai V18: V18 dang bi khuyet (du an nhay tu V17 len
     V19). Them file V18 bay gio se lam Flyway validate bao "Detected resolved
     migration not applied to database" tren moi DB da chay V19 -> ca nhom khong
     khoi dong duoc app.
   - File chi ALTER + UPDATE, co guard nen chay lai nhieu lan van an toan.
   ===================================================================== */

/* ---------- 1) Bo rang buoc cu (dang chan 'CO_HUU'/'THINH_GIANG') ---------- */
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Teacher_EmpType')
    ALTER TABLE Teacher DROP CONSTRAINT CK_Teacher_EmpType;
GO

/* ---------- 2) Chuyen du lieu cu sang bo gia tri moi ---------- */
UPDATE Teacher
SET EmploymentType = CASE EmploymentType
        WHEN 'FULL_TIME' THEN 'CO_HUU'
        WHEN 'PART_TIME' THEN 'THINH_GIANG'
        WHEN 'CONTRACT'  THEN 'THINH_GIANG'
        ELSE EmploymentType
    END
WHERE EmploymentType IN ('FULL_TIME', 'PART_TIME', 'CONTRACT');
GO

/* ---------- 3) Rang buoc moi ---------- */
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Teacher_EmpType')
    ALTER TABLE Teacher ADD CONSTRAINT CK_Teacher_EmpType
        CHECK (EmploymentType IN ('CO_HUU', 'THINH_GIANG'));
GO

/* ---------- 4) Cot kinh nghiem giang day ----------
   NVARCHAR (khong phai VARCHAR) vi noi dung la tieng Viet co dau.
   500 ky tu: NVARCHAR la kieu bien do dai nen khai 500 khong ton them byte nao
   cho o ngan; con so chi la TRAN. O nhap la textarea nen nguoi dung hay xuong
   dong/liet ke, 255 rat de cham tran. Chan song song bang @Size(max = 500) o DTO
   de tra 400 co thong bao ro, thay vi de SQL Server nem loi 2628 -> 500. */
IF COL_LENGTH('Teacher', 'TeachingExperience') IS NULL
    ALTER TABLE Teacher ADD TeachingExperience NVARCHAR(500) NULL;
GO
