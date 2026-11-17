package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.User;
import com.harshal.quizforge.auth.UserRepository;
import com.harshal.quizforge.common.AiGenerationException;
import com.harshal.quizforge.quiz.AiQuizGenerator.GeneratedQuestion;
import com.harshal.quizforge.quiz.AiQuizGenerator.GeneratedQuiz;
import com.harshal.quizforge.quiz.QuizDtos.GenerateQuizRequest;
import com.harshal.quizforge.quiz.QuizDtos.QuizSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock QuizRepository quizRepo;
    @Mock UserRepository userRepo;
    @Mock AttemptRepository attemptRepo;
    @Mock AiQuizGenerator ai;
    @InjectMocks QuizService service;

    private static final String TEXT = "x".repeat(300);

    private static GeneratedQuestion question() {
        return new GeneratedQuestion("Q?", List.of("a", "b", "c", "d"), 0, "why");
    }

    @Test
    void generate_usesAiTitleWhenNoneGiven_andSavesQuestions() {
        User user = new User();
        user.setEmail("me@test.com");
        when(ai.generate(TEXT, 3)).thenReturn(new GeneratedQuiz("Photosynthesis", List.of(question(), question(), question())));
        when(userRepo.findByEmail("me@test.com")).thenReturn(Optional.of(user));
        when(quizRepo.save(any(Quiz.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizSummary summary = service.generate("me@test.com", new GenerateQuizRequest(" ", TEXT, 3));

        assertThat(summary.title()).isEqualTo("Photosynthesis");
        assertThat(summary.questionCount()).isEqualTo(3);

        ArgumentCaptor<Quiz> saved = ArgumentCaptor.forClass(Quiz.class);
        verify(quizRepo).save(saved.capture());
        assertThat(saved.getValue().getOwner()).isSameAs(user);
        assertThat(saved.getValue().getQuestions()).allMatch(q -> q.getQuiz() == saved.getValue());
    }

    @Test
    void generate_savesNothingWhenAiFails() {
        when(ai.generate(anyString(), anyInt())).thenThrow(new AiGenerationException("bad output"));

        assertThatThrownBy(() -> service.generate("me@test.com", new GenerateQuizRequest(null, TEXT, 3)))
                .isInstanceOf(AiGenerationException.class);
        verify(quizRepo, never()).save(any());
    }
}
