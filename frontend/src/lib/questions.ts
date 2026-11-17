import {
  ArrowDownUp, CircleDot, Hash, ListChecks, Shuffle, SquareCheckBig, TextCursorInput, type LucideIcon,
} from 'lucide-react';
import type { QuestionType } from '@/types';

/** Display info for each question type (order = order in the generator form). */
export const QUESTION_TYPES: { type: QuestionType; label: string; description: string; icon: LucideIcon }[] = [
  { type: 'SINGLE_CHOICE', label: 'Multiple choice', description: 'Pick one correct option', icon: CircleDot },
  { type: 'MULTIPLE_CHOICE', label: 'Multi-select', description: 'Select all that apply', icon: ListChecks },
  { type: 'TRUE_FALSE', label: 'True / False', description: 'Is the statement true?', icon: SquareCheckBig },
  { type: 'FILL_BLANK', label: 'Fill in the blank', description: 'Type the missing term', icon: TextCursorInput },
  { type: 'NUMERIC', label: 'Numeric', description: 'Answer with a number', icon: Hash },
  { type: 'ORDERING', label: 'Ordering', description: 'Put steps in sequence', icon: ArrowDownUp },
  { type: 'MATCHING', label: 'Matching', description: 'Pair related items', icon: Shuffle },
];

export const TYPE_LABEL = Object.fromEntries(QUESTION_TYPES.map(t => [t.type, t.label])) as Record<QuestionType, string>;
export const TYPE_ICON = Object.fromEntries(QUESTION_TYPES.map(t => [t.type, t.icon])) as Record<QuestionType, LucideIcon>;

// ---- Per-type shapes. The API sends content/answers as generic JSON; these mirror the backend records.
export type ChoiceContent = { options: string[] };
export type FillBlankContent = { blanks: number };
export type NumericContent = { unit?: string | null };
export type OrderingContent = { items: string[] };
export type MatchingContent = { left: string[]; right: string[] };

export type Response =
  | { selected?: number }               // SINGLE_CHOICE
  | { selected?: number[] }             // MULTIPLE_CHOICE
  | { value?: boolean }                 // TRUE_FALSE
  | { values?: string[] }               // FILL_BLANK
  | { value?: number }                  // NUMERIC
  | { order?: number[] }                // ORDERING
  | { pairs?: (number | null)[] };      // MATCHING

export const BLANK = '___';

/** Initial response for a question; ordering starts with the displayed order (a valid answer to adjust). */
export function initialResponse(type: QuestionType, content: Record<string, unknown>): Response | undefined {
  if (type === 'ORDERING') return { order: (content as OrderingContent).items.map((_, i) => i) };
  return undefined;
}

/** Has the learner given a complete answer? Used for the progress bar and the submit button. */
export function isAnswered(type: QuestionType, content: Record<string, unknown>, r: Response | undefined, touched: boolean): boolean {
  if (!r) return false;
  switch (type) {
    case 'SINGLE_CHOICE': return typeof (r as { selected?: number }).selected === 'number';
    case 'MULTIPLE_CHOICE': return ((r as { selected?: number[] }).selected?.length ?? 0) > 0;
    case 'TRUE_FALSE': return typeof (r as { value?: boolean }).value === 'boolean';
    case 'FILL_BLANK': {
      const values = (r as { values?: string[] }).values ?? [];
      const blanks = (content as FillBlankContent).blanks;
      return values.length === blanks && values.every(v => v?.trim());
    }
    case 'NUMERIC': return Number.isFinite((r as { value?: number }).value);
    case 'ORDERING': return touched;   // the starting order is itself an answer; require the learner to engage
    case 'MATCHING': {
      const pairs = (r as { pairs?: (number | null)[] }).pairs ?? [];
      return pairs.length === (content as MatchingContent).left.length && pairs.every(p => p !== null && p !== undefined);
    }
  }
}

// ---- Generation blueprint
export const MAX_QUESTIONS = 20;
export const MAX_PER_TYPE = 15;

export type Counts = Partial<Record<QuestionType, number>>;

export const BLUEPRINT_PRESETS: { label: string; counts: Counts }[] = [
  { label: 'Quick check', counts: { SINGLE_CHOICE: 5 } },
  { label: 'Mixed', counts: { SINGLE_CHOICE: 3, MULTIPLE_CHOICE: 2, TRUE_FALSE: 2, FILL_BLANK: 2, ORDERING: 1 } },
  { label: 'Exam style', counts: { SINGLE_CHOICE: 4, MULTIPLE_CHOICE: 3, FILL_BLANK: 2, NUMERIC: 1, ORDERING: 1, MATCHING: 1 } },
];

export const totalOf = (counts: Counts) => Object.values(counts).reduce((sum, n) => sum + (n ?? 0), 0);

/**
 * Spreads `total` as evenly as possible over all types, e.g. 10 → three types get 2, four get 1.
 * The leftover questions go to types picked at random, so repeated clicks vary which ones get the extra.
 */
export function equalCounts(total: number): Counts {
  const types = QUESTION_TYPES.map(t => t.type);
  const base = Math.floor(total / types.length);
  const extra = new Set([...types].sort(() => Math.random() - 0.5).slice(0, total % types.length));
  return Object.fromEntries(types.map(t => [t, Math.min(MAX_PER_TYPE, base + (extra.has(t) ? 1 : 0))])) as Counts;
}

/**
 * Spreads `total` questions randomly over all types: every type gets one first (as far as the total
 * allows, picked in random order) so the mix is varied, then the rest land on random types.
 */
export function randomCounts(total: number): Counts {
  const types = QUESTION_TYPES.map(t => t.type).sort(() => Math.random() - 0.5);
  const counts: Counts = {};
  for (let i = 0; i < total; i++) {
    const type = i < types.length ? types[i] : types[Math.floor(Math.random() * types.length)];
    if ((counts[type] ?? 0) >= MAX_PER_TYPE) { i--; continue; }
    counts[type] = (counts[type] ?? 0) + 1;
  }
  return counts;
}

export function formatPoints(n: number) {
  return Number.isInteger(n) ? String(n) : n.toFixed(2).replace(/0+$/, '').replace(/\.$/, '');
}
