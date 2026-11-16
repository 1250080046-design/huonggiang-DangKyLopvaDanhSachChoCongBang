package vn.edu.registration.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Optional, one-time academic catalog import. Never deletes or modifies existing academic records.
 * Run after CsvAccountImporter, so the 100 imported teacher profiles are available.
 * Enabled explicitly with ACADEMIC_IMPORT_ENABLED=true.
 */
@Component
@Order(30)
public class AcademicDataImporter implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AcademicDataImporter.class);
    // CSV major is blank for common subjects; SQL NULL means available to every major.
    private static final Set<String> SUPPORTED_MAJORS = Set.of(
            "Công nghệ thông tin", "Hệ thống thông tin", "Khoa học máy tính",
            "Kỹ thuật phần mềm", "An toàn thông tin", "Kỹ thuật dữ liệu");
    private final CourseRepository courses;
    private final TermRepository terms;
    private final ClassSectionRepository sections;
    private final TeacherRepository teachers;

    @Value("${app.academic-data.enabled:false}") private boolean enabled;
    @Value("${app.academic-data.path:../import-data}") private String dataDirectory;
    @Value("${app.academic-data.archive-v8:false}") private boolean archiveV8;

    public AcademicDataImporter(CourseRepository courses, TermRepository terms,
                                ClassSectionRepository sections, TeacherRepository teachers) {
        this.courses = courses;
        this.terms = terms;
        this.sections = sections;
        this.teachers = teachers;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!enabled) {
            if (courses.count() == 0 || terms.count() == 0 || sections.count() == 0) {
                log.warn("Chưa có đủ dữ liệu đào tạo trong SQL Server: HocPhan={}, HocKy={}, LopHoc={}. " +
                        "ACADEMIC_IMPORT_ENABLED=false, nên backend KHÔNG tự nhập hoc_phan.csv/hoc_ky.csv/lop_hoc.csv. " +
                        "Chạy NHAP_DU_LIEU_DAO_TAO_V19_9.ps1 nếu muốn nhập dữ liệu CSV đã kiểm tra.",
                        courses.count(), terms.count(), sections.count());
            }
            return;
        }
        Path directory = Path.of(dataDirectory).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            Path fallback = Path.of("import-data").toAbsolutePath().normalize();
            if (Files.isDirectory(fallback)) directory = fallback;
        }
        // Read and validate all data before writing anything to the database.
        List<String[]> courseRows = read(directory.resolve("hoc_phan.csv"), "code,name,credits,major", 4);
        List<String[]> termRows = read(directory.resolve("hoc_ky.csv"), "name,academicYear,startDate,endDate", 4);
        List<String[]> programRows = read(directory.resolve("chuong_trinh_hoc.csv"), "courseCode,year,semester", 3);
        Map<String,Integer> programSemester = new HashMap<>();
        Map<String,Integer> programYear = new HashMap<>();
        for (String[] r : programRows) {
            int year=Integer.parseInt(r[1]), semester=Integer.parseInt(r[2]);
            require(year >= 1 && year <= 4 && (semester==1 || semester==2), "Năm học/học kỳ môn không hợp lệ: " + r[0]);
            require(programSemester.putIfAbsent(r[0].toUpperCase(Locale.ROOT), semester)==null, "Trùng môn trong chương trình: "+r[0]);
            programYear.put(r[0].toUpperCase(Locale.ROOT), year);
        }
        List<String[]> sectionRows = read(directory.resolve("lop_hoc.csv"),
                "name,courseCode,termName,academicYear,teacherUsername,room,schedule,capacity,status", 9);

        Set<String> courseCodes = new HashSet<>();
        for (String[] row : courseRows) {
            require(!row[0].isBlank() && !row[1].isBlank(), "Mã/tên học phần không được trống");
            require(courseCodes.add(row[0].toUpperCase(Locale.ROOT)), "Trùng mã học phần trong CSV: " + row[0]);
            require(programSemester.containsKey(row[0].toUpperCase(Locale.ROOT)), "Chưa phân năm/học kỳ cho môn: " + row[0]);
            int credits = Integer.parseInt(row[2]);
            require(credits > 0 && credits <= 10, "Số tín chỉ không hợp lệ: " + row[0]);
            // Blank is intentionally valid: a common course must remain available to all students.
            require(row[3].isEmpty() || SUPPORTED_MAJORS.contains(row[3]),
                    "Ngành học không hợp lệ cho học phần " + row[0] + ": " + row[3]);
        }
        Set<String> termKeys = new HashSet<>();
        for (String[] row : termRows) {
            require(!row[0].isBlank() && !row[1].isBlank(), "Học kỳ/năm học không được trống");
            require(termKeys.add(termKey(row[0], row[1])), "Trùng học kỳ trong CSV");
            require(!LocalDate.parse(row[3]).isBefore(LocalDate.parse(row[2])), "Ngày học kỳ không hợp lệ");
        }
        Map<String, Teacher> teacherByCode = teachers.findAll().stream()
                .filter(t -> t.getAccount() != null && t.getAccount().getUsername() != null)
                .collect(Collectors.toMap(t -> t.getAccount().getUsername().toUpperCase(Locale.ROOT),
                        t -> t, (a,b) -> a));
        Set<String> sectionKeys = new HashSet<>();
        for (String[] row : sectionRows) {
            require("Học kỳ 2".equalsIgnoreCase(row[2]) && programSemester.getOrDefault(row[1].toUpperCase(Locale.ROOT), 0)==2,
                    "Chỉ tạo lớp học kỳ 2 đúng chương trình: " + row[0]);
            require(courseCodes.contains(row[1].toUpperCase(Locale.ROOT))
                    || courses.findByCodeIgnoreCase(row[1]).isPresent(), "Không tìm thấy học phần: " + row[1]);
            require(termKeys.contains(termKey(row[2],row[3]))
                    || terms.findAll().stream().anyMatch(t -> termKey(t.getName(),t.getAcademicYear()).equals(termKey(row[2],row[3]))),
                    "Không tìm thấy học kỳ: " + row[2] + " " + row[3]);
            require(teacherByCode.containsKey(row[4].toUpperCase(Locale.ROOT)),
                    "Chưa có hồ sơ giảng viên " + row[4] + ". Hãy đồng bộ users.csv trước.");
            require(!row[0].isBlank() && !row[5].isBlank() && !row[6].isBlank(), "Thiếu tên lớp/phòng/lịch");
            require(Integer.parseInt(row[7]) > 0, "Sức chứa lớp không hợp lệ: " + row[0]);
            ClassStatus.valueOf(row[8]);
            require(sectionKeys.add(row[0].toUpperCase(Locale.ROOT) + "|" + termKey(row[2],row[3])),
                    "Lớp trùng trong CSV: " + row[0]);
        }

        // Khi đã từng import V8: chỉ đóng các lớp demo IT101-IT510 có hậu tố -01/-02/-03,
        // không xóa dữ liệu, không chạm lớp do ADMIN đặt tên khác. Không đóng nếu không bật import V9.
        int archivedDemoSections = 0;
        if (archiveV8) {
            for (ClassSection section : sections.findAll()) {
                if (section.getName() != null && section.getName().matches("IT(?:10|20|30|40|50)[0-9]-0[1-3]")
                        && section.getCourse() != null
                        && section.getCourse().getCode() != null
                        && section.getName().startsWith(section.getCourse().getCode() + "-")) {
                    if (section.getStatus() == ClassStatus.OPEN) {
                        section.setStatus(ClassStatus.CLOSED);
                        archivedDemoSections++;
                    }
                }
            }
        }
        // V9 chưa được áp dụng trên máy người dùng, nhưng phòng trường hợp chạy nhầm V9:
        // đóng các lớp V9 cũ không thuộc danh mục hiện tại, không xóa dữ liệu/đăng ký.
        Set<String> incomingNames=sectionRows.stream().map(r -> r[0].toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        int archivedV9 = 0;
        if (archiveV8) for (ClassSection section : sections.findAll()) {
            String name=section.getName();
            if (name!=null && name.matches("[A-Z0-9]+-HK2-[0-9]{2}")
                    && !incomingNames.contains(name.toUpperCase(Locale.ROOT))
                    && section.getStatus()==ClassStatus.OPEN) {
                section.setStatus(ClassStatus.CLOSED);
                archivedV9++;
            }
        }
        int addedCourses = 0, addedTerms = 0, addedSections = 0;
        Map<String, Course> courseByCode = courses.findAll().stream().collect(Collectors.toMap(
                c -> c.getCode().toUpperCase(Locale.ROOT), c -> c, (a,b) -> a));
        for (String[] row : courseRows) {
            String code = row[0].toUpperCase(Locale.ROOT);
            if (courseByCode.containsKey(code)) {
                // Only complete missing curriculum metadata; preserve all ADMIN edits.
                Course existing = courseByCode.get(code);
                if (existing.getYear() == null && existing.getSemester() == null) {
                    existing.setYear(programYear.get(code));
                    existing.setSemester(programSemester.get(code));
                }
                // Only fill missing major metadata; never overwrite a major chosen by Admin.
                // SQL NULL continues to mean a shared course (no major restriction).
                if ((existing.getMajor() == null || existing.getMajor().isBlank()) && !row[3].isBlank()) {
                    existing.setMajor(row[3]);
                }
                continue;
            }
            Course course = new Course();
            course.setCode(code); course.setName(row[1]); course.setCredits(Integer.parseInt(row[2]));
            course.setYear(programYear.get(code)); course.setSemester(programSemester.get(code));
            course.setMajor(row[3].isBlank() ? null : row[3]);
            courseByCode.put(code, courses.save(course)); addedCourses++;
        }
        Map<String, Term> termByKey = terms.findAll().stream().collect(Collectors.toMap(
                t -> termKey(t.getName(), t.getAcademicYear()), t -> t, (a,b) -> a));
        for (String[] row : termRows) {
            String key = termKey(row[0],row[1]);
            if (termByKey.containsKey(key)) continue;
            Term term = new Term();
            term.setName(row[0]); term.setAcademicYear(row[1]);
            term.setStartDate(LocalDate.parse(row[2])); term.setEndDate(LocalDate.parse(row[3]));
            termByKey.put(key, terms.save(term)); addedTerms++;
        }
        Set<String> existingSections = sections.findAll().stream()
                .map(s -> sectionKey(s.getName(), s.getTerm().getName(), s.getTerm().getAcademicYear()))
                .collect(Collectors.toSet());
        for (String[] row : sectionRows) {
            String key = sectionKey(row[0],row[2],row[3]);
            if (!existingSections.add(key)) continue;
            ClassSection section = new ClassSection();
            section.setName(row[0]);
            section.setCourse(courseByCode.get(row[1].toUpperCase(Locale.ROOT)));
            section.setTerm(termByKey.get(termKey(row[2],row[3])));
            section.setTeacher(teacherByCode.get(row[4].toUpperCase(Locale.ROOT)));
            section.setRoom(row[5]); section.setSchedule(row[6]);
            section.setCapacity(Integer.parseInt(row[7])); section.setEnrolled(0);
            section.setStatus(ClassStatus.valueOf(row[8]));
            sections.save(section); addedSections++;
        }
        log.info("Dữ liệu đào tạo: thêm {} học phần, {} học kỳ, {} lớp. Tổng: {} học phần, {} học kỳ, {} lớp.",
                addedCourses, addedTerms, addedSections, courses.count(), terms.count(), sections.count());
        log.info("Đã đóng {} lớp demo V8 và {} lớp demo V9 không còn trong chương trình, không xóa dữ liệu.", archivedDemoSections, archivedV9);
    }

    private static String termKey(String name, String year) {
        return name.strip().toUpperCase(Locale.ROOT) + "|" + year.strip().toUpperCase(Locale.ROOT);
    }
    private static String sectionKey(String name, String term, String year) {
        return name.strip().toUpperCase(Locale.ROOT) + "|" + termKey(term,year);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    private static List<String[]> read(Path file, String header, int columns) throws Exception {
        if (!Files.isRegularFile(file))
            throw new IllegalStateException("Không tìm thấy " + file + ". Không nhập dữ liệu đào tạo.");
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.getFirst().replace("\uFEFF", "").equals(header))
            throw new IllegalArgumentException("Sai tiêu đề CSV: " + file.getFileName());
        List<String[]> rows = new ArrayList<>();
        for (int i=1; i<lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            String[] parts = lines.get(i).split(",", -1);
            if (parts.length != columns)
                throw new IllegalArgumentException("Sai số cột tại " + file.getFileName() + " dòng " + (i+1));
            for (int j=0;j<parts.length;j++) parts[j]=parts[j].strip();
            rows.add(parts);
        }
        return rows;
    }
}
