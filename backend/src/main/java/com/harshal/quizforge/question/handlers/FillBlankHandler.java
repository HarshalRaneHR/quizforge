package com.harshal.quizforge.question.handlers;

import com.harshal.quizforge.question.AnswerKey;
import com.harshal.quizforge.question.LearnerResponse;
import com.harshal.quizforge.question.QuestionContent;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.question.QuestionTypeHandler;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One input per "___" in the prompt. Partial credit per blank; matching ignores case, extra spaces and end punctuation. */
@Component
public class FillBlankHandler
        implements QuestionTypeHandler<QuestionContent.FillBlank, AnswerKey.FillBlank, LearnerResponse.FillBlank> {

    public static final String BLANK = "___";

    @Override public QuestionType type() { return QuestionType.FILL_BLANK; }
    @Override public Class<QuestionContent.FillBlank> contentClass() { return QuestionContent.FillBlank.class; }
    @Override public Class<AnswerKey.FillBlank> keyClass() { return AnswerKey.FillBlank.class; }
    @Override public Class<LearnerResponse.FillBlank> responseClass() { return LearnerResponse.FillBlank.class; }

    public static int countBlanks(String prompt) {
        if (prompt == null) return 0;
        int count = 0;
        for (int i = prompt.indexOf(BLANK); i >= 0; i = prompt.indexOf(BLANK, i + BLANK.length())) count++;
        return count;
    }

    @Override
    public List<String> validate(String prompt, QuestionContent.FillBlank content, AnswerKey.FillBlank key) {
        List<String> errors = new ArrayList<>();
        int blanks = countBlanks(prompt);
        if (blanks < 1 || blanks > 5) {
            errors.add("prompt must contain 1-5 blanks written as ___");
        } else if (content.blanks() == null || content.blanks() != blanks) {
            errors.add("blanks must equal the number of ___ in the prompt");
        } else if (key.accepted() == null || key.accepted().size() != blanks) {
            errors.add("accepted answers are needed for every blank");
        } else if (key.accepted().stream().anyMatch(list -> list == null || list.isEmpty() || list.stream().allMatch(Checks::blank))) {
            errors.add("each blank needs at least one accepted answer");
        }
        return errors;
    }

    @Override
    public double grade(QuestionContent.FillBlank content, AnswerKey.FillBlank key, LearnerResponse.FillBlank response) {
        List<String> values = response.values();
        if (values == null) return 0.0;
        int blanks = key.accepted().size();
        int correct = 0;
        for (int i = 0; i < blanks && i < values.size(); i++) {
            String given = normalize(values.get(i));
            if (!given.isEmpty() && key.accepted().get(i).stream().map(FillBlankHandler::normalize).anyMatch(given::equals)) {
                correct++;
            }
        }
        return (double) correct / blanks;
    }

    static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        return n.replaceAll("[.,;:!?]+$", "");
    }
}
