package vn.edu.registration.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.registration.domain.Role;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Nhập file CSV/Excel thủ công do Admin chọn; giới hạn hàng và kích thước để tránh upload quá tải. */
@Service
public class PeopleImportService {
    private static final int MAX_ROWS = 5000;
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_ERRORS = 50;
    private final PeopleImportRowService rowService;

    public PeopleImportService(PeopleImportRowService rowService) { this.rowService = rowService; }

    public record RowError(int row, String reason) {}
    public record ImportSummary(int total, int created, int skipped, int ignoredRole, int failed, List<RowError> errors,
                                String message) {}
    private record RawRow(int number, Map<String, String> values) {}

    public ImportSummary importFile(MultipartFile file, Role role) {
        if (role != Role.STUDENT && role != Role.TEACHER)
            throw new IllegalArgumentException("Chỉ được nhập Sinh viên hoặc Giảng viên.");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Vui lòng chọn file có dữ liệu.");
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("Dung lượng tệp tối đa là 5 MB.");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        boolean csv = name.endsWith(".csv");
        if (!csv && !name.endsWith(".xlsx") && !name.endsWith(".xls"))
            throw new IllegalArgumentException("Chỉ hỗ trợ file CSV, XLSX hoặc XLS.");

        List<RawRow> rows;
        try (InputStream stream = file.getInputStream()) {
            rows = csv ? readCsv(stream) : readExcel(stream);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            // Không phản hồi nội dung dữ liệu/file của người dùng hoặc stack trace ra client.
            throw new IllegalArgumentException("Không đọc được tệp. Kiểm tra định dạng và phần tiêu đề.");
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("Tệp không có dòng dữ liệu nào.");

        int created = 0, skipped = 0, ignoredRole = 0, failed = 0;
        List<RowError> errors = new ArrayList<>();
        Set<String> emails = new HashSet<>(), usernames = new HashSet<>();
        for (RawRow row : rows) {
            try {
                Map<String, String> d = row.values();
                String declaredRole = d.getOrDefault("role", "").trim().toUpperCase(Locale.ROOT);
                if (!declaredRole.isEmpty()) {
                    String normalizedRole = declaredRole.replace("SINH VIÊN", "STUDENT")
                            .replace("GIẢNG VIÊN", "TEACHER");
                    if (!normalizedRole.equals("STUDENT") && !normalizedRole.equals("TEACHER"))
                        throw new IllegalArgumentException("Tệp chứa vai trò không hợp lệ hoặc không được phép nhập.");
                    if (!normalizedRole.equals(role.name())) {
                        ignoredRole++;
                        continue; // users.csv hỗn hợp: mỗi trang xử lý một loại tài khoản.
                    }
                }
                String email = d.getOrDefault("email", "").trim().toLowerCase(Locale.ROOT);
                String username = d.getOrDefault("username", "").trim().toLowerCase(Locale.ROOT);
                if (!email.isBlank() && !emails.add(email))
                    throw new IllegalArgumentException("Email bị trùng trong tệp.");
                if (!username.isBlank() && !usernames.add(username))
                    throw new IllegalArgumentException("Mã người dùng bị trùng trong tệp.");
                var input = new PeopleImportRowService.ImportPerson(
                        d.get("username"), d.get("email"), d.get("fullName"), d.get("faculty"),
                        d.get("major"), d.get("cohort"), d.get("dateOfBirth"), d.get("role"));
                if (rowService.importRow(input, role)) created++; else skipped++;
            } catch (Exception ex) {
                failed++;
                if (errors.size() < MAX_ERRORS) {
                    String reason = ex instanceof IllegalArgumentException && ex.getMessage() != null
                            ? ex.getMessage() : "Dữ liệu không hợp lệ hoặc vi phạm ràng buộc.";
                    errors.add(new RowError(row.number(), reason));
                }
            }
        }
        return new ImportSummary(created + skipped + ignoredRole + failed, created, skipped, ignoredRole, failed, errors,
                "Đã xử lý tệp. Tài khoản mới đang chờ kích hoạt; tài khoản cũ không bị ghi đè."
        );
    }

    private List<RawRow> readCsv(InputStream input) throws IOException {
        String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        if (content.startsWith("\uFEFF")) content = content.substring(1);
        String firstLine = content.lines().findFirst().orElse("");
        // Excel trong một số cấu hình vùng dùng dấu chấm phẩy khi xuất CSV.
        char delimiter = firstLine.contains(";") && !firstLine.contains(",") ? ';' : ',';
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).get()
                .parse(new StringReader(content))) {
            Iterator<CSVRecord> iterator = parser.iterator();
            if (!iterator.hasNext()) throw new IllegalArgumentException("Thiếu hàng tiêu đề.");
            List<String> headings = new ArrayList<>();
            iterator.next().forEach(headings::add);
            var map = headers(headings);
            List<RawRow> rows = new ArrayList<>();
            while (iterator.hasNext()) {
                CSVRecord row = iterator.next();
                if (rows.size() >= MAX_ROWS) throw new IllegalArgumentException("Tệp vượt quá 5.000 dòng dữ liệu.");
                if (row.size() > headings.size())
                    throw new IllegalArgumentException("Dòng CSV có số cột vượt quá tiêu đề (kiểm tra dấu phẩy/ngoặc kép).");
                Map<String, String> values = new LinkedHashMap<>();
                for (var entry : map.entrySet()) {
                    int index = entry.getValue();
                    values.put(entry.getKey(), index < row.size() ? row.get(index).trim() : "");
                }
                if (values.values().stream().allMatch(String::isBlank)) continue;
                rows.add(new RawRow((int) row.getRecordNumber(), values));
            }
            return rows;
        }
    }

    private List<RawRow> readExcel(InputStream input) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("File Excel không có sheet.");
            Sheet sheet = workbook.getSheetAt(0);
            Row heading = sheet.getRow(sheet.getFirstRowNum());
            if (heading == null) throw new IllegalArgumentException("Thiếu hàng tiêu đề.");
            if (heading.getLastCellNum() < 0 || heading.getLastCellNum() > 40)
                throw new IllegalArgumentException("Hàng tiêu đề Excel không hợp lệ.");
            DataFormatter formatter = new DataFormatter(Locale.US);
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < heading.getLastCellNum(); i++)
                headers.add(cellText(heading.getCell(i), formatter));
            Map<String, Integer> map = headers(headers);
            List<RawRow> rows = new ArrayList<>();
            int last = sheet.getLastRowNum();
            if (last - heading.getRowNum() > MAX_ROWS)
                throw new IllegalArgumentException("Tệp vượt quá 5.000 dòng dữ liệu.");
            for (int index = heading.getRowNum() + 1; index <= last; index++) {
                Row row = sheet.getRow(index);
                if (row == null) continue;
                Map<String, String> values = new LinkedHashMap<>();
                for (var entry : map.entrySet())
                    values.put(entry.getKey(), cellText(row.getCell(entry.getValue()), formatter));
                if (values.values().stream().allMatch(String::isBlank)) continue;
                rows.add(new RawRow(index + 1, values));
            }
            return rows;
        }
    }

    private String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        if (cell.getCellType() == CellType.FORMULA)
            throw new IllegalArgumentException("File Excel không được chứa công thức trong các cột nhập.");
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        String value = formatter.formatCellValue(cell).trim();
        if (value.length() > 1000) throw new IllegalArgumentException("Ô Excel có nội dung quá dài.");
        return value;
    }

    private Map<String, Integer> headers(List<String> names) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (names.size() > 40) throw new IllegalArgumentException("Tệp có quá nhiều cột.");
        for (int i = 0; i < names.size(); i++) {
            String canonical = canonical(names.get(i));
            if (canonical == null) continue; // password cũ và các cột ngoài mẫu không được xử lý
            if (result.putIfAbsent(canonical, i) != null)
                throw new IllegalArgumentException("Tiêu đề có cột bị trùng: " + canonical + ".");
        }
        if (!result.containsKey("email") || !result.containsKey("fullName"))
            throw new IllegalArgumentException("Tệp cần có cột email và fullName (Họ tên).");
        return result;
    }

    private String canonical(String name) {
        if (name == null) return null;
        // Phân biệt Khoa (faculty) và Khóa (cohort) trước khi loại bỏ dấu tiếng Việt.
        if (name.trim().equalsIgnoreCase("Khóa") || name.trim().equalsIgnoreCase("Khoá")) return "cohort";
        String key = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return switch (key) {
            case "email", "emailtruong" -> "email";
            case "fullname", "hoten", "hovaten", "name" -> "fullName";
            case "username", "tendangnhap", "mssv", "masinhvien", "magv", "magiangvien" -> "username";
            case "faculty", "khoa" -> "faculty";
            case "major", "nganh", "nganhhoc", "chuyenmon", "bomon", "specialty" -> "major";
            case "cohort", "khoahoc" -> "cohort";
            case "dateofbirth", "dob", "ngaysinh" -> "dateOfBirth";
            case "role", "vaitro" -> "role";
            default -> null;
        };
    }
}
