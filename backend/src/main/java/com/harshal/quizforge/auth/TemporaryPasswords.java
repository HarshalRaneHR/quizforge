package com.harshal.quizforge.auth;

import java.security.SecureRandom;

/**
 * One-time passwords an admin hands to a person, e.g. {@code k7Qm-3xPa-9vTz}.
 * 12 random characters (about 70 bits) from an alphabet without look-alikes (0/O, 1/l/I),
 * in groups of four so they're easy to read out or type.
 */
final class TemporaryPasswords {
    private TemporaryPasswords() {}

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    static String generate() {
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
