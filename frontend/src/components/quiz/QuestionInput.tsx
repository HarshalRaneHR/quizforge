import { Fragment, type ReactNode } from 'react';
import { ArrowDown, ArrowUp, CircleCheck, CircleX } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  BLANK, type ChoiceContent, type MatchingContent, type NumericContent, type OrderingContent, type Response,
} from '@/lib/questions';
import { cn } from '@/lib/utils';
import type { QuestionResult, SessionQuestion } from '@/types';

const LETTERS = 'ABCDEFGH';

/** Display order of option indexes for this attempt (shuffled per attempt, or the stored order). */
const displayOrder = (q: SessionQuestion, count: number) => q.optionOrder ?? Array.from({ length: count }, (_, i) => i);

type Props = {
  question: SessionQuestion;
  response: Response | undefined;
  onChange: (response: Response) => void;
  result?: QuestionResult;   // present after grading → read-only with feedback
};

type Mark = 'correct' | 'wrong' | 'idle';

const markClass = (mark: Mark) => cn(
  mark === 'correct' && 'border-emerald-500 bg-emerald-500/10',
  mark === 'wrong' && 'border-red-500 bg-red-500/10',
);

function MarkTag({ mark, wrongText = 'Your answer' }: { mark: Mark; wrongText?: string }) {
  if (mark === 'correct') return <span className="ml-auto flex shrink-0 items-center gap-1 text-xs font-semibold text-emerald-700 dark:text-emerald-400"><CircleCheck className="size-4" /> Correct</span>;
  if (mark === 'wrong') return <span className="ml-auto flex shrink-0 items-center gap-1 text-xs font-semibold text-red-700 dark:text-red-400"><CircleX className="size-4" /> {wrongText}</span>;
  return null;
}

/** A selectable option row (radio or checkbox) with letter badge and result marking. */
function OptionRow({ id, letter, mark, interactive, control, children }: {
  id: string; letter?: string; mark: Mark; interactive: boolean; control: ReactNode; children: ReactNode;
}) {
  return (
    <Label htmlFor={id} className={cn(
      'flex items-center gap-3 rounded-lg border p-3 font-normal transition-colors',
      interactive && 'cursor-pointer hover:bg-accent/60 has-data-[state=checked]:border-primary has-data-[state=checked]:bg-accent',
      markClass(mark),
    )}>
      {control}
      {letter && (
        <span aria-hidden="true" className={cn('grid size-6 shrink-0 place-items-center rounded-md border text-xs font-semibold text-muted-foreground',
          mark === 'correct' && 'border-emerald-500 bg-emerald-500 text-white', mark === 'wrong' && 'border-red-500 bg-red-500 text-white')}>
          {letter}
        </span>
      )}
      <span className="flex-1">{children}</span>
      <MarkTag mark={mark} />
    </Label>
  );
}

function SingleChoice({ question, response, onChange, result }: Props) {
  const { options } = question.content as ChoiceContent;
  const selected = (response as { selected?: number } | undefined)?.selected;
  const correct = (result?.correctAnswer as { correctIndex?: number } | undefined)?.correctIndex;
  return (
    <RadioGroup aria-labelledby={`q-${question.id}-prompt`} disabled={!!result}
                value={selected === undefined ? '' : String(selected)} onValueChange={v => onChange({ selected: Number(v) })}>
      {/* j = original option index (what the server grades); pos = where it's shown */}
      {displayOrder(question, options.length).map((j, pos) => {
        const mark: Mark = !result ? 'idle' : j === correct ? 'correct' : j === selected ? 'wrong' : 'idle';
        const id = `q-${question.id}-o-${j}`;
        return (
          <OptionRow key={j} id={id} letter={LETTERS[pos]} mark={mark} interactive={!result}
                     control={<RadioGroupItem value={String(j)} id={id} />}>{options[j]}</OptionRow>
        );
      })}
    </RadioGroup>
  );
}

