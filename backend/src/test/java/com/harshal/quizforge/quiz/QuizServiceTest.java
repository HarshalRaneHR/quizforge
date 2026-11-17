package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.User;
import com.harshal.quizforge.auth.UserRepository;
import com.harshal.quizforge.common.AiGenerationException;
import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.question.Difficulty;
import com.harshal.quizforge.question.QuestionFactory;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.question.QuestionTypeRegistry;
import com.harshal.quizforge.question.handlers.*;
import com.harshal.quizforge.quiz.AiQuizGenerator.GeneratedQuiz;
import com.harshal.quizforge.quiz.QuizDtos.Blueprint;
import com.harshal.quizforge.quiz.QuizDtos.GenerateQuizRequest;
import com.harshal.quizforge.quiz.QuizDtos.QuizSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuizServiceTest {

    private static final String TEXT = "x".repeat(300);

    private QuizRepository quizRepo;
    private UserRepository userRepo;
    private AiQuizGenerator ai;
    private QuizService service;
    private final QuestionFactory factory = new QuestionFactory(new Random(7));

    @BeforeEach
    void setUp() {
        quizRepo = mock(QuizRepository.class);
        userRepo = mock(UserRepository.class);
        ai = mock(AiQuizGenerator.class);
        var registry = new QuestionTypeRegistry(List.of(new SingleChoiceHandler(), new MultipleChoiceHandler(),
                new TrueFalseHandler(), new FillBlankHandler(), new NumericHandler(), new OrderingHandler(), new MatchingHandler()));
        service = new QuizService(quizRepo, userRepo, mock(AttemptRepository.class), ai, new QuestionMapper(registry));
    }

    private static GenerateQuizRequest request(String title, Map<QuestionType, Integer> counts) {
        return new GenerateQuizRequest(title, TEXT, new Blueprint(counts, Difficulty.MEDIUM));
    }

    @Test
    void generate_savesMixedQuestionTypesWithPositionsAndPoints() {
        User user = new User();
        user.setEmail("me@test.com");
        var drafts = List.of(
                factory.trueFalse(Difficulty.EASY, "Sky is blue", true, "x"),
                factory.ordering(Difficulty.EASY, "Order", List.of("a", "b", "c"), "x"));
        when(ai.generate(eq(TEXT), anyMap(), eq(Difficulty.MEDIUM))).thenReturn(new GeneratedQuiz("Science", drafts));
        when(userRepo.findByEmail("me@test.com")).thenReturn(Optional.of(user));
        when(quizRepo.save(any(Quiz.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizSummary summary = service.generate("me@test.com",
                request(" ", Map.of(QuestionType.TRUE_FALSE, 1, QuestionType.ORDERING, 1)));

        assertThat(summary.title()).isEqualTo("Science");
        assertThat(summary.questionCount()).isEqualTo(2);
        assertThat(summary.totalPoints()).isEqualTo(3);   // 1 (true/false) + 2 (ordering)

        ArgumentCaptor<Quiz> saved = ArgumentCaptor.forClass(Quiz.class);
        verify(quizRepo).save(saved.capture());
        var questions = saved.getValue().getQuestions();
        assertThat(questions).extracting(Question::getPosition).containsExactly(0, 1);
        assertThat(questions).extracting(Question::getType).containsExactly(QuestionType.TRUE_FALSE, QuestionType.ORDERING);
        assertThat(questions.get(1).getAnswerKeyJson()).contains("correctOrder");
        assertThat(questions).allMatch(q -> q.getQuiz() == saved.getValue());
    }

    @Test
    void generate_rejectsEmptyOrOversizedBlueprints() {
        assertThatThrownBy(() -> service.generate("me@test.com", request(null, Map.of(QuestionType.TRUE_FALSE, 0))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.generate("me@test.com",
                request(null, Map.of(QuestionType.TRUE_FALSE, 15, QuestionType.NUMERIC, 6))))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(ai);
    }

    @Test
    void generate_savesNothingWhenAiFails() {
        when(ai.generate(anyString(), anyMap(), any())).thenThrow(new AiGenerationException("bad output"));

        assertThatThrownBy(() -> service.generate("me@test.com", request(null, Map.of(QuestionType.SINGLE_CHOICE, 3))))
                .isInstanceOf(AiGenerationException.class);
        verify(quizRepo, never()).save(any());
    }
}
