package com.harshal.quizforge.quiz;

import com.harshal.quizforge.common.TooManyRequestsException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Token bucket per user for AI generation (Bucket4j): each user can start N generations per hour,
 * refilled gradually. Protects the model (and an API bill, with a hosted provider) from one user.
 * In-memory: fine for a single instance; several instances would share buckets via Redis (bucket4j-redis).
 */
@Component
public class GenerationRateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int perHour;

    public GenerationRateLimiter(@Value("${app.ai.generations-per-hour:10}") int perHour) {
        this.perHour = perHour;
    }

    /** Takes one token or throws 429 with how long to wait. */
    public void acquire(String userEmail) {
        Bucket bucket = buckets.computeIfAbsent(userEmail, k -> Bucket.builder()
                .addLimit(limit -> limit.capacity(perHour).refillGreedy(perHour, Duration.ofHours(1)))
                .build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long seconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
            throw new TooManyRequestsException(
                    "You've reached the limit of " + perHour + " AI generations per hour. Try again in " + human(seconds) + ".", seconds);
        }
    }

    private static String human(long seconds) {
        return seconds < 90 ? seconds + " seconds" : (seconds + 59) / 60 + " minutes";
    }
}
