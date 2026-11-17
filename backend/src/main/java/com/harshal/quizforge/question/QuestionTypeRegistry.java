package com.harshal.quizforge.question;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Looks up the {@link QuestionTypeHandler} for a type and handles the JSON (JSONB) conversions,
 * so the rest of the app never deals with per-type classes directly.
 */
@Slf4j
@Component
public class QuestionTypeRegistry {

    public static final int MIN_POINTS = 1;
    public static final int MAX_POINTS = 10;

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final Map<QuestionType, QuestionTypeHandler<?, ?, ?>> handlers = new EnumMap<>(QuestionType.class);
    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public QuestionTypeRegistry(List<QuestionTypeHandler<?, ?, ?>> all) {
        all.forEach(h -> handlers.put(h.type(), h));
        for (QuestionType type : QuestionType.values()) {
            if (!handlers.containsKey(type)) {
                throw new IllegalStateException("No QuestionTypeHandler registered for " + type);
            }
        }
    }

    public QuestionTypeHandler<?, ?, ?> handler(QuestionType type) {
        return handlers.get(type);
    }

    // ---------------------------------------------------------------- validation

    /** All problems with a draft (empty = valid). */
    public List<String> validate(QuestionDraft draft) {
        List<String> errors = new ArrayList<>();
        if (draft.type() == null) return List.of("type is required");
        if (draft.prompt() == null || draft.prompt().isBlank()) errors.add("prompt is required");
        if (draft.points() < MIN_POINTS || draft.points() > MAX_POINTS) {
            errors.add("points must be " + MIN_POINTS + "-" + MAX_POINTS);
        }
        QuestionTypeHandler<?, ?, ?> h = handler(draft.type());
        if (!h.contentClass().isInstance(draft.content()) || !h.keyClass().isInstance(draft.answerKey())) {
            errors.add("content and answer key don't match type " + draft.type());
            return errors;
        }
        errors.addAll(validateWith(h, draft.prompt(), draft.content(), draft.answerKey()));
        return errors;
    }

    private <C extends QuestionContent, K extends AnswerKey, R extends LearnerResponse>
    List<String> validateWith(QuestionTypeHandler<C, K, R> h, String prompt, QuestionContent content, AnswerKey key) {
        return h.validate(prompt, h.contentClass().cast(content), h.keyClass().cast(key));
    }

    // ---------------------------------------------------------------- grading

    /**
     * Fraction (0..1) earned for a response. Missing or malformed responses score 0 rather than
     * failing the whole submission.
     */
    public double grade(QuestionType type, String contentJson, String answerKeyJson, Map<String, Object> response) {
        if (response == null || response.isEmpty()) return 0.0;
        return gradeWith(handler(type), contentJson, answerKeyJson, response);
    }

    private <C extends QuestionContent, K extends AnswerKey, R extends LearnerResponse>
    double gradeWith(QuestionTypeHandler<C, K, R> h, String contentJson, String keyJson, Map<String, Object> response) {
        C content = json.readValue(contentJson, h.contentClass());
        K key = json.readValue(keyJson, h.keyClass());
        R parsed;
        try {
            parsed = json.convertValue(response, h.responseClass());
        } catch (RuntimeException e) {
            log.debug("Unparseable {} response {}: {}", h.type(), response, e.getMessage());
            return 0.0;
        }
        double fraction = h.grade(content, key, parsed);
        return Math.clamp(fraction, 0.0, 1.0);
    }

    // ---------------------------------------------------------------- JSON helpers

    public <T> T read(String jsonText, Class<T> type) {
        return json.readValue(jsonText, type);
    }

    public String toJson(Object value) {
        return json.writeValueAsString(value);
    }

    public Map<String, Object> toMap(String jsonText) {
        return json.readValue(jsonText, MAP);
    }

    public Map<String, Object> toMap(Object value) {
        return json.convertValue(value, MAP);
    }
}
