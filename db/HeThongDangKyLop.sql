SET NOCOUNT ON;
GO
IF DB_ID(N'HeThongDangKyLop') IS NULL
BEGIN
    CREATE DATABASE HeThongDangKyLop;
END;
GO
USE HeThongDangKyLop;
GO

-- Bang TaiKhoan
IF OBJECT_ID(N'dbo.TaiKhoan', N'U') IS NULL
BEGIN
CREATE TABLE TaiKhoan(
    MaTaiKhoan BIGINT IDENTITY(1, 1) NOT NULL PRIMARY KEY,
    Email NVARCHAR(255) NOT NULL CONSTRAINT UQ_TaiKhoan_Email UNIQUE,
    TenDangNhap NVARCHAR(40) NULL,
    MatKhauHash NVARCHAR(255) NULL,
    VaiTro NVARCHAR(20) NOT NULL,
    TrangThai NVARCHAR(20) NOT NULL DEFAULT N'PENDING',
    DaKichHoat BIT NOT NULL DEFAULT 0,
    BuocDoiMatKhau BIT NOT NULL DEFAULT 1,
    NgayTao DATETIME2(7) NOT NULL DEFAULT SYSDATETIME(),
    NgayKichHoat DATETIME2(7) NULL,
    LanDangNhapCuoi DATETIME2(7) NULL,
    CONSTRAINT CK_TaiKhoan_VaiTro CHECK (VaiTro IN (N'ADMIN', N'STUDENT', N'TEACHER')),
    CONSTRAINT CK_TaiKhoan_TrangThai CHECK (TrangThai IN (N'PENDING', N'ACTIVE', N'LOCKED'))
);
END;
GO

-- Bang SinhVien
IF OBJECT_ID(N'dbo.SinhVien', N'U') IS NULL
BEGIN
CREATE TABLE SinhVien(
    MaSinhVien BIGINT IDENTITY(1, 1) NOT NULL PRIMARY KEY,
    MaTaiKhoan BIGINT NOT NULL REFERENCES TaiKhoan(MaTaiKhoan),
    HoTen NVARCHAR(150) NOT NULL,
    Khoa NVARCHAR(150) NULL,
    Nganh NVARCHAR(150) NULL,
    NgaySinh DATE NULL,
    KhoaHoc NVARCHAR(30) NULL,
    CONSTRAINT UQ_SinhVien_TaiKhoan UNIQUE (MaTaiKhoan)
);
END;
GO

-- Bang GiangVien
IF OBJECT_ID(N'dbo.GiangVien', N'U') IS NULL
BEGIN
CREATE TABLE GiangVien(
    MaGiangVien BIGINT IDENTITY(1, 1) NOT NULL PRIMARY KEY,
    MaTaiKhoan BIGINT NOT NULL REFERENCES TaiKhoan(MaTaiKhoan),
    HoTen NVARCHAR(150) NOT NULL,
    Khoa NVARCHAR(150) NULL,
    BoMon NVARCHAR(150) NULL,
    NgaySinh DATE NULL,
    CONSTRAINT UQ_GiangVien_TaiKhoan UNIQUE (MaTaiKhoan)
);
END;
GO

-- Bang HocPhan
IF OBJECT_ID(N'dbo.HocPhan', N'U') IS NULL
BEGIN
CREATE TABLE HocPhan(
    MaHocPhan BIGINT IDENTITY(1, 1) NOT NULL PRIMARY KEY,
    MaHocPhanCode NVARCHAR(30) NOT NULL CONSTRAINT UQ_HocPhan_MaCode UNIQUE,
    TenHocPhan NVARCHAR(200) NOT NULL,
    SoTinChi INT NOT NULL,
    NamDaoTao INT NULL,
    HocKyDuKien INT NULL,
    NganhDaoTao NVARCHAR(150) NULL,
    CONSTRAINT CK_HocPhan_TinChi CHECK (SoTinChi BETWEEN 1 AND 10),
    CONSTRAINT CK_HocPhan_Nam CHECK (NamDaoTao IS NULL OR NamDaoTao BETWEEN 1 AND 4),
    CONSTRAINT CK_HocPhan_HocKy CHECK (HocKyDuKien IS NULL OR HocKyDuKien IN (1, 2)),
    CONSTRAINT CK_HocPhan_NamKyCap CHECK (
        (NamDaoTao IS NULL AND HocKyDuKien IS NULL) OR
        (NamDaoTao IS NOT NULL AND HocKyDuKien IS NOT NULL)
    )
);
END;
GO

