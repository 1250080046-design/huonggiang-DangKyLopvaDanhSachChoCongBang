package vn.edu.registration.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.registration.domain.Term;
import vn.edu.registration.repository.TermRepository;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;

/** Academic terms and registration windows are managed by Admin in SQL Server.
 * Legacy two-argument constructor supports existing isolated unit tests. */
@Component
public class RegistrationTermPolicy {
    private final TermRepository repository;
    private final String configuredTerm;
    private final String configuredYear;

    @Autowired
    public RegistrationTermPolicy(TermRepository repository,
            @Value("${app.registration.open-term:Học kỳ 2}") String openTerm,
            @Value("${app.registration.open-year:2026-2027}") String academicYear) {
        this.repository = repository;
        this.configuredTerm = openTerm.trim();
        this.configuredYear = academicYear.trim();
    }

    public RegistrationTermPolicy(String openTerm, String academicYear) {
        this(null, openTerm, academicYear);
    }

    private Term selectedTerm() {
        if (repository == null) return null;
        return repository.findByRegistrationOpenTrue().stream().findFirst().orElse(null);
    }

    public boolean canRegister(Term term) {
        if (term == null) return false;
        if (repository == null) return configuredTerm.equalsIgnoreCase(term.getName().trim())
                && configuredYear.equalsIgnoreCase(term.getAcademicYear().trim());
        Term open = selectedTerm();
        if (open == null || !open.getId().equals(term.getId())) return false;
        LocalDateTime now = LocalDateTime.now();
        return (open.getRegistrationStartsAt() == null || !now.isBefore(open.getRegistrationStartsAt()))
                && (open.getRegistrationEndsAt() == null || !now.isAfter(open.getRegistrationEndsAt()));
    }

    private String year() { Term term = selectedTerm(); return repository == null ? configuredYear : term == null ? "" : term.getAcademicYear(); }
    private String semesterName() { Term term = selectedTerm(); return repository == null ? configuredTerm : term == null ? "" : term.getName(); }

    public int openAcademicStartYear() {
        String year = year();
        if (!year.matches("\\d{4}-\\d{4}")) throw new IllegalStateException("Chưa cấu hình đợt đăng ký hợp lệ trong SQL Server.");
        int start = Integer.parseInt(year.substring(0, 4));
        if (Integer.parseInt(year.substring(5)) != start + 1) throw new IllegalStateException("Năm học không liên tiếp.");
        return start;
    }

    public int openSemester() {
        String value = Normalizer.normalize(semesterName().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").trim();
        if (value.matches("(?:hoc ky|hk|ky)\\s*1|1")) return 1;
        if (value.matches("(?:hoc ky|hk|ky)\\s*2|2")) return 2;
        if (repository != null && selectedTerm() == null) return 0;
        throw new IllegalStateException("Tên học kỳ phải là Học kỳ 1 hoặc Học kỳ 2.");
    }

    public String openPeriod() {
        Term term = selectedTerm();
        if (repository != null && term == null) return "Chưa có học kỳ mở đăng ký";
        return semesterName() + " " + year();
    }
}
