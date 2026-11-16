package vn.edu.registration.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import vn.edu.registration.api.PeopleAdminController.PersonView;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Xuất đúng danh sách đã lọc từ SQL Server, không giới hạn ở trang đang xem. */
@Service
public class PeopleExportService {
    private static final int MAX_EXPORT_ROWS = 20000;
    private static final String[] STUDENT_HEADERS = {
            "Mã sinh viên", "Email", "Họ và tên", "Ngày sinh", "Khoa", "Ngành học", "Khóa", "Trạng thái"
    };
    private static final String[] TEACHER_HEADERS = {
            "Mã giảng viên", "Email", "Họ và tên", "Ngày sinh", "Khoa", "Chuyên môn", "Trạng thái"
    };

    public ResponseEntity<byte[]> export(List<PersonView> people, boolean student, String requestedFormat) {
        String format = requestedFormat == null ? "xlsx" : requestedFormat.trim().toLowerCase(Locale.ROOT);
        if (!format.equals("csv") && !format.equals("xlsx"))
            throw new IllegalArgumentException("Chỉ hỗ trợ xuất Excel (.xlsx) hoặc CSV (.csv).");
        if (people.size() > MAX_EXPORT_ROWS)
            throw new IllegalArgumentException("Danh sách có hơn 20.000 dòng. Vui lòng lọc bớt trước khi xuất.");

        String[] headers = student ? STUDENT_HEADERS : TEACHER_HEADERS;
        String filename = (student ? "danh_sach_sinh_vien" : "danh_sach_giang_vien") + "_" + LocalDate.now()
                + "." + format;
        byte[] data = format.equals("csv") ? makeCsv(people, student, headers) : makeXlsx(people, student, headers);
        MediaType type = format.equals("csv")
                ? MediaType.parseMediaType("text/csv;charset=UTF-8")
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(data);
    }

    private String[] values(PersonView p, boolean student) {
        String date = p.dateOfBirth() == null ? "" : p.dateOfBirth().toString();
        String status = switch (p.status()) {
            case "ACTIVE" -> "Hoạt động";
            case "LOCKED" -> "Đã khóa";
            case "PENDING" -> "Chờ kích hoạt";
            default -> "Không xác định";
        };
        if (student) return new String[] {safe(p.username()), safe(p.email()), safe(p.fullName()), date,
                safe(p.faculty()), safe(p.major()), safe(p.cohort()), status};
        return new String[] {safe(p.username()), safe(p.email()), safe(p.fullName()), date,
                safe(p.faculty()), safe(p.major()), status};
    }

    private String safe(String text) { return text == null ? "" : text; }

    /** Avoid spreadsheets interpreting CSV values as formulas (also applies after whitespace). */
    private String safeCsv(String text) {
        if (text == null || text.isEmpty()) return "";
        String leading = text.stripLeading();
        if (!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0) return "'" + text;
        if (text.charAt(0) == '\t' || text.charAt(0) == '\r' || text.charAt(0) == '\n') return "'" + text;
        return text;
    }

    private byte[] makeCsv(List<PersonView> people, boolean student, String[] headers) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            out.write(0xEF); out.write(0xBB); out.write(0xBF); // Excel Windows UTF-8 BOM
            try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                 CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
                printer.printRecord((Object[]) headers);
                for (var person : people) {
                    String[] data = values(person, student);
                    for (int i = 0; i < data.length; i++) data[i] = safeCsv(data[i]);
                    printer.printRecord((Object[]) data);
                }
            }
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Không tạo được tệp CSV.", exception);
        }
    }

    private byte[] makeXlsx(List<PersonView> people, boolean student, String[] headers) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(student ? "Danh sách sinh viên" : "Danh sách giảng viên");
            sheet.createFreezePane(0, 1);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true); headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            textStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            CellStyle alternatingStyle = workbook.createCellStyle();
            alternatingStyle.cloneStyleFrom(textStyle);
            alternatingStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            alternatingStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row top = sheet.createRow(0); top.setHeightInPoints(28);
            for (int col = 0; col < headers.length; col++) {
                Cell cell = top.createCell(col, CellType.STRING);
                cell.setCellValue(headers[col]); cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(col, (col == 2 ? 28 : col == 1 ? 36 : col == 4 || col == 5 ? 26 : 19) * 256);
            }
            for (int index = 0; index < people.size(); index++) {
                Row row = sheet.createRow(index + 1); row.setHeightInPoints(21);
                String[] cells = values(people.get(index), student);
                for (int col = 0; col < cells.length; col++) {
                    Cell cell = row.createCell(col, CellType.STRING);
                    cell.setCellValue(cells[col]);
                    cell.setCellStyle(index % 2 == 0 ? textStyle : alternatingStyle);
                }
            }
            if (!people.isEmpty())
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, people.size(), 0, headers.length - 1));
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Không tạo được tệp Excel.", exception);
        }
    }
}
