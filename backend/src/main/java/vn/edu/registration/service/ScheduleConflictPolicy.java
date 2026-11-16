package vn.edu.registration.service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Supports "Thứ 2 07:30-09:30" and multiple sessions separated by semicolons. */
public final class ScheduleConflictPolicy {
    private static final Pattern SLOT = Pattern.compile("(?iu)^(?:thứ\\s*([2-7])|chủ\\s*nhật)\\s+(\\d{1,2}:\\d{2})\\s*[-–]\\s*(\\d{1,2}:\\d{2})$");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");
    private ScheduleConflictPolicy() {}
    public record Slot(int day, LocalTime start, LocalTime end) {}
    public static List<Slot> parse(String schedule) {
        if (schedule == null || schedule.isBlank()) return List.of();
        List<Slot> result = new ArrayList<>();
        for (String raw : schedule.split(";")) {
            Matcher match = SLOT.matcher(raw.trim());
            if (!match.matches()) throw new IllegalArgumentException("Lịch học không đúng định dạng 'Thứ 2 07:30-09:30': " + raw.trim());
            int day = match.group(1) == null ? 8 : Integer.parseInt(match.group(1));
            LocalTime start;
            LocalTime end;
            try {
                // Accept both 7:00 and 07:00; Java ISO_LOCAL_TIME rejects single-digit hours.
                start = LocalTime.parse(match.group(2), TIME);
                end = LocalTime.parse(match.group(3), TIME);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Giờ học không hợp lệ, vui lòng nhập theo mẫu Thứ 3 07:00-09:30.");
            }
            if (!start.isBefore(end)) throw new IllegalArgumentException("Giờ kết thúc phải sau giờ bắt đầu.");
            result.add(new Slot(day, start, end));
        }
        return List.copyOf(result);
    }
    public static boolean conflicts(String first, String second) {
        for (Slot a : parse(first)) for (Slot b : parse(second)) {
            if (a.day() == b.day() && a.start().isBefore(b.end()) && b.start().isBefore(a.end())) return true;
        }
        return false;
    }
}
