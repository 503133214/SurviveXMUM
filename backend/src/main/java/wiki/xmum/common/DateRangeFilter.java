package wiki.xmum.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Objects;
import java.util.regex.Pattern;

/** Shared validation for the administrator date-range filters. */
public final class DateRangeFilter {

    static final ZoneId SITE_ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private static final LocalDate MIN_DATABASE_DATE = LocalDate.of(1000, 1, 1);
    private static final Pattern DATE_SHAPE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    private DateRangeFilter() {}

    public static Range parse(String from, String to) {
        return parse(from, to, Clock.system(SITE_ZONE));
    }

    static Range parse(String from, String to, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        LocalDate start = parseDate(from, "开始日期");
        LocalDate end = parseDate(to, "结束日期");
        LocalDate today = LocalDate.now(clock);

        if ((start != null && start.isBefore(MIN_DATABASE_DATE))
                || (end != null && end.isBefore(MIN_DATABASE_DATE))) {
            throw new BizException("日期不能早于 1000-01-01");
        }
        if ((start != null && start.isAfter(today)) || (end != null && end.isAfter(today))) {
            throw new BizException("日期不能晚于今天");
        }
        if (start != null && end != null && start.isAfter(end)) {
            throw new BizException("开始日期不能晚于结束日期");
        }
        return new Range(start, end);
    }

    private static LocalDate parseDate(String raw, String label) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (!DATE_SHAPE.matcher(value).matches()) {
            throw new BizException(label + "必须是 YYYY-MM-DD 格式");
        }
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BizException(label + "必须是有效日期");
        }
    }

    public record Range(LocalDate from, LocalDate to) {}
}
