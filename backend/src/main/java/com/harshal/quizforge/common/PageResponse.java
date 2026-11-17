package com.harshal.quizforge.common;

import org.springframework.data.domain.Page;

import java.util.List;

/** Spring's Page JSON is unstable across versions; return our own shape. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
}