-- Bang HocKy
IF OBJECT_ID(N'dbo.HocKy', N'U') IS NULL
BEGIN
CREATE TABLE HocKy(
    MaHocKy BIGINT IDENTITY(1, 1) NOT NULL PRIMARY KEY,
    TenHocKy NVARCHAR(30) NOT NULL,
    NamHoc NVARCHAR(20) NOT NULL,
    NgayBatDau DATE NULL,
    NgayKetThuc DATE NULL,
    DangMoDangKy BIT NOT NULL DEFAULT 0,
    MoDangKyTu DATETIME2(7) NULL,
    DongDangKyLuc DATETIME2(7) NULL,
    CONSTRAINT UQ_HocKy_Ten_Nam UNIQUE (TenHocKy, NamHoc),
    CONSTRAINT CK_HocKy_Ngay CHECK (
        NgayBatDau IS NULL OR NgayKetThuc IS NULL OR NgayKetThuc >= NgayBatDau
    ),
    CONSTRAINT CK_HocKy_KhoangDangKy CHECK (
        MoDangKyTu IS NULL OR DongDangKyLuc IS NULL OR MoDangKyTu < DongDangKyLuc
    )
);
END;
GO

-- Bang LopHoc
IF OBJECT_ID(N'dbo.LopHoc', N'U') IS NULL
BEGIN
CREATE TABLE LopHoc(
    MaLop BIGINT IDENTITY(1, 1) NOT NULL PRIMARY KEY,
    TenLop NVARCHAR(100) NOT NULL,
    MaHocKy BIGINT NOT NULL REFERENCES HocKy(MaHocKy),
    MaHocPhan BIGINT NOT NULL REFERENCES HocPhan(MaHocPhan),
    MaGiangVien BIGINT NOT NULL REFERENCES GiangVien(MaGiangVien),
    PhongHoc NVARCHAR(100) NULL,
    LichHoc NVARCHAR(200) NULL,
    SucChua INT NOT NULL,
    SoLuongDaDangKy INT NOT NULL DEFAULT 0,
    SinhVienRRGanNhat BIGINT NULL,
    TrangThai NVARCHAR(20) NOT NULL DEFAULT N'OPEN',
    CONSTRAINT CK_LopHoc_SucChua CHECK (SucChua > 0),
    CONSTRAINT CK_LopHoc_SiSo CHECK (SoLuongDaDangKy >= 0 AND SoLuongDaDangKy <= SucChua),
    CONSTRAINT CK_LopHoc_TrangThai CHECK (TrangThai IN (N'OPEN', N'CLOSED', N'CANCELLED'))
);
END;
GO

-- Bang DangKy
IF OBJECT_ID(N'dbo.DangKy', N'U') IS NULL
BEGIN
CREATE TABLE DangKy(
    MaDangKy BIGINT IDENTITY(1, 1) NOT NULL PRIMARY KEY,
    MaSinhVien BIGINT NOT NULL REFERENCES SinhVien(MaSinhVien),
    MaLop BIGINT NOT NULL REFERENCES LopHoc(MaLop),
    ThoiGianDangKy DATETIME2(7) NOT NULL,
    TrangThai NVARCHAR(20) NOT NULL,
    CONSTRAINT UQ_DangKy_SinhVien_Lop UNIQUE (MaSinhVien, MaLop),
    CONSTRAINT CK_DangKy_TrangThai CHECK (TrangThai IN (N'ACTIVE', N'CANCELLED'))
);
END;
GO

