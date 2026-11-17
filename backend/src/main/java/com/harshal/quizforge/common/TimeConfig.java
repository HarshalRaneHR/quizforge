package com.harshal.quizforge.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class TimeConfig {

    /** Injected instead of calling Instant.now() directly, so time-based logic can be unit tested. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
