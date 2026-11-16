package vn.edu.registration.api;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.registration.repository.ClassSectionRepository;
import vn.edu.registration.service.RegistrationService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Admin-only export: obtain all ACTIVE registrations for the requested class from SQL Server. */
@RestController
@RequestMapping("/api/admin/classes")
public class ClassStudentExportController {
    private final ClassSectionRepository classes;
    private final RegistrationService registrations;

    public ClassStudentExportController(ClassSectionRepository classes, RegistrationService registrations) {
        this.classes = classes;
        this.registrations = registrations;
    }

    @GetMapping("/{id}/students/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id,
                                        @RequestParam(defaultValue = "xlsx") String format) {
        if (!"xlsx".equalsIgnoreCase(format))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ hỗ trợ Excel (.xlsx) cho danh sách lớp.");
        var section = classes.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp học phần."));
        var students = registrations.studentsInClass(id);
        if (students.size() > 20000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách vượt quá 20.000 sinh viên.");

        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Danh sách sinh viên");
            String[] headers = {"STT", "Mã sinh viên", "Họ và tên", "Email", "Khoa", "Ngày đăng ký"};
            CellStyle headerStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row heading = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = heading.createCell(i); cell.setCellValue(headers[i]); cell.setCellStyle(headerStyle);
            }
            DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));
            for (int i = 0; i < students.size(); i++) {
                ApiDtos.StudentInClass student = students.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(studentCode(student.email(), student.studentId()));
                row.createCell(2).setCellValue(safe(student.fullName()));
                row.createCell(3).setCellValue(safe(student.email()));
                row.createCell(4).setCellValue(safe(student.faculty()));
                row.createCell(5).setCellValue(student.registeredAt() == null ? "" : dateFormat.format(student.registeredAt()));
            }
            int[] widths = {8, 20, 35, 42, 32, 24};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(1, students.size()), 0, headers.length - 1));
            workbook.write(out);
            bytes = out.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không tạo được file Excel.", e);
        }
        String fileName = "danh_sach_sinh_vien_lop_" + id + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(bytes);
    }

    private static String studentCode(String email, Long internalId) {
        String prefix = email == null ? "" : email.split("@", 2)[0].trim();
        return prefix.matches("[0-9]{6,20}") ? prefix : (internalId == null ? "" : internalId.toString());
    }
    private static String safe(String input) { return input == null ? "" : input; }
}
