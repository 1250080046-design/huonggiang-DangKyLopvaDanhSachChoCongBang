package vn.edu.registration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.edu.registration.service.CsvImportRowService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component @Order(20)
public class CsvAccountImporter implements CommandLineRunner {
    private static final Logger log=LoggerFactory.getLogger(CsvAccountImporter.class);
    private final CsvImportRowService importer;
    @Value("${app.csv.path:../import-data/users.csv}") private String configuredPath;
    @Value("${app.csv.enabled:true}") private boolean enabled;
    public CsvAccountImporter(CsvImportRowService importer) { this.importer=importer; }
    @Override public void run(String... args) throws Exception {
        if(!enabled) return;
        Path file=Path.of(configuredPath).toAbsolutePath().normalize();
        if(!Files.isRegularFile(file)) {
            Path fallback=Path.of("import-data/users.csv").toAbsolutePath().normalize();
            if(!Files.isRegularFile(fallback)) { log.info("Không tìm thấy file CSV để đồng bộ. Backend vẫn chạy."); return; }
            file=fallback;
        }
        int created=0, existing=0, invalid=0, rowNumber=0;
        Set<String> seen=new HashSet<>();
        Map<String, Integer> failureReasons = new LinkedHashMap<>();
        try(var reader=Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while((line=reader.readLine())!=null) {
                rowNumber++;
                if(rowNumber==1 && line.startsWith("\uFEFF")) line=line.substring(1);
                if(rowNumber==1 && line.equals("username,password,role,fullName,dateOfBirth,email,faculty,major,cohort")) continue;
                if(line.isBlank()) continue;
                try {
                    String[] columns=parseCsvLine(line);
                    if(columns.length!=9) throw new IllegalArgumentException("Số cột khác 9");
                    if(!seen.add(columns[0].trim().toLowerCase())) throw new IllegalArgumentException("Mã người dùng trùng trong CSV");
                    if(importer.importRow(columns)) created++; else existing++;
                } catch(Exception e) {
                    invalid++;
                    String reason = safeMessage(e);
                    failureReasons.merge(reason, 1, Integer::sum);
                    // Avoid thousands of repeated warnings without hiding the precise validation reason.
                    if (invalid <= 10) {
                        log.warn("CSV dòng {}: {}: {}", rowNumber, e.getClass().getSimpleName(), reason);
                    } else if (invalid == 11) {
                        log.warn("CSV có nhiều lỗi: chỉ hiện 10 dòng đầu. Xem tổng hợp nguyên nhân ở cuối.");
                    }
                }
            }
        }
        log.info("Đồng bộ CSV: tạo mới {}, đã tồn tại {}, lỗi {}",created,existing,invalid);
        failureReasons.forEach((reason, count) ->
                log.warn("Tổng hợp lỗi nhập CSV: {} dòng - {}", count, reason));
    }
    private static String safeMessage(Exception e) {
        // Whitelist constant, non-sensitive messages only. Never log raw CSV values, passwords,
        // database SQL, arbitrary exception messages or stack traces from private user data.
        if (e instanceof IllegalArgumentException) {
            String msg = e.getMessage();
            if (msg == null) return "Bản ghi không hợp lệ / không thể lưu";
            return switch (msg) {
                case "Số cột khác 9", "Mã người dùng trùng trong CSV", "Dấu ngoặc kép chưa đóng",
                     "Thiếu hoặc sai định dạng dữ liệu", "Role không khớp mã người dùng",
                     "Mã sinh viên, khóa, năm sinh hoặc email không khớp",
                     "Vai trò tài khoản đã tồn tại không khớp",
                     "Email đã tồn tại" -> msg;
                default -> "Bản ghi không hợp lệ / không thể lưu";
            };
        }
        return "Bản ghi không hợp lệ / không thể lưu";
    }
    private static String[] parseCsvLine(String line) {
        java.util.List<String> out=new java.util.ArrayList<>();
        StringBuilder field=new StringBuilder(); boolean quoted=false;
        for(int i=0;i<line.length();i++) {
            char ch=line.charAt(i);
            if(ch=='"') { if(quoted && i+1<line.length() && line.charAt(i+1)=='"') {field.append('"');i++;} else quoted=!quoted; }
            else if(ch==',' && !quoted) {out.add(field.toString()); field.setLength(0);}
            else field.append(ch);
        }
        if(quoted) throw new IllegalArgumentException("Dấu ngoặc kép chưa đóng");
        out.add(field.toString()); return out.toArray(String[]::new);
    }
}
