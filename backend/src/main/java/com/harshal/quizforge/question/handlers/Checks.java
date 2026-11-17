package com.harshal.quizforge.question.handlers;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Small validation helpers shared by the handlers. */
final class Checks {
    private Checks() {}

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** min..max non-blank, case-insensitively distinct strings. */
    static void textList(List<String> items, int min, int max, String what, List<String> errors) {
        if (items == null || items.size() < min || items.size() > max) {
            errors.add(what + " must have " + min + "-" + max + " entries");
            return;
        }
        if (items.stream().anyMatch(Checks::blank)) {
            errors.add(what + " must not be blank");
        }
        long distinct = items.stream().filter(Objects::nonNull)
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).distinct().count();
        if (distinct != items.size()) {
            errors.add(what + " must be distinct");
        }
    }

    static boolean inRange(Integer index, int size) {
        return index != null && index >= 0 && index < size;
    }

    /** True if {@code list} contains each of 0..n-1 exactly once. */
    static boolean isPermutation(List<Integer> list, int n) {
        if (list == null || list.size() != n) return false;
        var seen = new HashSet<Integer>();
        for (Integer i : list) {
            if (!inRange(i, n) || !seen.add(i)) return false;
        }
        return true;
    }
}
