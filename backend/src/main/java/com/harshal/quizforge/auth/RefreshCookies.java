package com.harshal.quizforge.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the refresh-token cookie:
 * HttpOnly (JavaScript can't read it), SameSite=Strict (not sent on cross-site requests → no CSRF),
 * Secure (HTTPS only), and scoped to /api/auth so it isn't sent with every API call.
 */
@Component
public class RefreshCookies {

    public static final String NAME = "qf_refresh";
    private static final String PATH = "/api/auth";

    private final boolean secure;

    public RefreshCookies(@Value("${app.jwt.cookie-secure}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie create(String rawToken, Duration maxAge) {
        return base(rawToken).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}
