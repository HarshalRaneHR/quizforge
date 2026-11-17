import type { AuthoringQuestion, QuestionType } from '@/types';
import { BLANK } from './questions';

/** Sensible empty form for each type (keeps prompt/explanation/points/difficulty when switching type). */
export function blankQuestion(type: QuestionType, keep?: Partial<AuthoringQuestion>): AuthoringQuestion {
  const base: AuthoringQuestion = {
    type, prompt: keep?.prompt ?? '', explanation: keep?.explanation ?? '',
    difficulty: keep?.difficulty ?? 'MEDIUM',
    points: keep?.points ?? (['MULTIPLE_CHOICE', 'ORDERING', 'MATCHING'].includes(type) ? 2 : 1),
  };
  switch (type) {
    case 'SINGLE_CHOICE': return { ...base, options: ['', '', '', ''], correctIndexes: [0] };
    case 'MULTIPLE_CHOICE': return { ...base, options: ['', '', '', ''], correctIndexes: [] };
    case 'TRUE_FALSE': return { ...base, answer: true };
    case 'FILL_BLANK': return { ...base, prompt: base.prompt || `The ${BLANK} is …`, blanks: [[]] };
    case 'NUMERIC': return { ...base, tolerance: 0, unit: '' };
    case 'ORDERING': return { ...base, items: ['', '', ''] };
    case 'MATCHING': return { ...base, pairs: [{ left: '', right: '' }, { left: '', right: '' }, { left: '', right: '' }] };
  }
}

/** Number of "___" blanks in a fill-in-the-blank prompt. */
export const countBlanks = (prompt: string) => prompt.split(BLANK).length - 1;
