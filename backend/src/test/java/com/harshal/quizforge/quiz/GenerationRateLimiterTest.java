package com.harshal.quizforge.quiz;

import com.harshal.quizforge.common.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationRateLimiterTest {

    @Test
    void allowsTheHourlyQuotaThenRejectsWithRetryAfter() {
        GenerationRateLimiter limiter = new GenerationRateLimiter(3);
        for (int i = 0; i < 3; i++) assertThatCode(() -> limiter.acquire("a@test.com")).doesNotThrowAnyException();

        assertThatThrownBy(() -> limiter.acquire("a@test.com"))
                .isInstanceOfSatisfying(TooManyRequestsException.class, e -> assertThat(e.getRetryAfterSeconds()).isPositive());
    }

    @Test
    void bucketsArePerUser() {
        GenerationRateLimiter limiter = new GenerationRateLimiter(1);
        limiter.acquire("a@test.com");
        assertThatCode(() -> limiter.acquire("b@test.com")).doesNotThrowAnyException();
    }
}
