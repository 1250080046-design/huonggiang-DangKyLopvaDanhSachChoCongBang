package vn.edu.registration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Validates the academic sample bundle without requiring SQL Server. */
class AcademicDataCsvTest {
    private static final Set<String> MAJORS = Set.of(
            "Công nghệ thông tin", "Hệ thống thông tin", "Khoa học máy tính",
            "Kỹ thuật phần mềm", "An toàn thông tin", "Kỹ thuật dữ liệu");

    @Test
    void academicCsvHasValidReferencesAndNonConflictingRoomSlots() throws Exception {
        Path dir = Path.of("../import-data");
        if (!Files.isDirectory(dir)) dir = Path.of("import-data");
        var courseLines = Files.readAllLines(dir.resolve("hoc_phan.csv"), StandardCharsets.UTF_8);
        var programLines = Files.readAllLines(dir.resolve("chuong_trinh_hoc.csv"), StandardCharsets.UTF_8);
        var termLines = Files.readAllLines(dir.resolve("hoc_ky.csv"), StandardCharsets.UTF_8);
        var classLines = Files.readAllLines(dir.resolve("lop_hoc.csv"), StandardCharsets.UTF_8);
        assertEquals("code,name,credits,major", courseLines.getFirst());
        assertEquals("courseCode,year,semester", programLines.getFirst());
        assertEquals(140, courseLines.size()); // 55 original courses + 84 new specialist courses + header
        assertEquals(140, programLines.size());
        assertEquals(3, termLines.size());
        assertEquals(149, classLines.size()); // 136 existing sections + 12 added in V20.51 + CSV header
        for (String code : List.of("SWE421", "SWE422", "SWE423")) {
            long matching = classLines.stream().skip(1)
                    .filter(line -> line.split(",", -1)[1].equals(code))
                    .count();
            assertEquals(4L, matching, "Expected 4 newly opened sections for " + code);
        }

        Set<String> courseCodes = new HashSet<>();
        Map<String, String> majorByCourse = new HashMap<>();
        for (int i=1; i<courseLines.size(); i++) {
            var data = courseLines.get(i).split(",", -1);
            assertEquals(4, data.length);
            assertTrue(courseCodes.add(data[0]), "Duplicate course code " + data[0]);
            assertTrue(Integer.parseInt(data[2]) > 0 && Integer.parseInt(data[2]) <= 10);
            assertTrue(data[3].isEmpty() || MAJORS.contains(data[3]), "Invalid major in " + data[0]);
            majorByCourse.put(data[0], data[3]);
        }
        Set<String> mappedCodes = new HashSet<>();
        Map<String, int[]> specialistByMajor = new HashMap<>();
        for (int i=1; i<programLines.size(); i++) {
            var r = programLines.get(i).split(",",-1);
            assertEquals(3,r.length);
            assertTrue(courseCodes.contains(r[0]));
            assertTrue(mappedCodes.add(r[0]));
            int year = Integer.parseInt(r[1]), semester = Integer.parseInt(r[2]);
            assertTrue(year >= 1 && year <= 4);
            assertTrue(semester == 1 || semester == 2);
            String major = majorByCourse.get(r[0]);
            if (!major.isEmpty() && year >= 3) {
                var totals = specialistByMajor.computeIfAbsent(major, ignored -> new int[4]);
                totals[year == 3 ? semester - 1 : semester + 1]++;
            }
        }
        assertEquals(courseCodes, mappedCodes);
        for (String major : MAJORS) {
            int[] counts = specialistByMajor.get(major);
            assertNotNull(counts, "No specialist subjects for " + major);
            assertTrue(counts[0] >= 4 && counts[1] >= 4 && counts[2] >= 3 && counts[3] >= 3,
                    "Year 3/4 must have sufficient specialist subjects for " + major);
        }
        assertTrue(majorByCourse.get("CO3010").isEmpty(), "Common software-engineering foundation should remain shared");

        Set<String> rooms = new HashSet<>();
        Set<String> teacherSlots = new HashSet<>();
        Set<String> classNames = new HashSet<>();
        Set<String> teachers = new HashSet<>();
        for (int i=1;i<classLines.size();i++) {
            var r=classLines.get(i).split(",",-1);
            assertEquals(9,r.length);
            assertTrue(courseCodes.contains(r[1]));
            assertTrue(r[4].matches("GV\\d{3}"));
            assertTrue(Integer.parseInt(r[7])>0);
            assertTrue(classNames.add(r[0]));
            assertTrue(rooms.add(r[2]+"/"+r[3]+"/"+r[5]+"/"+r[6]));
            assertTrue(teacherSlots.add(r[2]+"/"+r[3]+"/"+r[4]+"/"+r[6]));
            teachers.add(r[4]);
        }
        assertEquals(100, teachers.size());
    }
}