function MultipleChoice({ question, response, onChange, result }: Props) {
  const { options } = question.content as ChoiceContent;
  const selected = (response as { selected?: number[] } | undefined)?.selected ?? [];
  const correct = (result?.correctAnswer as { correctIndexes?: number[] } | undefined)?.correctIndexes ?? [];
  const toggle = (j: number, on: boolean) =>
    onChange({ selected: on ? [...selected, j].sort((a, b) => a - b) : selected.filter(s => s !== j) });
  return (
    <div role="group" aria-labelledby={`q-${question.id}-prompt`} className="grid gap-3">
      <p className="text-sm text-muted-foreground">Select all that apply.</p>
      {displayOrder(question, options.length).map((j, pos) => {
        const isCorrect = correct.includes(j);
        const picked = selected.includes(j);
        const mark: Mark = !result ? 'idle' : isCorrect ? 'correct' : picked ? 'wrong' : 'idle';
        const id = `q-${question.id}-o-${j}`;
        return (
          <OptionRow key={j} id={id} letter={LETTERS[pos]} mark={mark} interactive={!result}
                     control={<Checkbox id={id} checked={picked} disabled={!!result} onCheckedChange={c => toggle(j, c === true)} />}>
            {options[j]}
            {result && isCorrect && !picked && <span className="ml-2 text-xs text-muted-foreground">(you missed this)</span>}
          </OptionRow>
        );
      })}
    </div>
  );
}

function TrueFalse({ question, response, onChange, result }: Props) {
  const value = (response as { value?: boolean } | undefined)?.value;
  const answer = (result?.correctAnswer as { answer?: boolean } | undefined)?.answer;
  return (
    <RadioGroup aria-labelledby={`q-${question.id}-prompt`} disabled={!!result} className="grid grid-cols-2 gap-3"
                value={value === undefined ? '' : String(value)} onValueChange={v => onChange({ value: v === 'true' })}>
      {[true, false].map(option => {
        const mark: Mark = !result ? 'idle' : option === answer ? 'correct' : option === value ? 'wrong' : 'idle';
        const id = `q-${question.id}-${option}`;
        return (
          <OptionRow key={String(option)} id={id} mark={mark} interactive={!result}
                     control={<RadioGroupItem value={String(option)} id={id} />}>
            <span className="font-medium">{option ? 'True' : 'False'}</span>
          </OptionRow>
        );
      })}
    </RadioGroup>
  );
}

function FillBlank({ question, response, onChange, result }: Props) {
  const parts = question.prompt.split(BLANK);
  const values = (response as { values?: string[] } | undefined)?.values ?? [];
  const accepted = (result?.correctAnswer as { accepted?: string[][] } | undefined)?.accepted ?? [];
  const setValue = (i: number, v: string) => {
    const next = Array.from({ length: parts.length - 1 }, (_, k) => values[k] ?? '');
    next[i] = v;
    onChange({ values: next });
  };
  return (
    <div className="space-y-3">
      <p id={`q-${question.id}-prompt`} className="text-base leading-loose sm:text-lg">
        {parts.map((part, i) => (
          <Fragment key={i}>
            {part}
            {i < parts.length - 1 && (
              <Input aria-label={`Blank ${i + 1}`} disabled={!!result} value={values[i] ?? ''} onChange={e => setValue(i, e.target.value)}
                     className="mx-1 inline-flex h-8 w-44 align-middle" placeholder={`Blank ${i + 1}`} />
            )}
          </Fragment>
        ))}
      </p>
      {result && accepted.length > 0 && (
        <p className="text-sm text-muted-foreground">
          Accepted: {accepted.map((list, i) => <span key={i} className="mr-3 font-medium text-foreground">{accepted.length > 1 && `${i + 1}. `}{list.join(' / ')}</span>)}
        </p>
      )}
    </div>
  );
}

function NumericAnswer({ question, response, onChange, result }: Props) {
  const { unit } = question.content as NumericContent;
  const value = (response as { value?: number } | undefined)?.value;
  const key = result?.correctAnswer as { value?: number; tolerance?: number } | undefined;
  return (
    <div className="space-y-3">
      <div className="flex max-w-xs items-center gap-2">
        <Input type="number" step="any" inputMode="decimal" aria-labelledby={`q-${question.id}-prompt`} disabled={!!result}
               className={cn(result && markClass(result.correct ? 'correct' : 'wrong'))}
               value={value ?? ''} placeholder="Your answer"
               onChange={e => onChange({ value: e.target.value === '' ? undefined : Number(e.target.value) })} />
        {unit && <span className="text-sm text-muted-foreground">{unit}</span>}
      </div>
      {result && key?.value !== undefined && (
        <p className="text-sm text-muted-foreground">
          Correct answer: <span className="font-medium text-foreground">{key.value}{key.tolerance ? ` ± ${key.tolerance}` : ''}{unit ? ` ${unit}` : ''}</span>
        </p>
      )}
    </div>
  );
}

