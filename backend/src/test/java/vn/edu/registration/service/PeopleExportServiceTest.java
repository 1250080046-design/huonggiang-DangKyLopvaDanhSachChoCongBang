package vn.edu.registration.service;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import vn.edu.registration.api.PeopleAdminController.PersonView;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PeopleExportServiceTest {
    private final PeopleExportService service = new PeopleExportService();

    private PersonView student(String code, String name) {
        return new PersonView(1L, code, name, "test@example.edu.vn", "Công nghệ thông tin",
                "Kỹ thuật phần mềm", "K22", LocalDate.of(2004,5,20), "ACTIVE");
    }

    @Test void excelContainsVietnameseHeadersRowsAndPreservesStudentIdAsText() throws Exception {
        var result = service.export(List.of(student("0224001001", "Nguyễn Văn A")), true, "xlsx");
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getHeaders().getFirst("Content-Disposition").contains("danh_sach_sinh_vien"));
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(result.getBody()))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("Mã sinh viên", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("0224001001", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Nguyễn Văn A", sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals("2004-05-20", sheet.getRow(1).getCell(3).getStringCellValue());
            assertEquals("Hoạt động", sheet.getRow(1).getCell(7).getStringCellValue());
        }
    }

    @Test void csvHasUtf8BomAndEscapesSpreadsheetFormulas() {
        var result = service.export(List.of(student("2240010001", "=1+1")), true, "csv");
        byte[] bytes = result.getBody();
        assertNotNull(bytes);
        assertEquals((byte) 0xef, bytes[0]);
        assertEquals((byte) 0xbb, bytes[1]);
        assertEquals((byte) 0xbf, bytes[2]);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(csv.contains("Mã sinh viên"));
        assertTrue(csv.contains("'=1+1"));
        assertTrue(csv.contains("Kỹ thuật phần mềm"));
    }

    @Test void teacherExportUsesSpecialtyHeaderNotMajor() throws Exception {
        var teacher = new PersonView(2L,"GV001","Trần Minh A","gv@example.edu.vn",
                "Công nghệ thông tin","An toàn thông tin",null,LocalDate.of(1985,9,3),"PENDING");
        var result = service.export(List.of(teacher), false, "xlsx");
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(result.getBody()))) {
            assertEquals("Chuyên môn", workbook.getSheetAt(0).getRow(0).getCell(5).getStringCellValue());
            assertEquals("An toàn thông tin", workbook.getSheetAt(0).getRow(1).getCell(5).getStringCellValue());
        }
    }

    @Test void rejectsUnexpectedFormat() {
        assertThrows(IllegalArgumentException.class, () -> service.export(List.of(),true,"pdf"));
    }
}