-- Bang DanhSachCho
IF OBJECT_ID(N'dbo.DanhSachCho', N'U') IS NULL
BEGIN
CREATE TABLE DanhSachCho(
    MaDSC BIGINT IDENTITY(1, 1) NOT NULL PRIMARY KEY,
    MaSinhVien BIGINT NOT NULL REFERENCES SinhVien(MaSinhVien),
    MaLop BIGINT NOT NULL REFERENCES LopHoc(MaLop),
    ThoiDiemThamGia DATETIME2(7) NOT NULL,
    TrangThai NVARCHAR(20) NOT NULL,
    LyDoKhongDuDieuKien NVARCHAR(500) NULL,
    ThoiDiemCapNhat DATETIME2(7) NULL,
    CONSTRAINT UQ_DanhSachCho_SinhVien_Lop UNIQUE (MaSinhVien, MaLop),
    CONSTRAINT CK_DanhSachCho_TrangThai CHECK (
        TrangThai IN (N'WAITING', N'PROMOTED', N'WITHDRAWN', N'INELIGIBLE')
    )
);
END;
GO

-- Bang TokenKichHoat
IF OBJECT_ID(N'dbo.TokenKichHoat', N'U') IS NULL
BEGIN
CREATE TABLE TokenKichHoat(
    MaToken BIGINT IDENTITY(1, 1) NOT NULL PRIMARY KEY,
    MaTaiKhoan BIGINT NOT NULL REFERENCES TaiKhoan(MaTaiKhoan),
    TokenHash CHAR(64) NOT NULL CONSTRAINT UQ_TokenKichHoat_Hash UNIQUE,
    ThoiDiemTao DATETIME2(7) NOT NULL,
    ThoiDiemHetHan DATETIME2(7) NOT NULL,
    ThoiDiemSuDung DATETIME2(7) NULL,
    CONSTRAINT CK_TokenKichHoat_Han CHECK (ThoiDiemHetHan > ThoiDiemTao)
);
END;
GO

-- Bang NhatKyQuyetDinh
IF OBJECT_ID(N'dbo.NhatKyQuyetDinh', N'U') IS NULL
BEGIN
CREATE TABLE NhatKyQuyetDinh(
    MaNhatKy BIGINT IDENTITY(1, 1) NOT NULL PRIMARY KEY,
    MaLop BIGINT NOT NULL,
    MaSinhVien BIGINT NULL,
    MaDSC BIGINT NULL,
    MaDangKy BIGINT NULL,
    MaTaiKhoanThucHien BIGINT NULL,
    ThoiDiem DATETIME2(7) NOT NULL,
    KetQua NVARCHAR(50) NOT NULL,
    LyDo NVARCHAR(500) NULL
);
END;
GO

-- Bang LuotXetRoundRobin
IF OBJECT_ID(N'dbo.LuotXetRoundRobin', N'U') IS NULL
BEGIN
CREATE TABLE LuotXetRoundRobin(
    MaLuotXet BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    MaLop BIGINT NOT NULL,
    ThoiDiem DATETIME2(7) NOT NULL,
    NguyenNhan NVARCHAR(100) NOT NULL,
    SiSoTruoc INT NOT NULL,
    SiSoSau INT NOT NULL,
    SucChua INT NOT NULL,
    ConTroTruoc BIGINT NULL,
    ConTroSau BIGINT NULL,
    SoNguoiDuocCap INT NOT NULL,
    KetQua NVARCHAR(60) NOT NULL
);
END;
GO

-- Bang ChiTietXetRoundRobin
IF OBJECT_ID(N'dbo.ChiTietXetRoundRobin', N'U') IS NULL
BEGIN
CREATE TABLE ChiTietXetRoundRobin(
    MaChiTiet BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    MaLuotXet BIGINT NOT NULL REFERENCES LuotXetRoundRobin(MaLuotXet),
    ThuTuXet INT NOT NULL,
    MaSinhVien BIGINT NOT NULL,
    MaDSC BIGINT NULL,
    KetQua NVARCHAR(60) NOT NULL,
    LyDo NVARCHAR(500) NULL,
    CONSTRAINT UQ_ChiTietXetRoundRobin_ThuTu UNIQUE(MaLuotXet, ThuTuXet)
);
END;
GO

