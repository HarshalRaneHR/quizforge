package com.harshal.quizforge.analytics;

import com.harshal.quizforge.analytics.AnalyticsDtos.MyAnalytics;
import com.harshal.quizforge.analytics.AnalyticsDtos.PlatformStats;
import com.harshal.quizforge.analytics.AnalyticsDtos.QuizAnalytics;
import com.harshal.quizforge.auth.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Progress for learners, quiz insights for instructors, platform stats for admins")
public class AnalyticsController {

    private final AnalyticsService analytics;

    @Operation(summary = "My progress: averages, streak, score trend, accuracy by question type")
    @GetMapping("/api/analytics/me")
    public MyAnalytics mine(@Parameter(hidden = true) Authentication auth) {
        return analytics.mine(CurrentUser.of(auth));
    }

    @Operation(summary = "How learners did on one of my quizzes",
            description = "Score distribution, pass rate and per-question difficulty with the most common wrong answer. Excludes your own previews.")
    @GetMapping("/api/studio/quizzes/{id}/analytics")
    public QuizAnalytics quiz(@Parameter(hidden = true) Authentication auth, @PathVariable Long id) {
        return analytics.forQuiz(CurrentUser.of(auth), id);
    }

    @Operation(summary = "Platform statistics (admin)")
    @GetMapping("/api/admin/stats")
    public PlatformStats platform() {
        return analytics.platform();
    }
}