function Ordering({ question, response, onChange, result }: Props) {
  const { items } = question.content as OrderingContent;
  const order = (response as { order?: number[] } | undefined)?.order ?? items.map((_, i) => i);
  const correct = (result?.correctAnswer as { correctOrder?: number[] } | undefined)?.correctOrder;
  const move = (pos: number, delta: number) => {
    const next = [...order];
    [next[pos], next[pos + delta]] = [next[pos + delta], next[pos]];
    onChange({ order: next });
  };
  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">Use the arrows to put the items in the correct order (first at the top).</p>
      <ol aria-labelledby={`q-${question.id}-prompt`} className="grid gap-2">
        {order.map((itemIndex, pos) => {
          const mark: Mark = !correct ? 'idle' : correct[pos] === itemIndex ? 'correct' : 'wrong';
          return (
            <li key={itemIndex} className={cn('flex items-center gap-3 rounded-lg border p-2 pl-3', markClass(mark))}>
              <span className="grid size-6 shrink-0 place-items-center rounded-md bg-muted text-xs font-semibold">{pos + 1}</span>
              <span className="flex-1">{items[itemIndex]}</span>
              {!result ? (
                <span className="flex gap-1">
                  <Button type="button" variant="ghost" size="icon" disabled={pos === 0} aria-label={`Move "${items[itemIndex]}" up`} onClick={() => move(pos, -1)}><ArrowUp /></Button>
                  <Button type="button" variant="ghost" size="icon" disabled={pos === order.length - 1} aria-label={`Move "${items[itemIndex]}" down`} onClick={() => move(pos, 1)}><ArrowDown /></Button>
                </span>
              ) : <MarkTag mark={mark} wrongText="Wrong position" />}
            </li>
          );
        })}
      </ol>
      {correct && !result?.correct && (
        <p className="text-sm text-muted-foreground">Correct order: <span className="font-medium text-foreground">{correct.map(i => items[i]).join(' → ')}</span></p>
      )}
    </div>
  );
}

function Matching({ question, response, onChange, result }: Props) {
  const { left, right } = question.content as MatchingContent;
  const pairs = (response as { pairs?: (number | null)[] } | undefined)?.pairs ?? left.map(() => null);
  const correct = (result?.correctAnswer as { pairs?: number[] } | undefined)?.pairs;
  const setPair = (i: number, r: number) => {
    const next = left.map((_, k) => pairs[k] ?? null);
    next[i] = r;
    onChange({ pairs: next });
  };
  return (
    <div className="grid gap-2" role="group" aria-labelledby={`q-${question.id}-prompt`}>
      <p className="text-sm text-muted-foreground">Match each item on the left with one on the right.</p>
      {left.map((l, i) => {
        const chosen = pairs[i];
        const mark: Mark = !correct ? 'idle' : chosen === correct[i] ? 'correct' : 'wrong';
        return (
          <div key={i} className={cn('grid items-center gap-2 rounded-lg border p-3 sm:grid-cols-[1fr_auto_1fr]', markClass(mark))}>
            <span className="font-medium">{l}</span>
            <span aria-hidden="true" className="hidden text-muted-foreground sm:block">→</span>
            <div className="flex items-center gap-2">
              <Select value={chosen === null || chosen === undefined ? '' : String(chosen)} disabled={!!result} onValueChange={v => setPair(i, Number(v))}>
                <SelectTrigger className="w-full" aria-label={`Match for ${l}`}><SelectValue placeholder="Choose…" /></SelectTrigger>
                <SelectContent>{right.map((r, j) => <SelectItem key={j} value={String(j)}>{r}</SelectItem>)}</SelectContent>
              </Select>
              {result && <MarkTag mark={mark} wrongText="" />}
            </div>
            {correct && mark === 'wrong' && (
              <span className="text-sm text-muted-foreground sm:col-span-3">Correct: <span className="font-medium text-foreground">{right[correct[i]]}</span></span>
            )}
          </div>
        );
      })}
    </div>
  );
}

/** Renders the answer UI for any question type. */
export function QuestionInput(props: Props) {
  switch (props.question.type) {
    case 'SINGLE_CHOICE': return <SingleChoice {...props} />;
    case 'MULTIPLE_CHOICE': return <MultipleChoice {...props} />;
    case 'TRUE_FALSE': return <TrueFalse {...props} />;
    case 'FILL_BLANK': return <FillBlank {...props} />;
    case 'NUMERIC': return <NumericAnswer {...props} />;
    case 'ORDERING': return <Ordering {...props} />;
    case 'MATCHING': return <Matching {...props} />;
  }
}