-- Bang CuocTroChuyen
IF OBJECT_ID(N'dbo.CuocTroChuyen', N'U') IS NULL
BEGIN
CREATE TABLE CuocTroChuyen(
    MaCuocTroChuyen BIGINT IDENTITY(1, 1) NOT NULL PRIMARY KEY,
    MaTaiKhoanNguoiDung BIGINT NOT NULL REFERENCES TaiKhoan(MaTaiKhoan),
    MaTaiKhoanQuanTri BIGINT NULL REFERENCES TaiKhoan(MaTaiKhoan),
    ThoiDiemTao DATETIME2(7) NOT NULL,
    ThoiDiemCapNhat DATETIME2(7) NOT NULL,
    TrangThai NVARCHAR(20) NOT NULL,
    CONSTRAINT CK_CuocTroChuyen_TrangThai CHECK (TrangThai IN (N'OPEN', N'IN_PROGRESS', N'CLOSED'))
);
END;
GO

-- Bang TinNhan
IF OBJECT_ID(N'dbo.TinNhan', N'U') IS NULL
BEGIN
CREATE TABLE TinNhan(
    MaTinNhan BIGINT IDENTITY(1, 1) NOT NULL PRIMARY KEY,
    MaCuocTroChuyen BIGINT NOT NULL REFERENCES CuocTroChuyen(MaCuocTroChuyen),
    MaTaiKhoanGui BIGINT NOT NULL REFERENCES TaiKhoan(MaTaiKhoan),
    NoiDung NVARCHAR(MAX) NOT NULL,
    ThoiDiemGui DATETIME2(7) NOT NULL,
    DaDoc BIT NOT NULL DEFAULT 0,
    ThoiDiemDoc DATETIME2(7) NULL
);
END;
GO

-- Bang TienDoHocTap
IF OBJECT_ID(N'dbo.TienDoHocTap', N'U') IS NULL
BEGIN
CREATE TABLE TienDoHocTap (
    MaSinhVien BIGINT NOT NULL PRIMARY KEY REFERENCES SinhVien(MaSinhVien),
    TrangThai VARCHAR(20) NOT NULL DEFAULT 'ON_TRACK',
    NamDaoTaoThucTe INT NULL,
    GhiChu NVARCHAR(500) NULL,
    CapNhatLuc DATETIME2(7) NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT CK_TienDoHocTap_TrangThai CHECK (TrangThai IN ('ON_TRACK','DELAYED','SUSPENDED','RETURNED')),
    CONSTRAINT CK_TienDoHocTap_Nam CHECK (NamDaoTaoThucTe BETWEEN 1 AND 4 OR NamDaoTaoThucTe IS NULL)
);
END;
GO

-- Bang QuyenHocLai
IF OBJECT_ID(N'dbo.QuyenHocLai', N'U') IS NULL
BEGIN
CREATE TABLE QuyenHocLai (
    MaQuyen BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    MaSinhVien BIGINT NOT NULL REFERENCES SinhVien(MaSinhVien),
    MaHocPhan BIGINT NOT NULL REFERENCES HocPhan(MaHocPhan),
    CONSTRAINT UQ_QuyenHocLai UNIQUE(MaSinhVien,MaHocPhan)
);
END;
GO

-- Dong bo cot bo sung V20.68 cho database cu.
IF COL_LENGTH(N'dbo.DanhSachCho', N'LyDoKhongDuDieuKien') IS NULL
    ALTER TABLE dbo.DanhSachCho ADD LyDoKhongDuDieuKien NVARCHAR(500) NULL;
GO
IF COL_LENGTH(N'dbo.DanhSachCho', N'ThoiDiemCapNhat') IS NULL
    ALTER TABLE dbo.DanhSachCho ADD ThoiDiemCapNhat DATETIME2(7) NULL;
