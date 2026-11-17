package com.harshal.quizforge.quiz;

import com.harshal.quizforge.common.AiGenerationException;
import com.harshal.quizforge.question.Difficulty;
import com.harshal.quizforge.question.QuestionDraft;
import com.harshal.quizforge.question.QuestionFactory;
import com.harshal.quizforge.question.QuestionFactory.Pair;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.question.QuestionTypeRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Generates questions of several types from study material with Spring AI.
 * One LLM call per requested type, each with a type-specific JSON shape (structured output) —
 * simpler shapes are far more reliable with small local models than one big polymorphic schema.
 * Every generated question is validated by its type handler; invalid ones are dropped.
 */
@Slf4j
@Component
public class AiQuizGenerator {

    public record GeneratedQuiz(String title, List<QuestionDraft> questions) {}

    // ---- Shapes the model fills in (boxed types: a missing value fails validation, not parsing)
    record Batch<T>(String title, List<T> questions) {}
    record AiSingleChoice(String prompt, List<String> options, Integer correctIndex, String explanation) {}
    record AiMultipleChoice(String prompt, List<String> options, List<Integer> correctIndexes, String explanation) {}
    record AiTrueFalse(String statement, Boolean answer, String explanation) {}
    record AiFillBlank(String sentence, List<String> answers, String explanation) {}
    record AiNumeric(String prompt, Double answer, Double tolerance, String unit, String explanation) {}
    record AiOrdering(String prompt, List<String> itemsInCorrectOrder, String explanation) {}
    record AiPair(String left, String right) {}
    record AiMatching(String prompt, List<AiPair> pairs, String explanation) {}

    private interface ToDraft<T> { QuestionDraft apply(T item, Difficulty difficulty); }

    private record TypeSpec<T>(String instructions, ParameterizedTypeReference<Batch<T>> shape, ToDraft<T> toDraft) {}

    private static final String SYSTEM_PROMPT = """
            You are an experienced teacher writing quiz questions.
            Use ONLY facts from the user's study material. Never invent facts.
            Every question needs a one-sentence explanation of the correct answer.
            Also give the quiz a short title (max 8 words).
            """;

    private final ChatClient chatClient;   // null when no AI provider starter is on the classpath
    private final QuestionTypeRegistry registry;
    private final Map<QuestionType, TypeSpec<?>> specs = new EnumMap<>(QuestionType.class);

    public AiQuizGenerator(ObjectProvider<ChatClient.Builder> builder, QuestionTypeRegistry registry) {
        ChatClient.Builder b = builder.getIfAvailable();
        this.chatClient = b != null ? b.build() : null;
        this.registry = registry;
        QuestionFactory f = new QuestionFactory(RandomGenerator.getDefault());

        specs.put(QuestionType.SINGLE_CHOICE, new TypeSpec<>("""
                Write multiple-choice questions with exactly 4 options and exactly ONE correct option.
                correctIndex is the 0-based index of the correct option.""",
                new ParameterizedTypeReference<Batch<AiSingleChoice>>() {},
                (q, d) -> f.singleChoice(d, q.prompt(), q.options(), q.correctIndex(), q.explanation())));

        specs.put(QuestionType.MULTIPLE_CHOICE, new TypeSpec<>("""
                Write "select all that apply" questions with 4 or 5 options of which 2 or 3 are correct.
                correctIndexes lists the 0-based indexes of ALL correct options.""",
                new ParameterizedTypeReference<Batch<AiMultipleChoice>>() {},
                (q, d) -> f.multipleChoice(d, q.prompt(), q.options(), q.correctIndexes(), q.explanation())));

        specs.put(QuestionType.TRUE_FALSE, new TypeSpec<>("""
                Write true/false items. Each statement is clearly either true or false according to the material.
                Mix true and false statements. answer is true or false.""",
                new ParameterizedTypeReference<Batch<AiTrueFalse>>() {},
                (q, d) -> f.trueFalse(d, q.statement(), q.answer(), q.explanation())));

        specs.put(QuestionType.FILL_BLANK, new TypeSpec<>("""
                Write fill-in-the-blank items: a sentence from the material where ONE key term is replaced by ___
                (exactly three underscores). answers lists the accepted answers: the exact term plus common variants.""",
                new ParameterizedTypeReference<Batch<AiFillBlank>>() {},
                (q, d) -> f.fillBlank(d, q.sentence(), List.of(q.answers()), q.explanation())));

        specs.put(QuestionType.NUMERIC, new TypeSpec<>("""
                Write questions whose answer is a single number stated in or computed from the material.
                answer is the number, tolerance is the accepted difference (0 for exact), unit may be empty.""",
                new ParameterizedTypeReference<Batch<AiNumeric>>() {},
                (q, d) -> f.numeric(d, q.prompt(), q.answer(), q.tolerance() == null ? 0 : q.tolerance(), q.unit(), q.explanation())));

        specs.put(QuestionType.ORDERING, new TypeSpec<>("""
                Write ordering questions: put 3 to 6 steps, events or stages into the correct sequence.
                itemsInCorrectOrder MUST be listed in the correct order.""",
                new ParameterizedTypeReference<Batch<AiOrdering>>() {},
                (q, d) -> f.ordering(d, q.prompt(), q.itemsInCorrectOrder(), q.explanation())));

        specs.put(QuestionType.MATCHING, new TypeSpec<>("""
                Write matching questions with 3 to 5 pairs, e.g. term -> definition or cause -> effect.
                Each pair has a left and a right value; all values must be distinct.""",
                new ParameterizedTypeReference<Batch<AiMatching>>() {},
                (q, d) -> f.matching(d, q.prompt(), q.pairs().stream().map(p -> new Pair(p.left(), p.right())).toList(), q.explanation())));

        for (QuestionType type : QuestionType.values()) {
            if (!specs.containsKey(type)) throw new IllegalStateException("No AI spec for " + type);
        }
    }

