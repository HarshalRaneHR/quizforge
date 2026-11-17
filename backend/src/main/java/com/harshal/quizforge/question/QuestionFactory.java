package com.harshal.quizforge.question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

/**
 * Builds {@link QuestionDraft}s from "natural" input (items in the correct order, matching pairs
 * side by side, correct option at any index) and shuffles what the learner sees, so the stored
 * display order never gives the answer away.
 */
public final class QuestionFactory {

    public record Pair(String left, String right) {}

    private final RandomGenerator random;
    /** AI output: shuffle (models tend to put the right answer first). Authors: keep their option order. */
    private final boolean shuffleOptions;

    public QuestionFactory(RandomGenerator random) {
        this(random, true);
    }

    public QuestionFactory(RandomGenerator random, boolean shuffleOptions) {
        this.random = random;
        this.shuffleOptions = shuffleOptions;
    }

    public QuestionDraft singleChoice(Difficulty d, String prompt, List<String> options, int correctIndex, String explanation) {
        Shuffle s = optionShuffle(options.size());
        return draft(QuestionType.SINGLE_CHOICE, d, prompt,
                new QuestionContent.Choice(s.apply(options)),
                new AnswerKey.SingleChoice(s.newIndexOf(correctIndex)), explanation);
    }

    public QuestionDraft multipleChoice(Difficulty d, String prompt, List<String> options, List<Integer> correctIndexes, String explanation) {
        Shuffle s = optionShuffle(options.size());
        List<Integer> correct = correctIndexes.stream().map(s::newIndexOf).sorted().toList();
        return draft(QuestionType.MULTIPLE_CHOICE, d, prompt,
                new QuestionContent.Choice(s.apply(options)), new AnswerKey.MultipleChoice(correct), explanation);
    }

    public QuestionDraft trueFalse(Difficulty d, String statement, boolean answer, String explanation) {
        return draft(QuestionType.TRUE_FALSE, d, statement, new QuestionContent.TrueFalse(), new AnswerKey.TrueFalse(answer), explanation);
    }

    /** {@code acceptedPerBlank.get(i)} = accepted answers for the i-th "___" in the prompt. */
    public QuestionDraft fillBlank(Difficulty d, String prompt, List<List<String>> acceptedPerBlank, String explanation) {
        return draft(QuestionType.FILL_BLANK, d, prompt,
                new QuestionContent.FillBlank(acceptedPerBlank.size()), new AnswerKey.FillBlank(acceptedPerBlank), explanation);
    }

    public QuestionDraft numeric(Difficulty d, String prompt, double value, double tolerance, String unit, String explanation) {
        return draft(QuestionType.NUMERIC, d, prompt,
                new QuestionContent.Numeric(unit == null || unit.isBlank() ? null : unit.trim()),
                new AnswerKey.Numeric(value, Math.abs(tolerance)), explanation);
    }

    /** Items given in the correct order; shown shuffled (never already in the right order). */
    public QuestionDraft ordering(Difficulty d, String prompt, List<String> itemsInCorrectOrder, String explanation) {
        Shuffle s = shuffleNotIdentity(itemsInCorrectOrder.size());
        List<Integer> correctOrder = IntStream.range(0, itemsInCorrectOrder.size()).map(s::newIndexOf).boxed().toList();
        return draft(QuestionType.ORDERING, d, prompt,
                new QuestionContent.Ordering(s.apply(itemsInCorrectOrder)), new AnswerKey.Ordering(correctOrder), explanation);
    }

    /** Left items keep their order; right items are shuffled. */
    public QuestionDraft matching(Difficulty d, String prompt, List<Pair> pairs, String explanation) {
        List<String> left = pairs.stream().map(Pair::left).toList();
        List<String> right = pairs.stream().map(Pair::right).toList();
        Shuffle s = shuffleNotIdentity(pairs.size());
        List<Integer> key = IntStream.range(0, pairs.size()).map(s::newIndexOf).boxed().toList();
        return draft(QuestionType.MATCHING, d, prompt,
                new QuestionContent.Matching(left, s.apply(right)), new AnswerKey.Matching(key), explanation);
    }

    private static QuestionDraft draft(QuestionType type, Difficulty d, String prompt, QuestionContent content,
                                       AnswerKey key, String explanation) {
        return new QuestionDraft(type, d == null ? Difficulty.MEDIUM : d, type.defaultPoints(),
                prompt == null ? null : prompt.trim(), content, key, explanation == null ? null : explanation.trim());
    }

    // ---------------------------------------------------------------- shuffling

    /** perm.get(k) = original index of the element displayed at position k. */
    private record Shuffle(List<Integer> perm) {
        <T> List<T> apply(List<T> original) {
            return perm.stream().map(original::get).toList();
        }

        int newIndexOf(int originalIndex) {
            return perm.indexOf(originalIndex);
        }
    }

    private Shuffle optionShuffle(int n) {
        return shuffleOptions ? shuffle(n) : new Shuffle(IntStream.range(0, n).boxed().toList());
    }

    private Shuffle shuffle(int n) {
        List<Integer> perm = new ArrayList<>(IntStream.range(0, n).boxed().toList());
        Collections.shuffle(perm, random);
        return new Shuffle(perm);
    }

    private Shuffle shuffleNotIdentity(int n) {
        Shuffle s = shuffle(n);
        for (int tries = 0; n > 1 && tries < 10 && isIdentity(s.perm()); tries++) {
            s = shuffle(n);
        }
        if (n > 1 && isIdentity(s.perm())) {   // astronomically unlikely; rotate by one
            List<Integer> rotated = new ArrayList<>(s.perm());
            Collections.rotate(rotated, 1);
            s = new Shuffle(rotated);
        }
        return s;
    }

    private static boolean isIdentity(List<Integer> perm) {
        for (int i = 0; i < perm.size(); i++) if (perm.get(i) != i) return false;
        return true;
    }
}