GO
-- Backfill de man hinh hien thi thoi diem trang thai cho du lieu lich su.
UPDATE dbo.DanhSachCho
SET ThoiDiemCapNhat=ThoiDiemThamGia
WHERE ThoiDiemCapNhat IS NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.LuotXetRoundRobin') AND name=N'IX_LuotXetRoundRobin_ThoiDiem')
    CREATE INDEX IX_LuotXetRoundRobin_ThoiDiem ON LuotXetRoundRobin(ThoiDiem DESC);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.TaiKhoan') AND name=N'UX_TaiKhoan_TenDangNhap')
    CREATE UNIQUE INDEX UX_TaiKhoan_TenDangNhap
    ON TaiKhoan(TenDangNhap) WHERE TenDangNhap IS NOT NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.HocPhan') AND name=N'IX_HocPhan_Nam_Ky')
    CREATE INDEX IX_HocPhan_Nam_Ky ON HocPhan(NamDaoTao, HocKyDuKien);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.HocKy') AND name=N'IX_HocKy_DangMoDangKy')
    CREATE INDEX IX_HocKy_DangMoDangKy ON HocKy(DangMoDangKy);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.LopHoc') AND name=N'IX_LopHoc_GiangVien')
    CREATE INDEX IX_LopHoc_GiangVien ON LopHoc(MaGiangVien);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.LopHoc') AND name=N'IX_LopHoc_HocKy_TrangThai')
    CREATE INDEX IX_LopHoc_HocKy_TrangThai ON LopHoc(MaHocKy, TrangThai);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.LopHoc') AND name=N'IX_LopHoc_HocPhan')
    CREATE INDEX IX_LopHoc_HocPhan ON LopHoc(MaHocPhan);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.DangKy') AND name=N'IX_DangKy_Lop_TrangThai')
    CREATE INDEX IX_DangKy_Lop_TrangThai ON DangKy(MaLop, TrangThai);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.DangKy') AND name=N'IX_DangKy_SinhVien_TrangThai')
    CREATE INDEX IX_DangKy_SinhVien_TrangThai ON DangKy(MaSinhVien, TrangThai);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.DanhSachCho') AND name=N'IX_DanhSachCho_RoundRobin')
    CREATE INDEX IX_DanhSachCho_RoundRobin ON DanhSachCho(MaLop, TrangThai, MaSinhVien);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.DanhSachCho') AND name=N'IX_DanhSachCho_SinhVien_TrangThai')
    CREATE INDEX IX_DanhSachCho_SinhVien_TrangThai ON DanhSachCho(MaSinhVien, TrangThai);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.NhatKyQuyetDinh') AND name=N'IX_NhatKyQuyetDinh_Lop_ThoiDiem')
    CREATE INDEX IX_NhatKyQuyetDinh_Lop_ThoiDiem ON NhatKyQuyetDinh(MaLop, ThoiDiem);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.CuocTroChuyen') AND name=N'IX_CuocTroChuyen_User_Status')
    CREATE INDEX IX_CuocTroChuyen_User_Status ON CuocTroChuyen(MaTaiKhoanNguoiDung, TrangThai);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.TinNhan') AND name=N'IX_TinNhan_Conversation_Time')
    CREATE INDEX IX_TinNhan_Conversation_Time ON TinNhan(MaCuocTroChuyen, ThoiDiemGui);
GO

-- V20.51: 12 lop SWE421/SWE422/SWE423. Chi them khi du du lieu phu thuoc.
-- Neu du lieu hoc vu chua duoc import, bo qua an toan va co the chay lai sau khi import.
SET XACT_ABORT ON;
GO
IF (SELECT COUNT(DISTINCT MaHocPhanCode) FROM dbo.HocPhan
    WHERE MaHocPhanCode IN (N'SWE421',N'SWE422',N'SWE423')
      AND NamDaoTao=4 AND HocKyDuKien=2 AND NganhDaoTao=N'Kỹ thuật phần mềm') = 3
AND EXISTS (SELECT 1 FROM dbo.HocKy WHERE TenHocKy=N'Học kỳ 2' AND NamHoc=N'2026-2027')
AND (SELECT COUNT(DISTINCT tk.TenDangNhap)
     FROM dbo.TaiKhoan tk JOIN dbo.GiangVien gv ON gv.MaTaiKhoan=tk.MaTaiKhoan
     WHERE tk.VaiTro=N'TEACHER' AND tk.TenDangNhap IN
       (N'GV037',N'GV038',N'GV039',N'GV040',N'GV041',N'GV042',
        N'GV043',N'GV044',N'GV045',N'GV046',N'GV047',N'GV048'))=12
