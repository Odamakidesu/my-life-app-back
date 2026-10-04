package com.mylifeapp.note.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RecurrenceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 4, 12, 0);

    @Test
    @DisplayName("締切がまだ先なら、1回分だけ進める")
    void advancesOnceWhenDeadlineIsAhead() {
        LocalDateTime deadline = NOW.plusHours(1);

        assertThat(Recurrence.DAILY.nextAfter(deadline, NOW)).isEqualTo(deadline.plusDays(1));
        assertThat(Recurrence.WEEKLY.nextAfter(deadline, NOW)).isEqualTo(deadline.plusWeeks(1));
        assertThat(Recurrence.MONTHLY.nextAfter(deadline, NOW)).isEqualTo(deadline.plusMonths(1));
    }

    @Test
    @DisplayName("締切を過ぎていれば、今より後になるまで進める")
    void skipsPastOccurrences() {
        LocalDateTime deadline = NOW.minusDays(3).withHour(9);

        assertThat(Recurrence.DAILY.nextAfter(deadline, NOW)).isEqualTo(LocalDateTime.of(2026, 10, 5, 9, 0));
    }

    @Test
    @DisplayName("毎月は月末を越えない（1/31 の次は 2/28）")
    void monthlyClampsToEndOfMonth() {
        assertThat(Recurrence.MONTHLY.advance(LocalDateTime.of(2027, 1, 31, 9, 0)))
                .isEqualTo(LocalDateTime.of(2027, 2, 28, 9, 0));
    }
}
