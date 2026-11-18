package com.harshal.quizforge.auth;

import com.harshal.quizforge.common.TooManyRequestsException;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Slows down password guessing: after N failed logins for one email, further attempts are refused
 * until the window refills (Bucket4j). Only failures count, and a successful login clears the record.
 * Unknown emails are limited the same way, so the limit doesn't reveal which accounts exist.
 * In-memory, like the AI rate limit: fine for a single instance.
 */
@Component
public class LoginAttemptLimiter {

    /** Upper bound on remembered emails; buckets that have fully refilled are dropped first. */
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Bucket> failures = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration window;

    public LoginAttemptLimiter(@Value("${app.auth.max-failed-logins:5}") int maxFailures,
                               @Value("${app.auth.failed-login-window-minutes:15}") long windowMinutes) {
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    /** Throws 429 if this email has used up its failed attempts. */
    public void checkAllowed(String email) {
        Bucket bucket = failures.get(email);
        if (bucket == null || bucket.getAvailableTokens() > 0) return;
        long nanos = bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill();
        long seconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(nanos));
        throw new TooManyRequestsException(
                "Too many failed login attempts. Try again in " + (seconds < 90 ? seconds + " seconds" : (seconds + 59) / 60 + " minutes") + ".",
                seconds);
    }

    public void recordFailure(String email) {
        if (failures.size() >= MAX_TRACKED) prune();
        failures.computeIfAbsent(email, k -> Bucket.builder()
                .addLimit(limit -> limit.capacity(maxFailures).refillGreedy(maxFailures, window))
                .build()).tryConsume(1);
    }

    public void recordSuccess(String email) {
        failures.remove(email);
    }

    private void prune() {
        failures.entrySet().removeIf(e -> e.getValue().getAvailableTokens() >= maxFailures);
        if (failures.size() >= MAX_TRACKED) failures.clear();   // still full: under attack, start over
    }
}