BEGIN
BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @LopMoi TABLE (
      TenLop NVARCHAR(100), MaMon NVARCHAR(30), TenHocKy NVARCHAR(30), NamHoc NVARCHAR(20),
      TenGiangVien NVARCHAR(40), PhongHoc NVARCHAR(100), LichHoc NVARCHAR(200),
      SucChua INT, TrangThai NVARCHAR(20)
    );
    INSERT INTO @LopMoi (TenLop,MaMon,TenHocKy,NamHoc,TenGiangVien,PhongHoc,LichHoc,SucChua,TrangThai)
    VALUES
(N'SWE421-HK2-01',N'SWE421',N'Học kỳ 2',N'2026-2027',N'GV037',N'B201',N'Thứ 2 13:00-15:00',40,N'OPEN'),
(N'SWE421-HK2-02',N'SWE421',N'Học kỳ 2',N'2026-2027',N'GV038',N'B202',N'Thứ 2 13:00-15:00',45,N'OPEN'),
(N'SWE421-HK2-03',N'SWE421',N'Học kỳ 2',N'2026-2027',N'GV039',N'B203',N'Thứ 2 13:00-15:00',50,N'OPEN'),
(N'SWE421-HK2-04',N'SWE421',N'Học kỳ 2',N'2026-2027',N'GV040',N'B204',N'Thứ 2 13:00-15:00',55,N'OPEN'),
(N'SWE422-HK2-01',N'SWE422',N'Học kỳ 2',N'2026-2027',N'GV041',N'B201',N'Thứ 3 13:00-15:00',40,N'OPEN'),
(N'SWE422-HK2-02',N'SWE422',N'Học kỳ 2',N'2026-2027',N'GV042',N'B202',N'Thứ 3 13:00-15:00',45,N'OPEN'),
(N'SWE422-HK2-03',N'SWE422',N'Học kỳ 2',N'2026-2027',N'GV043',N'B203',N'Thứ 3 13:00-15:00',50,N'OPEN'),
(N'SWE422-HK2-04',N'SWE422',N'Học kỳ 2',N'2026-2027',N'GV044',N'B204',N'Thứ 3 13:00-15:00',55,N'OPEN'),
(N'SWE423-HK2-01',N'SWE423',N'Học kỳ 2',N'2026-2027',N'GV045',N'B201',N'Thứ 4 13:00-15:00',40,N'OPEN'),
(N'SWE423-HK2-02',N'SWE423',N'Học kỳ 2',N'2026-2027',N'GV046',N'B202',N'Thứ 4 13:00-15:00',45,N'OPEN'),
(N'SWE423-HK2-03',N'SWE423',N'Học kỳ 2',N'2026-2027',N'GV047',N'B203',N'Thứ 4 13:00-15:00',50,N'OPEN'),
(N'SWE423-HK2-04',N'SWE423',N'Học kỳ 2',N'2026-2027',N'GV048',N'B204',N'Thứ 4 13:00-15:00',55,N'OPEN');

    IF EXISTS (
      SELECT 1 FROM @LopMoi s
      LEFT JOIN HocPhan h ON h.MaHocPhanCode=s.MaMon
      WHERE h.MaHocPhan IS NULL OR h.NamDaoTao<>4 OR h.HocKyDuKien<>2
         OR h.NganhDaoTao<>N'Kỹ thuật phần mềm'
    ) THROW 51051, N'Học phần SWE421/SWE422/SWE423 chưa có hoặc chưa được gán đúng năm 4, học kỳ 2, ngành Kỹ thuật phần mềm.', 1;

    IF EXISTS (
      SELECT 1 FROM @LopMoi s
      LEFT JOIN HocKy hk ON hk.TenHocKy=s.TenHocKy AND hk.NamHoc=s.NamHoc
      WHERE hk.MaHocKy IS NULL
    ) THROW 51052, N'Chưa tìm thấy Học kỳ 2 năm 2026-2027 trong SQL Server.', 1;

    IF EXISTS (
      SELECT 1 FROM @LopMoi s
      LEFT JOIN TaiKhoan tk ON tk.TenDangNhap=s.TenGiangVien AND tk.VaiTro=N'TEACHER'
      LEFT JOIN GiangVien gv ON gv.MaTaiKhoan=tk.MaTaiKhoan
      WHERE gv.MaGiangVien IS NULL
    ) THROW 51053, N'Chưa có hồ sơ giảng viên GV037-GV048. Hãy nhập users.csv/đồng bộ giảng viên trước.', 1;

    INSERT INTO LopHoc (TenLop,MaHocKy,MaHocPhan,MaGiangVien,PhongHoc,LichHoc,SucChua,SoLuongDaDangKy,TrangThai)
    SELECT s.TenLop,hk.MaHocKy,h.MaHocPhan,gv.MaGiangVien,s.PhongHoc,s.LichHoc,s.SucChua,0,s.TrangThai
    FROM @LopMoi s
    JOIN HocPhan h ON h.MaHocPhanCode=s.MaMon
    JOIN HocKy hk ON hk.TenHocKy=s.TenHocKy AND hk.NamHoc=s.NamHoc
    JOIN TaiKhoan tk ON tk.TenDangNhap=s.TenGiangVien AND tk.VaiTro=N'TEACHER'
    JOIN GiangVien gv ON gv.MaTaiKhoan=tk.MaTaiKhoan
    WHERE NOT EXISTS (
      SELECT 1 FROM LopHoc old WHERE old.TenLop=s.TenLop AND old.MaHocKy=hk.MaHocKy
    );

    DECLARE @Them INT=@@ROWCOUNT;
    COMMIT TRANSACTION;
    PRINT CONCAT(N'Đã thêm ', @Them, N' lớp học phần (tối đa 12).');
    SELECT h.MaHocPhanCode, h.TenHocPhan,
           COUNT(l.MaLop) AS SoLop, SUM(CASE WHEN l.TrangThai=N'OPEN' THEN 1 ELSE 0 END) AS SoLopDangMo
    FROM HocPhan h
    LEFT JOIN LopHoc l ON l.MaHocPhan=h.MaHocPhan
    LEFT JOIN HocKy hk ON hk.MaHocKy=l.MaHocKy
    WHERE h.MaHocPhanCode IN (N'INTER402',N'THESIS402',N'SWE421',N'SWE422',N'SWE423')
      AND (hk.MaHocKy IS NULL OR (hk.TenHocKy=N'Học kỳ 2' AND hk.NamHoc=N'2026-2027'))
    GROUP BY h.MaHocPhanCode,h.TenHocPhan ORDER BY h.MaHocPhanCode;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
