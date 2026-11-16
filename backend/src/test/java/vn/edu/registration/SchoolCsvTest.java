package vn.edu.registration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;

class SchoolCsvTest {
    @Test void csvContainsRequiredRowsAndUniqueIdentifiers() throws Exception {
        Path path=Path.of("../import-data/users.csv");
        if(!Files.exists(path)) return; // optional file for clean CI checkouts (gitignored)
        var lines=Files.readAllLines(path);
        assertEquals(3101,lines.size());
        assertEquals("username,password,role,fullName,dateOfBirth,email,faculty,major,cohort",lines.get(0));
        Set<String> usernames=new HashSet<>();
        int student=0,teacher=0;
        Map<String,Integer> perCohort=new HashMap<>();
        String previousSort="";
        Set<String> emails=new HashSet<>();
        for(String line:lines.subList(1,lines.size())) {
            var col=line.split(",",-1);
            assertEquals(9,col.length);
            assertTrue(usernames.add(col[0]));
            assertTrue(emails.add(col[5].toLowerCase(java.util.Locale.ROOT)));
            if("STUDENT".equals(col[2])) {
                student++;
                assertTrue(col[0].matches("[0-9]{10}"));
                assertEquals(col[0]+"@sv.hcm.edu.vn",col[5]);
                assertTrue(col[8].matches("K2[2-6]"));
                int birth=2000+Integer.parseInt(col[8].substring(1))-18;
                assertEquals(birth,java.time.LocalDate.parse(col[4]).getYear());
                String group=col[8];
                perCohort.merge(group,1,Integer::sum);
                assertEquals(perCohort.get(group).intValue(), Integer.parseInt(col[0].substring(6)));
                assertEquals(group.substring(1)+String.valueOf(birth%10)+"001",col[0].substring(0,6));
                assertTrue(previousSort.compareTo(group)<=0);
                previousSort=group;
            }
            if("TEACHER".equals(col[2]))teacher++;
            assertTrue(col[1].length()>=12);
        }
        assertEquals(3000,student);assertEquals(100,teacher);
        assertEquals(5,perCohort.size());
        assertEquals(3000,perCohort.values().stream().mapToInt(Integer::intValue).sum());
    }
}