    public GeneratedQuiz generate(String sourceText, Map<QuestionType, Integer> counts, Difficulty difficulty) {
        return generate(sourceText, counts, difficulty, done -> {});
    }

    /** Number of LLM calls a blueprint needs (one per question type used) — the job's progress total. */
    public static int stepsFor(Map<QuestionType, Integer> counts) {
        return (int) counts.values().stream().filter(n -> n != null && n > 0).count();
    }

    /**
     * @param onStepDone called with the number of finished steps (question types) so far, for progress reporting
     */
    public GeneratedQuiz generate(String sourceText, Map<QuestionType, Integer> counts, Difficulty difficulty,
                                  java.util.function.IntConsumer onStepDone) {
        if (chatClient == null) {
            throw new AiGenerationException("AI provider is not configured");
        }
        Difficulty level = difficulty == null ? Difficulty.MEDIUM : difficulty;
        List<QuestionDraft> drafts = new ArrayList<>();
        String title = null;
        RuntimeException lastFailure = null;
        int done = 0;

        for (QuestionType type : QuestionType.values()) {   // stable order: grouped by type
            int count = counts.getOrDefault(type, 0);
            if (count <= 0) continue;
            try {
                var result = generateType(type, specs.get(type), count, sourceText, level);
                drafts.addAll(result.questions());
                if (title == null && result.title() != null && !result.title().isBlank()) title = result.title().trim();
            } catch (RuntimeException e) {
                // Can't reach the model at all: every other type would fail the same way, so stop now.
                if (isUnreachable(e)) {
                    throw new AiGenerationException("AI service is unreachable. Is the model server (Ollama) running?", e);
                }
                lastFailure = e;
                log.warn("AI generation failed for {}: {}", type, e.getMessage());
            }
            onStepDone.accept(++done);
        }

        if (drafts.isEmpty()) {
            String reason = lastFailure != null ? "AI service failed: " + lastFailure.getMessage() : "AI returned no valid questions";
            throw new AiGenerationException(reason, lastFailure);
        }
        return new GeneratedQuiz(title == null ? "Untitled quiz" : title, drafts);
    }

    private <T> GeneratedQuiz generateType(QuestionType type, TypeSpec<T> spec, int count, String source, Difficulty level) {
        Batch<T> batch = chatClient.prompt()
                .system(SYSTEM_PROMPT + spec.instructions() + "\nWrite exactly " + count + " questions. " + difficultyHint(level))
                // Passed as a parameter so braces in the study text aren't treated as template syntax.
                .user(u -> u.text("Study material:\n{material}").param("material", source))
                .call()
                .entity(spec.shape());

        List<QuestionDraft> valid = new ArrayList<>();
        if (batch != null && batch.questions() != null) {
            for (T item : batch.questions()) {
                if (item == null || valid.size() == count) continue;
                // Never trust LLM output blindly — validate before it touches the database.
                try {
                    QuestionDraft draft = spec.toDraft().apply(item, level);
                    List<String> errors = registry.validate(draft);
                    if (errors.isEmpty()) valid.add(draft);
                    else log.info("Dropped invalid {} question: {}", type, errors);
                } catch (RuntimeException e) {
                    log.info("Dropped malformed {} question: {}", type, e.getMessage());
                }
            }
        }
        log.info("Generated {}/{} valid {} questions", valid.size(), count, type);
        return new GeneratedQuiz(batch == null ? null : batch.title(), valid);
    }

    /** Connection-level failure (refused, timeout, DNS) anywhere in the cause chain. */
    static boolean isUnreachable(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.io.IOException) return true;
        }
        return false;
    }

    private static String difficultyHint(Difficulty level) {
        return switch (level) {
            case EASY -> "Difficulty: easy — test recall of key facts and definitions.";
            case MEDIUM -> "Difficulty: medium — test understanding, not just recall.";
            case HARD -> "Difficulty: hard — test application and reasoning, with plausible distractors.";
        };
    }
}
