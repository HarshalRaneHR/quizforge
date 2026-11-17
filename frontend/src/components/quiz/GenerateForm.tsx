import { useState } from 'react';
import { LoaderCircle, Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { equalCounts, MAX_QUESTIONS, totalOf, type Counts } from '@/lib/questions';
import { cn } from '@/lib/utils';
import type { Difficulty } from '@/types';
import { BlueprintPicker } from './BlueprintPicker';

const MIN_CHARS = 200;
const MAX_CHARS = 20000;

export type GenerateValues = {
  title?: string;
  sourceText: string;
  blueprint: { counts: Record<string, number>; difficulty: Difficulty };
};

/** Study material + question-type blueprint. Used for practice quizzes and in the Studio builder. */
export function GenerateForm({ withTitle, busy, onSubmit }: {
  withTitle?: boolean; busy: boolean; onSubmit: (values: GenerateValues) => void;
}) {
  const [title, setTitle] = useState('');
  const [sourceText, setSourceText] = useState('');
  const [target, setTarget] = useState(10);
  const [counts, setCounts] = useState<Counts>(() => equalCounts(10));
  const [difficulty, setDifficulty] = useState<Difficulty>('MEDIUM');

  const chars = sourceText.length;
  const enough = chars >= MIN_CHARS;
  const total = totalOf(counts);
  // The split must add up to the chosen number of questions.
  const valid = enough && total === target && total >= 1 && total <= MAX_QUESTIONS;

  const submit = () => onSubmit({
    title: withTitle ? title : undefined,
    sourceText,
    blueprint: {
      counts: Object.fromEntries(Object.entries(counts).filter(([, n]) => (n ?? 0) > 0)) as Record<string, number>,
      difficulty,
    },
  });

  return (
    <form className="grid gap-5" onSubmit={e => { e.preventDefault(); if (valid) submit(); }}>
      {withTitle && (
        <div className="grid gap-2">
          <Label htmlFor="gen-title">Title <span className="font-normal text-muted-foreground">(optional)</span></Label>
          <Input id="gen-title" maxLength={120} placeholder="AI will suggest one" value={title} onChange={e => setTitle(e.target.value)} />
        </div>
      )}
      <div className="grid gap-2">
        <Label htmlFor="gen-source">Study material</Label>
        <Textarea id="gen-source" required minLength={MIN_CHARS} maxLength={MAX_CHARS} rows={8}
                  className="min-h-40 resize-y" placeholder="Paste your notes, an article or a textbook section…"
                  aria-describedby="gen-source-hint" value={sourceText} onChange={e => setSourceText(e.target.value)} />
        <div id="gen-source-hint" className="flex justify-between text-xs text-muted-foreground">
          <span className={cn(enough && 'text-emerald-600 dark:text-emerald-400')}>
            {enough ? 'Looks good ✓' : `${MIN_CHARS - chars} more characters needed`}
          </span>
          <span>{chars.toLocaleString()} / {MAX_CHARS.toLocaleString()}</span>
        </div>
      </div>
      <BlueprintPicker target={target} onTargetChange={setTarget} counts={counts} onCountsChange={setCounts}
                       difficulty={difficulty} onDifficultyChange={setDifficulty} />
      <div className="flex justify-end">
        <Button type="submit" disabled={busy || !valid}>
          {busy ? <><LoaderCircle className="animate-spin" /> Generating…</> : <><Sparkles /> Generate {target} question{target === 1 ? '' : 's'}</>}
        </Button>
      </div>
    </form>
  );
}
