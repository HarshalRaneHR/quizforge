package com.harshal.quizforge.common;

import lombok.Getter;

/** Rate limit exceeded; {@code retryAfterSeconds} becomes the Retry-After header. */
@Getter
public class TooManyRequestsException extends RuntimeException {
    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
