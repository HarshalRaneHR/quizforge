package com.harshal.quizforge.analytics;

import com.harshal.quizforge.quiz.QuizRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AnalyticsServiceTest {

    private final AnalyticsService service = new AnalyticsService(mock(AnalyticsRepository.class), mock(QuizRepository.class),
            null, Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC));

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    @Test
    void streakCountsConsecutiveDaysEndingToday() {
        assertThat(service.streak(Set.of(d("2026-11-17"), d("2026-11-16"), d("2026-11-15"), d("2026-11-13")))).isEqualTo(3);
    }

    @Test
    void streakStillCountsIfTodayHasNoAttemptYet() {
        assertThat(service.streak(Set.of(d("2026-11-16"), d("2026-11-15")))).isEqualTo(2);
    }

    @Test
    void streakIsZeroAfterAMissedDay() {
        assertThat(service.streak(Set.of(d("2026-11-14")))).isZero();
        assertThat(service.streak(Set.of())).isZero();
    }

    @Test
    void distributionUsesTenBucketsWithHundredInTheLast() {
        var buckets = AnalyticsService.distribution(List.of(0, 9, 10, 55, 99, 100, 100));
        assertThat(buckets).hasSize(10);
        assertThat(buckets.get(0).count()).isEqualTo(2);   // 0, 9
        assertThat(buckets.get(1).count()).isEqualTo(1);   // 10
        assertThat(buckets.get(5).count()).isEqualTo(1);   // 55
        assertThat(buckets.get(9).count()).isEqualTo(3);   // 99, 100, 100
        assertThat(buckets.get(9).label()).isEqualTo("90–100");
    }
}
