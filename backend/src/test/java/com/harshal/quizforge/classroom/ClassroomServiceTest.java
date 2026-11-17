package com.harshal.quizforge.classroom;

import com.harshal.quizforge.quiz.AttemptRepository.AttemptStats;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure helpers: progress state, best score rounding, CSV safety. (Flows are covered by the API checks.) */
class ClassroomServiceTest {

    private static AttemptStats stats(Double best, long submitted, long open) {
        return new AttemptStats() {
            public Long getQuizId() { return 1L; }
            public Long getUserId() { return 2L; }
            public Double getBest() { return best; }
            public Long getSubmitted() { return submitted; }
            public Long getOpen() { return open; }
        };
    }

    @Test
    void progressFromAttemptStats() {
        assertThat(ClassroomService.progress(null)).isEqualTo(ClassroomDtos.Progress.NOT_STARTED);
        assertThat(ClassroomService.progress(stats(null, 0, 1))).isEqualTo(ClassroomDtos.Progress.IN_PROGRESS);
        assertThat(ClassroomService.progress(stats(0.5, 1, 1))).isEqualTo(ClassroomDtos.Progress.COMPLETED);
    }

    @Test
    void bestScoreIsAWholePercentage() {
        assertThat(ClassroomService.best(stats(0.666, 1, 0))).isEqualTo(67);
        assertThat(ClassroomService.best(stats(null, 0, 1))).isNull();
    }

    @Test
    void csvCellsAreQuotedAndFormulaSafe() {
        assertThat(ClassroomService.csvCell("plain")).isEqualTo("plain");
        assertThat(ClassroomService.csvCell("Quiz, part 1")).isEqualTo("\"Quiz, part 1\"");
        assertThat(ClassroomService.csvCell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(ClassroomService.csvCell("=HYPERLINK(\"x\")")).startsWith("\"'=HYPERLINK");   // CSV injection neutralised
    }
}
