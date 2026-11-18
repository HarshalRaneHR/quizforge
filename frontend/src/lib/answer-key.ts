import type { KeyedQuestion, QuestionResult } from '@/types';
import type { Response } from './questions';

/** The response a learner would have to give for full marks, derived from the stored answer key. */
export function perfectResponse(q: KeyedQuestion): Response | undefined {
  const k = q.correctAnswer as Record<string, unknown>;
  switch (q.question.type) {
    case 'SINGLE_CHOICE': return { selected: k.correctIndex as number };
    case 'MULTIPLE_CHOICE': return { selected: k.correctIndexes as number[] };
    case 'TRUE_FALSE': return { value: k.answer as boolean };
    case 'FILL_BLANK': return { values: (k.accepted as string[][]).map(list => list[0] ?? '') };
    case 'NUMERIC': return { value: k.value as number };
    case 'ORDERING': return { order: k.correctOrder as number[] };
    case 'MATCHING': return { pairs: k.pairs as number[] };
  }
}

/** Shape the key like a fully-correct graded result, so the normal result view renders it. */
export function asPerfectResult(q: KeyedQuestion): QuestionResult {
  return {
    question: q.question,
    pointsAwarded: q.question.points,
    maxPoints: q.question.points,
    correct: true,
    response: perfectResponse(q) as Record<string, unknown>,
    correctAnswer: q.correctAnswer,
    explanation: q.explanation,
  };
}
