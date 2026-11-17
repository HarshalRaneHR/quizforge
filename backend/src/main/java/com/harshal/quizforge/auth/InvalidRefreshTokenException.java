package com.harshal.quizforge.auth;

/** The refresh token is unknown, expired or revoked — the user must log in again. */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) { super(message); }
}
