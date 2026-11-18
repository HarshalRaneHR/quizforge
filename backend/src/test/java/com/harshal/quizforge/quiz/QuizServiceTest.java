package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.CurrentUser;
import com.harshal.quizforge.auth.Permission;
import com.harshal.quizforge.auth.User;
import com.harshal.quizforge.auth.UserRepository;
import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.common.ConflictException;
import com.harshal.quizforge.common.NotFoundException;
import com.harshal.quizforge.question.Difficulty;
import com.harshal.quizforge.question.QuestionFactory;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.question.QuestionTypeRegistry;
import com.harshal.quizforge.question.handlers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class QuizServiceTest {

    private QuizRepository quizRepo;
    private UserRepository userRepo;
    private AttemptRepository attemptRepo;
    private QuizService service;
    private final QuestionFactory factory = new QuestionFactory(new Random(7));

    /** All seven handlers, for tests that need real validation and grading. */
    public static QuestionTypeRegistry registry() {
        return new QuestionTypeRegistry(List.of(new SingleChoiceHandler(), new MultipleChoiceHandler(),
                new TrueFalseHandler(), new FillBlankHandler(), new NumericHandler(), new OrderingHandler(), new MatchingHandler()));
    }

    @BeforeEach
    void setUp() {
        quizRepo = mock(QuizRepository.class);
        userRepo = mock(UserRepository.class);
        attemptRepo = mock(AttemptRepository.class);
        service = new QuizService(quizRepo, userRepo, attemptRepo, new QuestionMapper(registry()), registry());
    }

    private static User user(String email) {
        User u = new User();
        u.setEmail(email);
        return u;
    }

    private static Quiz quizOwnedBy(String email) {
        Quiz q = new Quiz();
        q.setId(1L);
        q.setOwner(user(email));
        return q;
    }

    @Test
    void createPracticeQuiz_isPrivatePublishedWithPositionsAndPoints() {
        when(userRepo.findByEmail("me@test.com")).thenReturn(Optional.of(user("me@test.com")));
        when(quizRepo.save(any(Quiz.class))).thenAnswer(inv -> inv.getArgument(0));
        var drafts = List.of(
                factory.trueFalse(Difficulty.EASY, "Sky is blue", true, "x"),
                factory.ordering(Difficulty.EASY, "Order", List.of("a", "b", "c"), "x"));

        Quiz quiz = service.createPracticeQuiz("me@test.com", " ", "Science", "text", drafts);

        assertThat(quiz.getTitle()).isEqualTo("Science");          // blank title → AI title
        assertThat(quiz.getStatus()).isEqualTo(QuizStatus.PUBLISHED);
        assertThat(quiz.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(quiz.getTotalPoints()).isEqualTo(3);            // 1 + 2
        assertThat(quiz.getQuestions()).extracting(Question::getPosition).containsExactly(0, 1);
        assertThat(quiz.getQuestions().get(1).getAnswerKeyJson()).contains("correctOrder");
    }

    @Test
    void checkedCounts_rejectsEmptyOrOversizedBlueprints() {
        assertThatThrownBy(() -> QuizService.checkedCounts(Map.of(QuestionType.TRUE_FALSE, 0))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> QuizService.checkedCounts(Map.of(QuestionType.TRUE_FALSE, 15, QuestionType.NUMERIC, 6)))
                .isInstanceOf(BadRequestException.class);
        assertThat(QuizService.checkedCounts(Map.of(QuestionType.TRUE_FALSE, 2, QuestionType.NUMERIC, 0)))
                .containsOnlyKeys(QuestionType.TRUE_FALSE);
    }

    @Test
    void delete_quizOthersAttemptedMustBeArchivedInstead() {
        when(quizRepo.findWithQuestionsById(1L)).thenReturn(Optional.of(quizOwnedBy("teacher@test.com")));
        when(attemptRepo.existsByQuizIdAndUserEmailNot(1L, "teacher@test.com")).thenReturn(true);

        assertThatThrownBy(() -> service.delete(new CurrentUser("teacher@test.com", Set.of()), 1L))
                .isInstanceOf(ConflictException.class);
        verify(quizRepo, never()).delete(any(Quiz.class));
    }

    @Test
    void delete_adminCanDeleteEvenWithAttempts() {
        Quiz quiz = quizOwnedBy("teacher@test.com");
        when(quizRepo.findWithQuestionsById(1L)).thenReturn(Optional.of(quiz));
        when(attemptRepo.existsByQuizIdAndUserEmailNot(1L, "teacher@test.com")).thenReturn(true);

        service.delete(new CurrentUser("admin@test.com", Set.of(Permission.QUIZ_MANAGE_ALL.authority())), 1L);

        verify(quizRepo).delete(quiz);
    }

    @Test
    void getForTaking_hidesOtherUsersPrivateQuizzesAs404() {
        Quiz quiz = quizOwnedBy("owner@test.com");   // DRAFT + PRIVATE by default
        when(quizRepo.findWithQuestionsById(1L)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> service.getOverview(new CurrentUser("other@test.com", Set.of()), 1L))
                .isInstanceOf(NotFoundException.class);
    }
}
