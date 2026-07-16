package wiki.xmum.common;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DateRangeFilterTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-16T16:30:00Z"), DateRangeFilter.SITE_ZONE);

    @Test
    void acceptsEmptyPastAndCurrentSiteDates() {
        DateRangeFilter.Range empty = DateRangeFilter.parse(null, " ", CLOCK);
        assertNull(empty.from());
        assertNull(empty.to());

        DateRangeFilter.Range range = DateRangeFilter.parse("2026-02-28", "2026-07-17", CLOCK);
        assertEquals("2026-02-28", range.from().toString());
        assertEquals("2026-07-17", range.to().toString());
    }

    @Test
    void rejectsFutureDatesUsingTheSiteTimezone() {
        BizException start = assertThrows(BizException.class,
                () -> DateRangeFilter.parse("2026-07-18", null, CLOCK));
        BizException end = assertThrows(BizException.class,
                () -> DateRangeFilter.parse(null, "9999-12-31", CLOCK));

        assertEquals(400, start.getCode());
        assertEquals("日期不能晚于今天", start.getMessage());
        assertEquals("日期不能晚于今天", end.getMessage());
    }

    @Test
    void rejectsInvalidDatesAndReversedRanges() {
        assertEquals("开始日期必须是 YYYY-MM-DD 格式", assertThrows(BizException.class,
                () -> DateRangeFilter.parse("2026-7-01", null, CLOCK)).getMessage());
        assertEquals("结束日期必须是有效日期", assertThrows(BizException.class,
                () -> DateRangeFilter.parse(null, "2026-02-30", CLOCK)).getMessage());
        assertEquals("开始日期不能晚于结束日期", assertThrows(BizException.class,
                () -> DateRangeFilter.parse("2026-07-17", "2026-07-16", CLOCK)).getMessage());
        assertEquals("日期不能早于 1000-01-01", assertThrows(BizException.class,
                () -> DateRangeFilter.parse("0000-01-01", null, CLOCK)).getMessage());
        assertEquals("日期不能早于 1000-01-01", assertThrows(BizException.class,
                () -> DateRangeFilter.parse(null, "0999-12-31", CLOCK)).getMessage());
    }

    @Test
    void acceptsAValidLeapDay() {
        DateRangeFilter.Range range = DateRangeFilter.parse("2024-02-29", "2024-02-29", CLOCK);
        assertEquals("2024-02-29", range.from().toString());
    }
}
