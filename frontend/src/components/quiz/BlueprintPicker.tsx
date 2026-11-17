import { Dices, Equal, Minus, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import {
  BLUEPRINT_PRESETS, equalCounts, MAX_PER_TYPE, MAX_QUESTIONS, QUESTION_TYPES, randomCounts, totalOf, type Counts,
} from '@/lib/questions';
import { cn } from '@/lib/utils';
import type { Difficulty, QuestionType } from '@/types';

const DIFFICULTIES: { value: Difficulty; label: string; hint: string }[] = [
  { value: 'EASY', label: 'Easy', hint: 'Recall' },
  { value: 'MEDIUM', label: 'Medium', hint: 'Understanding' },
  { value: 'HARD', label: 'Hard', hint: 'Application' },
];

/**
 * Pick the total number of questions first, then split it across types — by hand (+/−),
 * evenly (Equal), randomly (Random) or with a preset. Valid when the split adds up to the total.
 */
export function BlueprintPicker({ target, onTargetChange, counts, onCountsChange, difficulty, onDifficultyChange }: {
  target: number; onTargetChange: (n: number) => void;
  counts: Counts; onCountsChange: (c: Counts) => void;
  difficulty: Difficulty; onDifficultyChange: (d: Difficulty) => void;
}) {
  const assigned = totalOf(counts);
  const left = target - assigned;
  const set = (type: QuestionType, n: number) => onCountsChange({ ...counts, [type]: Math.max(0, Math.min(MAX_PER_TYPE, n)) });

  const changeTarget = (raw: number) => {
    const n = Math.max(1, Math.min(MAX_QUESTIONS, Math.round(raw) || 1));
    onTargetChange(n);
    // Shrinking below what's already assigned: re-spread evenly so the split still fits.
    if (assigned > n) onCountsChange(equalCounts(n));
  };

  const applyPreset = (c: Counts) => { onTargetChange(totalOf(c)); onCountsChange(c); };

  return (
    <div className="grid gap-5">
      <div className="grid gap-2 sm:max-w-xs">
        <Label htmlFor="bp-total">Number of questions</Label>
        <Input id="bp-total" type="number" min={1} max={MAX_QUESTIONS} value={target}
               onChange={e => changeTarget(Number(e.target.value))} />
        <p className="text-xs text-muted-foreground">1–{MAX_QUESTIONS}. Then choose how to split them across question types.</p>
      </div>

      <div className="grid gap-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <Label>Question types</Label>
          <div className="flex flex-wrap gap-1.5">
            <Button type="button" variant="outline" size="xs" onClick={() => onCountsChange(equalCounts(target))}>
              <Equal /> Equal
            </Button>
            <Button type="button" variant="outline" size="xs" onClick={() => onCountsChange(randomCounts(target))}>
              <Dices /> Random
            </Button>
            {BLUEPRINT_PRESETS.map(p => (
              <Button key={p.label} type="button" variant="ghost" size="xs" onClick={() => applyPreset(p.counts)}>{p.label}</Button>
            ))}
            {assigned > 0 && <Button type="button" variant="ghost" size="xs" onClick={() => onCountsChange({})}>Clear</Button>}
          </div>
        </div>
        <ul className="grid gap-2 sm:grid-cols-2">
          {QUESTION_TYPES.map(({ type, label, description, icon: Icon }) => {
            const n = counts[type] ?? 0;
            return (
              <li key={type} className={cn('flex items-center gap-3 rounded-lg border p-3 transition-colors', n > 0 && 'border-primary/50 bg-accent/50')}>
                <span className={cn('grid size-9 shrink-0 place-items-center rounded-md bg-muted text-muted-foreground', n > 0 && 'bg-primary/15 text-primary')}>
                  <Icon className="size-4" aria-hidden="true" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium">{label}</p>
                  <p className="truncate text-xs text-muted-foreground">{description}</p>
                </div>
                <div className="flex items-center gap-1" role="group" aria-label={`${label} questions`}>
                  <Button type="button" variant="outline" size="icon-xs" aria-label={`Fewer ${label} questions`} disabled={n === 0} onClick={() => set(type, n - 1)}><Minus /></Button>
                  <span className="w-6 text-center text-sm font-semibold tabular-nums" aria-live="polite">{n}</span>
                  <Button type="button" variant="outline" size="icon-xs" aria-label={`More ${label} questions`} disabled={n >= MAX_PER_TYPE || left <= 0} onClick={() => set(type, n + 1)}><Plus /></Button>
                </div>
              </li>
            );
          })}
        </ul>
        <p className={cn('text-xs', left === 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-700 dark:text-amber-400')} aria-live="polite">
          {left === 0 ? `All ${target} questions assigned ✓` : `${assigned} of ${target} assigned, ${left} left. Use +, Equal or Random.`}
        </p>
      </div>

      <div className="grid gap-2">
        <Label id="difficulty-label">Difficulty</Label>
        <RadioGroup aria-labelledby="difficulty-label" value={difficulty} onValueChange={v => onDifficultyChange(v as Difficulty)} className="grid grid-cols-3 gap-2">
          {DIFFICULTIES.map(d => (
            <Label key={d.value} htmlFor={`diff-${d.value}`}
                   className="flex cursor-pointer items-center gap-2 rounded-lg border p-3 font-normal has-data-[state=checked]:border-primary has-data-[state=checked]:bg-accent">
              <RadioGroupItem id={`diff-${d.value}`} value={d.value} />
              <span><span className="block text-sm font-medium">{d.label}</span><span className="text-xs text-muted-foreground">{d.hint}</span></span>
            </Label>
          ))}
        </RadioGroup>
      </div>
    </div>
  );
}
