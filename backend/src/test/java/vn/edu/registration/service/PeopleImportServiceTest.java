package vn.edu.registration.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.registration.domain.Role;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PeopleImportServiceTest {
    private final PeopleImportRowService rows = mock(PeopleImportRowService.class);
    private final PeopleImportService service = new PeopleImportService(rows);

    @Test void importsCsvAndDoesNotPassPlaintextPassword() {
        when(rows.importRow(any(), eq(Role.STUDENT))).thenReturn(true);
        String data = "username,password,role,fullName,dateOfBirth,email,faculty,major,cohort\n"
                + "2240010001,SecretFromOldFile,STUDENT,Nguyễn Văn A,2004-01-02,sv1@example.edu,Đào tạo,Công nghệ thông tin,K22\n";
        var file = new MockMultipartFile("file", "users.csv", "text/csv", data.getBytes(StandardCharsets.UTF_8));
        var result = service.importFile(file, Role.STUDENT);
        assertEquals(1, result.created());
        assertEquals(0, result.failed());
        var captor = org.mockito.ArgumentCaptor.forClass(PeopleImportRowService.ImportPerson.class);
        verify(rows).importRow(captor.capture(), eq(Role.STUDENT));
        assertEquals("2240010001", captor.getValue().username());
        assertEquals("sv1@example.edu", captor.getValue().email());
        assertFalse(captor.getValue().toString().contains("SecretFromOldFile"));
    }

    @Test void importsExcelFile() throws Exception {
        when(rows.importRow(any(), eq(Role.TEACHER))).thenReturn(true);
        byte[] excel;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Giảng viên");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("email");
            header.createCell(1).setCellValue("Họ tên");
            header.createCell(2).setCellValue("Chuyên môn");
            Row record = sheet.createRow(1);
            record.createCell(0).setCellValue("gv1@example.edu");
            record.createCell(1).setCellValue("Lê Thu Hà");
            record.createCell(2).setCellValue("An toàn thông tin");
            workbook.write(out);
            excel = out.toByteArray();
        }
        var file = new MockMultipartFile("file", "teacher.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);
        var result = service.importFile(file, Role.TEACHER);
        assertEquals(1, result.created());
        assertEquals(0, result.failed());
        verify(rows).importRow(any(), eq(Role.TEACHER));
    }

    @Test void refusesDuplicateEmailsInSameFile() {
        when(rows.importRow(any(), eq(Role.STUDENT))).thenReturn(true);
        String data = "email,fullName\nsv1@example.edu,Nguyễn Văn A\nsv1@example.edu,Nguyễn Văn B\n";
        var file = new MockMultipartFile("file", "students.csv", "text/csv", data.getBytes(StandardCharsets.UTF_8));
        var result = service.importFile(file, Role.STUDENT);
        assertEquals(1, result.created());
        assertEquals(1, result.failed());
        assertEquals(3, result.errors().get(0).row());
        verify(rows, times(1)).importRow(any(), eq(Role.STUDENT));
    }

    @Test void skipsOtherRoleInSharedUsersCsv() {
        when(rows.importRow(any(), eq(Role.TEACHER))).thenReturn(true);
        String csv = "role,email,fullName\nSTUDENT,sv@example.edu,Học viên\nTEACHER,gv@example.edu,Giảng viên\n";
        var file = new MockMultipartFile("file", "users.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        var result = service.importFile(file, Role.TEACHER);
        assertEquals(2, result.total());
        assertEquals(1, result.created());
        assertEquals(1, result.ignoredRole());
        assertEquals(0, result.failed());
        verify(rows, times(1)).importRow(any(), eq(Role.TEACHER));
    }

    @Test void supportsSemicolonCsv() {
        when(rows.importRow(any(), eq(Role.STUDENT))).thenReturn(true);
        String csv = "email;fullName;major\nsv1@example.edu;Nguyễn Văn A;Kỹ thuật phần mềm\n";
        var file = new MockMultipartFile("file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, service.importFile(file, Role.STUDENT).created());
    }

    @Test void distinguishesVietnameseKhoaAndKhoaHocHeaders() {
        when(rows.importRow(any(), eq(Role.STUDENT))).thenReturn(true);
        String data = "MSSV,Email,Họ và tên,Khoa,Khóa,Ngành học\n"
                + "2240010012,sv2@example.edu,Nguyễn Văn B,Công nghệ thông tin,K22,Khoa học máy tính\n";
        var file = new MockMultipartFile("file", "students.csv", "text/csv", data.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, service.importFile(file, Role.STUDENT).created());
        var captor = org.mockito.ArgumentCaptor.forClass(PeopleImportRowService.ImportPerson.class);
        verify(rows).importRow(captor.capture(), eq(Role.STUDENT));
        assertEquals("Công nghệ thông tin", captor.getValue().faculty());
        assertEquals("K22", captor.getValue().cohort());
    }

    @Test void readsVietnameseExcelWithSeparateExampleSheetWithoutImportingExample() throws Exception {
        when(rows.importRow(any(), eq(Role.STUDENT))).thenReturn(true);
        byte[] excel;
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var input = book.createSheet("Dữ liệu nhập");
            var header = input.createRow(0);
            for (int i = 0; i < 7; i++) header.createCell(i).setCellValue(
                    new String[] {"Mã sinh viên","Email","Họ và tên","Ngày sinh","Khoa","Ngành học","Khóa"}[i]);
            var person = input.createRow(1);
            for (int i = 0; i < 7; i++) person.createCell(i).setCellValue(
                    new String[] {"2240010005","sv@example.edu.vn","Lê Thị B","2004-01-02","Công nghệ thông tin","Khoa học máy tính","K22"}[i]);
            var guide = book.createSheet("Hướng dẫn và ví dụ");
            guide.createRow(0).createCell(0).setCellValue("Ví dụ không được nhập");
            guide.createRow(1).createCell(0).setCellValue("SV_MAU");
            book.write(out); excel = out.toByteArray();
        }
        var file = new MockMultipartFile("file", "sinh_vien_mau.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);
        var result = service.importFile(file, Role.STUDENT);
        assertEquals(1, result.created());
        assertEquals(0, result.failed());
        verify(rows, times(1)).importRow(any(), eq(Role.STUDENT));
        var captor = org.mockito.ArgumentCaptor.forClass(PeopleImportRowService.ImportPerson.class);
        verify(rows).importRow(captor.capture(), eq(Role.STUDENT));
        assertEquals("K22", captor.getValue().cohort());
    }

    @Test void acceptsVietnameseTeacherCsv() {
        when(rows.importRow(any(), eq(Role.TEACHER))).thenReturn(true);
        String csv = "Mã giảng viên,Email,Họ và tên,Ngày sinh,Khoa,Chuyên môn\n"
                + "GV001,gv@example.edu.vn,Nguyễn Thị Mẫu,1985-08-16,Công nghệ thông tin,Kỹ thuật phần mềm\n";
        var file = new MockMultipartFile("file", "giang_vien_mau.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, service.importFile(file, Role.TEACHER).created());
        var captor = org.mockito.ArgumentCaptor.forClass(PeopleImportRowService.ImportPerson.class);
        verify(rows).importRow(captor.capture(), eq(Role.TEACHER));
        assertEquals("Kỹ thuật phần mềm", captor.getValue().major());
    }

    @Test void rejectsMissingRequiredHeaders() {
        var file = new MockMultipartFile("file", "students.csv", "text/csv", "name,major\nA,B\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> service.importFile(file, Role.STUDENT));
        verifyNoInteractions(rows);
    }
}
