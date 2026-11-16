# Hệ thống đăng ký lớp và danh sách chờ công bằng

Đây là đồ án xây dựng website hỗ trợ sinh viên đăng ký lớp học phần, theo dõi lịch học và tham gia danh sách chờ khi lớp hết chỗ. Điểm chính của hệ thống là cơ chế **Round Robin**: khi có chỗ trống, hệ thống lần lượt xét người đang chờ theo vòng, kiểm tra điều kiện và cấp chỗ trực tiếp cho sinh viên hợp lệ.

Hệ thống có ba vai trò:

- **Sinh viên:** xem chương trình đào tạo, tìm và đăng ký lớp, hủy đăng ký, xem lịch học, theo dõi danh sách chờ và gửi yêu cầu tham gia lại khi đủ điều kiện.
- **Giảng viên:** xem lớp được phân công, lịch dạy và danh sách sinh viên.
- **Quản trị viên:** quản lý sinh viên, giảng viên, học phần, lớp học, học kỳ, chương trình đào tạo; theo dõi đăng ký, danh sách chờ, lịch sử xét Round Robin và nhập/xuất Excel/CSV.

## Công nghệ sử dụng

| Thành phần | Công nghệ |
| --- | --- |
| Frontend | Next.js 15, React 19, TypeScript |
| Backend | Java 25, Spring Boot 3.5, Maven |
| Cơ sở dữ liệu | Microsoft SQL Server |
| Bảo mật | Spring Security, JWT trong HttpOnly Cookie, CSRF |
| Truy cập dữ liệu | Spring Data JPA / Hibernate |

## Chuẩn bị môi trường

Trên Windows, cần cài **JDK 25**, **Maven 3.9+**, **Node.js 20.9+**, **SQL Server** và **SQL Server Management Studio (SSMS)**. VS Code hoặc IntelliJ IDEA đều có thể sử dụng để mở project.

Kiểm tra nhanh các công cụ đã cài:

```powershell
java -version
mvn -version
node -v
npm -v
```

## Cài đặt và chạy chương trình

### 1. Chuẩn bị cơ sở dữ liệu

Mở SQL Server và SSMS, kết nối đến máy chủ của bạn. Mở file:

```text
db/HeThongDangKyLop.sql
```

Chạy script bằng **Execute** để tạo hoặc bổ sung cấu trúc cơ sở dữ liệu `HeThongDangKyLop`. Nếu đang sử dụng database có dữ liệu, hãy **sao lưu trước khi chạy**. Không cần chạy các migration SQL riêng vì chúng đã được gộp vào file chính.

Thư mục `import-data/` chứa dữ liệu mẫu về học phần, học kỳ, lớp học và người dùng. Một số dữ liệu được backend đọc theo cấu hình; những dữ liệu còn lại có thể được nhập qua các chức năng quản trị. Nếu chưa có đủ học phần, học kỳ hoặc giảng viên, script có thể bỏ qua phần bổ sung lớp mẫu và cho phép chạy lại sau.

### 2. Khởi động backend

Mở terminal tại thư mục `backend`. Cấu hình kết nối SQL Server thông qua biến môi trường; thay các giá trị bên dưới theo máy của bạn:

```powershell
$env:DB_URL = "jdbc:sqlserver://localhost:1433;databaseName=HeThongDangKyLop;encrypt=true;trustServerCertificate=true"
$env:DB_USERNAME = "sa"
$env:DB_PASSWORD = "MAT_KHAU_SQL_SERVER_CUA_BAN"
$env:JWT_SECRET = "CHUOI_BI_MAT_DU_DAI_VA_NGAU_NHIEN"

mvn spring-boot:run
```

Để chạy kiểm thử trước khi khởi động:

```powershell
mvn clean test
```

Backend mặc định hoạt động tại **http://localhost:8081**. Có thể kiểm tra trạng thái tại:

```text
http://localhost:8081/actuator/health
```

Khi chạy thành công, API sẽ trả trạng thái `UP`. Giữ terminal backend đang chạy.

> Các giá trị mật khẩu và JWT secret chỉ là chỗ điền thông tin. Không lưu mật khẩu thật hoặc khóa bí mật lên GitHub. Với môi trường triển khai thực tế, cần sử dụng secret đủ mạnh và cấu hình bảo mật phù hợp.

### 3. Khởi động frontend

Mở terminal thứ hai tại thư mục `frontend`:

```powershell
npm install
npm run dev
```

Truy cập website tại **http://localhost:3000**.

Frontend mặc định chuyển các yêu cầu `/backend/...` đến backend tại `http://127.0.0.1:8081`. Nếu backend chạy ở địa chỉ khác, đặt biến `API_PROXY_TARGET` trước khi khởi động Next.js:

```powershell
$env:API_PROXY_TARGET = "http://127.0.0.1:8081"
npm run dev
```

### 4. Sử dụng hệ thống

Đăng nhập bằng tài khoản đã có trong cơ sở dữ liệu hoặc được quản trị viên tạo/kích hoạt. Sau khi đăng nhập, mỗi vai trò sẽ được chuyển đến khu vực chức năng tương ứng.

Để kiểm tra nghiệp vụ chính, có thể mở một lớp còn chỗ để đăng ký; sau đó thử lớp đã đầy, tham gia danh sách chờ và hủy một lượt đăng ký để quan sát quá trình xét chỗ theo Round Robin. Quản trị viên có thể xem lịch sử quyết định và chi tiết từng lượt xét.

## Cấu trúc thư mục

```text
HeThongDangKyLop/
├── backend/            # API Spring Boot, xử lý nghiệp vụ và kiểm thử
├── frontend/           # Giao diện Next.js
├── db/
│   └── HeThongDangKyLop.sql
├── import-data/        # Dữ liệu học phần, học kỳ, lớp và người dùng
└── README.md
```

## Một số lỗi thường gặp

- **Backend không kết nối SQL Server:** kiểm tra dịch vụ SQL Server, TCP/IP, cổng 1433, tên database và thông tin đăng nhập.
- **Frontend báo API 500:** kiểm tra terminal backend và bảo đảm đã chạy file SQL chính tương ứng với phiên bản mã nguồn.
- **Không đăng nhập được hoặc báo CSRF 403:** sử dụng thống nhất `http://localhost:3000`, kiểm tra backend ở cổng 8081 và thử đăng nhập lại.
- **Không chạy được `mvn` hoặc `npm`:** kiểm tra JDK/Maven/Node.js đã cài và biến môi trường `PATH`.

## Ghi chú

Project phục vụ mục đích học tập và trình bày đồ án **Phân tích và Thiết kế Phần mềm Hướng Đối Tượng**. Phần kiểm thử tự động có trong backend, nhưng khi triển khai trên môi trường mới vẫn cần kiểm tra kết nối SQL Server, các nghiệp vụ đăng ký đồng thời và dữ liệu thực tế trước khi sử dụng.