END
ELSE
BEGIN
    PRINT N'V20.51: Bo qua 12 lop SWE, chua du hoc phan / hoc ky / giang vien. Nhap CSV sau do chay lai file SQL nay.';
END;
GO

-- Kiem tra tong hop sau khi chay (chi doc, khong thay doi du lieu).
SELECT N'DanhSachCho.LyDoKhongDuDieuKien' AS HangMuc,
       CASE WHEN COL_LENGTH(N'dbo.DanhSachCho',N'LyDoKhongDuDieuKien') IS NOT NULL THEN N'OK' ELSE N'THIEU' END AS TrangThai
UNION ALL SELECT N'DanhSachCho.ThoiDiemCapNhat', CASE WHEN COL_LENGTH(N'dbo.DanhSachCho',N'ThoiDiemCapNhat') IS NOT NULL THEN N'OK' ELSE N'THIEU' END
UNION ALL SELECT N'TienDoHocTap', CASE WHEN OBJECT_ID(N'dbo.TienDoHocTap',N'U') IS NOT NULL THEN N'OK' ELSE N'THIEU' END
UNION ALL SELECT N'QuyenHocLai', CASE WHEN OBJECT_ID(N'dbo.QuyenHocLai',N'U') IS NOT NULL THEN N'OK' ELSE N'THIEU' END
UNION ALL SELECT N'LuotXetRoundRobin', CASE WHEN OBJECT_ID(N'dbo.LuotXetRoundRobin',N'U') IS NOT NULL THEN N'OK' ELSE N'THIEU' END
UNION ALL SELECT N'ChiTietXetRoundRobin', CASE WHEN OBJECT_ID(N'dbo.ChiTietXetRoundRobin',N'U') IS NOT NULL THEN N'OK' ELSE N'THIEU' END;
GO
