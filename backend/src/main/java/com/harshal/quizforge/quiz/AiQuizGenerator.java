package com.harshal.quizforge.quiz;

import com.harshal.quizforge.common.AiGenerationException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/** Spring AI's .entity(Class) asks the model for JSON matching the record and parses it (structured output). */
@Component
public class AiQuizGenerator {

    public record GeneratedQuestion(String prompt, List<String> options, int correctIndex, String explanation) {}
    public record GeneratedQuiz(String title, List<GeneratedQuestion> questions) {}

    private static final String SYSTEM_PROMPT = """
            You are an experienced teacher writing a multiple-choice quiz.
            Use ONLY facts from the user's study material.
            Every question must have exactly 4 options and exactly one correct answer.
            correctIndex is the 0-based index of the correct option.
            Give each question a one-sentence explanation of the correct answer.
            Also give the quiz a short title (max 8 words).
            """;

    private final ChatClient chatClient;   // null when no AI provider starter is on the classpath

    public AiQuizGenerator(ObjectProvider<ChatClient.Builder> builder) {
        ChatClient.Builder b = builder.getIfAvailable();
        this.chatClient = b != null ? b.build() : null;
    }

    public GeneratedQuiz generate(String sourceText, int questionCount) {
        if (chatClient == null) {
            throw new AiGenerationException("AI provider is not configured");
        }
        GeneratedQuiz quiz;
        try {
            quiz = chatClient.prompt()
                    .system(SYSTEM_PROMPT + "Write exactly " + questionCount + " questions.")
                    // Passed as a parameter so braces in the study text aren't treated as template syntax.
                    .user(u -> u.text("Study material:\n{material}").param("material", sourceText))
                    .call()
                    .entity(GeneratedQuiz.class);
        } catch (RuntimeException e) {
            throw new AiGenerationException("AI service failed: " + e.getMessage(), e);
        }
        validate(quiz);
        // Models sometimes return extra questions; keep what was asked for.
        int keep = Math.min(questionCount, quiz.questions().size());
        return new GeneratedQuiz(quiz.title(), List.copyOf(quiz.questions().subList(0, keep)));
    }

    // Never trust LLM output blindly — validate before it touches the database.
    private static void validate(GeneratedQuiz quiz) {
        if (quiz == null || quiz.questions() == null || quiz.questions().isEmpty()) {
            throw new AiGenerationException("AI returned no questions");
        }
        for (GeneratedQuestion q : quiz.questions()) {
            boolean valid = q.prompt() != null && !q.prompt().isBlank()
                    && q.options() != null && q.options().size() == 4
                    && q.correctIndex() >= 0 && q.correctIndex() < 4;
            if (!valid) throw new AiGenerationException("AI returned a malformed question");
        }
    }
}
