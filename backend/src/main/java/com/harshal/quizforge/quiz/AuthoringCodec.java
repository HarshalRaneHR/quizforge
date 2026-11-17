package com.harshal.quizforge.quiz;

import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.question.AnswerKey;
import com.harshal.quizforge.question.Difficulty;
import com.harshal.quizforge.question.QuestionContent;
import com.harshal.quizforge.question.QuestionDraft;
import com.harshal.quizforge.question.QuestionFactory;
import com.harshal.quizforge.question.QuestionFactory.Pair;
import com.harshal.quizforge.question.QuestionTypeRegistry;
import com.harshal.quizforge.quiz.StudioDtos.AuthoringQuestion;
import com.harshal.quizforge.quiz.StudioDtos.MatchPair;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

/** Converts between the editor's natural question form and stored questions, validating on the way in. */
@Component
public class AuthoringCodec {

    private final QuestionTypeRegistry registry;
    private final QuestionFactory factory = new QuestionFactory(RandomGenerator.getDefault(), false);

    public AuthoringCodec(QuestionTypeRegistry registry) {
        this.registry = registry;
    }

    /** Editor input → validated draft. Throws 400 with every problem listed. */
    public QuestionDraft toDraft(AuthoringQuestion q) {
        Difficulty d = q.difficulty() == null ? Difficulty.MEDIUM : q.difficulty();
        QuestionDraft draft;
        try {
            draft = switch (q.type()) {
                case SINGLE_CHOICE -> {
                    if (q.correctIndexes() == null || q.correctIndexes().size() != 1) {
                        throw new BadRequestException("Mark exactly one correct option");
                    }
                    yield factory.singleChoice(d, q.prompt(), q.options(), q.correctIndexes().getFirst(), q.explanation());
                }
                case MULTIPLE_CHOICE -> factory.multipleChoice(d, q.prompt(), q.options(), q.correctIndexes(), q.explanation());
                case TRUE_FALSE -> factory.trueFalse(d, q.prompt(), q.answer(), q.explanation());
                case FILL_BLANK -> factory.fillBlank(d, q.prompt(), q.blanks(), q.explanation());
                case NUMERIC -> factory.numeric(d, q.prompt(), q.value(), q.tolerance() == null ? 0 : q.tolerance(), q.unit(), q.explanation());
                case ORDERING -> factory.ordering(d, q.prompt(), q.items(), q.explanation());
                case MATCHING -> factory.matching(d, q.prompt(),
                        q.pairs().stream().map(p -> new Pair(p.left(), p.right())).toList(), q.explanation());
            };
        } catch (NullPointerException | IndexOutOfBoundsException e) {
            throw new BadRequestException("Missing or incomplete fields for a " + q.type() + " question");
        }
        if (q.points() != null) {
            draft = new QuestionDraft(draft.type(), draft.difficulty(), q.points(), draft.prompt(),
                    draft.content(), draft.answerKey(), draft.explanation());
        }
        List<String> errors = registry.validate(draft);
        if (!errors.isEmpty()) {
            throw new BadRequestException("Invalid question: " + String.join("; ", errors));
        }
        return draft;
    }

    /** Stored question → editor form (ordering back in correct order, matching pairs re-joined). */
    public AuthoringQuestion toAuthoring(Question q) {
        String c = q.getContentJson();
        String k = q.getAnswerKeyJson();
        return switch (q.getType()) {
            case SINGLE_CHOICE -> base(q, registry.read(c, QuestionContent.Choice.class).options(),
                    List.of(registry.read(k, AnswerKey.SingleChoice.class).correctIndex()), null, null, null, null, null, null, null);
            case MULTIPLE_CHOICE -> base(q, registry.read(c, QuestionContent.Choice.class).options(),
                    registry.read(k, AnswerKey.MultipleChoice.class).correctIndexes(), null, null, null, null, null, null, null);
            case TRUE_FALSE -> base(q, null, null, registry.read(k, AnswerKey.TrueFalse.class).answer(), null, null, null, null, null, null);
            case FILL_BLANK -> base(q, null, null, null, registry.read(k, AnswerKey.FillBlank.class).accepted(), null, null, null, null, null);
            case NUMERIC -> {
                var key = registry.read(k, AnswerKey.Numeric.class);
                yield base(q, null, null, null, null, key.value(), key.tolerance(), registry.read(c, QuestionContent.Numeric.class).unit(), null, null);
            }
            case ORDERING -> {
                var items = registry.read(c, QuestionContent.Ordering.class).items();
                var order = registry.read(k, AnswerKey.Ordering.class).correctOrder();
                yield base(q, null, null, null, null, null, null, null, order.stream().map(items::get).toList(), null);
            }
            case MATCHING -> {
                var content = registry.read(c, QuestionContent.Matching.class);
                var key = registry.read(k, AnswerKey.Matching.class).pairs();
                var pairs = IntStream.range(0, content.left().size())
                        .mapToObj(i -> new MatchPair(content.left().get(i), content.right().get(key.get(i)))).toList();
                yield base(q, null, null, null, null, null, null, null, null, pairs);
            }
        };
    }

    private static AuthoringQuestion base(Question q, List<String> options, List<Integer> correct, Boolean answer,
                                          List<List<String>> blanks, Double value, Double tolerance, String unit,
                                          List<String> items, List<MatchPair> pairs) {
        return new AuthoringQuestion(q.getType(), q.getDifficulty(), q.getPoints(), q.getPrompt(), q.getExplanation(),
                options, correct, answer, blanks, value, tolerance, unit, items, pairs);
    }
}
