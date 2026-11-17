import { useState, type ReactNode } from 'react';
import { useMutation } from '@tanstack/react-query';
import { ArrowDown, ArrowUp, LoaderCircle, Plus, X } from 'lucide-react';
import { api, errorMessage } from '@/api/client';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { blankQuestion, countBlanks } from '@/lib/authoring';
import { BLANK, QUESTION_TYPES, TYPE_LABEL } from '@/lib/questions';
import type { AuthoringQuestion, AuthoringQuestionView, Difficulty, QuestionType } from '@/types';


function Field({ label, htmlFor, hint, children }: { label: string; htmlFor?: string; hint?: string; children: ReactNode }) {
  return (
    <div className="grid gap-2">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}

/** Editable list of text rows with add / remove / optional move and an optional marker control per row. */
function RowList({ rows, onChange, min, max, placeholder, marker, movable, label }: {
  rows: string[]; onChange: (rows: string[]) => void; min: number; max: number; placeholder: (i: number) => string;
  marker?: (i: number) => ReactNode; movable?: boolean; label: string;
}) {
  const set = (i: number, v: string) => onChange(rows.map((r, k) => (k === i ? v : r)));
  const move = (i: number, d: number) => { const n = [...rows]; [n[i], n[i + d]] = [n[i + d], n[i]]; onChange(n); };
  return (
    <div className="grid gap-2">
      {rows.map((row, i) => (
        <div key={i} className="flex items-center gap-2">
          {marker?.(i)}
          {movable && <span className="w-5 text-center text-xs font-semibold text-muted-foreground">{i + 1}</span>}
          <Input value={row} placeholder={placeholder(i)} aria-label={`${label} ${i + 1}`} onChange={e => set(i, e.target.value)} />
          {movable && (
            <>
              <Button type="button" variant="ghost" size="icon-sm" disabled={i === 0} aria-label={`Move ${label} ${i + 1} up`} onClick={() => move(i, -1)}><ArrowUp /></Button>
              <Button type="button" variant="ghost" size="icon-sm" disabled={i === rows.length - 1} aria-label={`Move ${label} ${i + 1} down`} onClick={() => move(i, 1)}><ArrowDown /></Button>
            </>
          )}
          <Button type="button" variant="ghost" size="icon-sm" disabled={rows.length <= min} aria-label={`Remove ${label} ${i + 1}`}
                  onClick={() => onChange(rows.filter((_, k) => k !== i))}><X /></Button>
        </div>
      ))}
      {rows.length < max && (
        <Button type="button" variant="outline" size="sm" className="justify-self-start" onClick={() => onChange([...rows, ''])}>
          <Plus /> Add {label}
        </Button>
      )}
    </div>
  );
}

function TypeFields({ q, set }: { q: AuthoringQuestion; set: (patch: Partial<AuthoringQuestion>) => void }) {
  switch (q.type) {
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_CHOICE': {
      const options = q.options ?? [];
      const correct = q.correctIndexes ?? [];
      const single = q.type === 'SINGLE_CHOICE';
      // Removing an option shifts indexes: keep the "correct" marks attached to the same options.
      const onOptions = (next: string[]) => {
        if (next.length < options.length) {
          const removed = options.findIndex((o, i) => next[i] !== o);
          const idx = removed === -1 ? options.length - 1 : removed;
          set({ options: next, correctIndexes: correct.filter(c => c !== idx).map(c => (c > idx ? c - 1 : c)) });
        } else set({ options: next });
      };
      return (
        <Field label={single ? 'Options (select the correct one)' : 'Options (tick every correct one)'}>
          {single ? (
            <RadioGroup value={String(correct[0] ?? '')} onValueChange={v => set({ correctIndexes: [Number(v)] })} aria-label="Correct option">
              <RowList rows={options} onChange={onOptions} min={2} max={6} label="option" placeholder={i => `Option ${i + 1}`}
                       marker={i => <RadioGroupItem value={String(i)} aria-label={`Option ${i + 1} is correct`} />} />
            </RadioGroup>
          ) : (
            <RowList rows={options} onChange={onOptions} min={3} max={8} label="option" placeholder={i => `Option ${i + 1}`}
                     marker={i => (
                       <Checkbox checked={correct.includes(i)} aria-label={`Option ${i + 1} is correct`}
                                 onCheckedChange={c => set({ correctIndexes: c === true ? [...correct, i].sort((a, b) => a - b) : correct.filter(x => x !== i) })} />
                     )} />
          )}
        </Field>
      );
    }
    case 'TRUE_FALSE':
      return (
        <Field label="Correct answer">
          <RadioGroup className="flex gap-4" value={String(q.answer ?? true)} onValueChange={v => set({ answer: v === 'true' })}>
            {[true, false].map(v => (
              <Label key={String(v)} className="flex items-center gap-2 font-normal"><RadioGroupItem value={String(v)} /> {v ? 'True' : 'False'}</Label>
            ))}
          </RadioGroup>
        </Field>
      );
    case 'FILL_BLANK': {
      const n = countBlanks(q.prompt);
      const blanks = Array.from({ length: n }, (_, i) => q.blanks?.[i] ?? []);
      return (
        <Field label="Accepted answers" hint="Separate alternatives with commas, e.g. mitochondria, mitochondrion. Matching ignores case and extra spaces.">
          {n === 0 ? <p className="text-sm text-destructive">Add at least one blank (___) to the question text.</p> : blanks.map((list, i) => (
            <Input key={i} aria-label={`Accepted answers for blank ${i + 1}`} placeholder={`Blank ${i + 1}`}
                   defaultValue={list.join(', ')}
                   onChange={e => set({ blanks: blanks.map((b, k) => (k === i ? e.target.value.split(',').map(s => s.trim()).filter(Boolean) : b)) })} />
          ))}
        </Field>
      );
    }
    case 'NUMERIC':
      return (
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Correct value" htmlFor="num-value">
            <Input id="num-value" type="number" step="any" value={q.value ?? ''} onChange={e => set({ value: e.target.value === '' ? undefined : Number(e.target.value) })} />
          </Field>
          <Field label="Tolerance ±" htmlFor="num-tol">
            <Input id="num-tol" type="number" step="any" min={0} value={q.tolerance ?? 0} onChange={e => set({ tolerance: Number(e.target.value) })} />
          </Field>
          <Field label="Unit (optional)" htmlFor="num-unit">
            <Input id="num-unit" value={q.unit ?? ''} placeholder="e.g. km" onChange={e => set({ unit: e.target.value })} />
          </Field>
        </div>
      );
    case 'ORDERING':
      return (
        <Field label="Items in the CORRECT order" hint="Learners see them shuffled.">
          <RowList rows={q.items ?? []} onChange={items => set({ items })} min={3} max={8} label="item" movable placeholder={i => `Step ${i + 1}`} />
        </Field>
      );
    case 'MATCHING': {
      const pairs = q.pairs ?? [];
      const setPair = (i: number, side: 'left' | 'right', v: string) => set({ pairs: pairs.map((p, k) => (k === i ? { ...p, [side]: v } : p)) });
      return (
        <Field label="Matching pairs" hint="Learners see the right-hand column shuffled.">
          {pairs.map((p, i) => (
            <div key={i} className="flex items-center gap-2">
              <Input aria-label={`Left ${i + 1}`} placeholder="Term" value={p.left} onChange={e => setPair(i, 'left', e.target.value)} />
              <span className="text-muted-foreground" aria-hidden="true">→</span>
              <Input aria-label={`Right ${i + 1}`} placeholder="Matches" value={p.right} onChange={e => setPair(i, 'right', e.target.value)} />
              <Button type="button" variant="ghost" size="icon-sm" disabled={pairs.length <= 3} aria-label={`Remove pair ${i + 1}`}
                      onClick={() => set({ pairs: pairs.filter((_, k) => k !== i) })}><X /></Button>
            </div>
          ))}
          {pairs.length < 8 && (
            <Button type="button" variant="outline" size="sm" className="justify-self-start" onClick={() => set({ pairs: [...pairs, { left: '', right: '' }] })}>
              <Plus /> Add pair
            </Button>
          )}
        </Field>
      );
    }
  }
}

/** Create or edit one question of any type. */
export function QuestionEditorDialog({ quizId, editing, initialType, open, onOpenChange, onSaved }: {
  quizId: number; editing?: AuthoringQuestionView; initialType?: QuestionType;
  open: boolean; onOpenChange: (open: boolean) => void; onSaved: () => void;
}) {
  const [q, setQ] = useState<AuthoringQuestion>(() => editing?.question ?? blankQuestion(initialType ?? 'SINGLE_CHOICE'));
  const set = (patch: Partial<AuthoringQuestion>) => setQ(prev => ({ ...prev, ...patch }));

  const save = useMutation({
    mutationFn: () => {
      // Fill-blank: send exactly one accepted-answers list per ___ in the prompt.
      const body = q.type === 'FILL_BLANK'
        ? { ...q, blanks: Array.from({ length: countBlanks(q.prompt) }, (_, i) => q.blanks?.[i] ?? []) }
        : q;
      return editing
        ? api.put(`/studio/quizzes/${quizId}/questions/${editing.id}`, body)
        : api.post(`/studio/quizzes/${quizId}/questions`, body);
    },
    onSuccess: () => { onSaved(); onOpenChange(false); },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{editing ? 'Edit question' : 'Add question'}</DialogTitle>
          <DialogDescription>{TYPE_LABEL[q.type]}: {QUESTION_TYPES.find(t => t.type === q.type)?.description}</DialogDescription>
        </DialogHeader>

        <form id="question-form" className="grid gap-5" onSubmit={e => { e.preventDefault(); save.mutate(); }}>
          <div className="grid gap-4 sm:grid-cols-3">
            <Field label="Type">
              <Select value={q.type} onValueChange={v => setQ(blankQuestion(v as QuestionType, q))}>
                <SelectTrigger className="w-full" aria-label="Question type"><SelectValue /></SelectTrigger>
                <SelectContent>{QUESTION_TYPES.map(t => <SelectItem key={t.type} value={t.type}>{t.label}</SelectItem>)}</SelectContent>
              </Select>
            </Field>
            <Field label="Difficulty">
              <Select value={q.difficulty ?? 'MEDIUM'} onValueChange={v => set({ difficulty: v as Difficulty })}>
                <SelectTrigger className="w-full" aria-label="Difficulty"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="EASY">Easy</SelectItem><SelectItem value="MEDIUM">Medium</SelectItem><SelectItem value="HARD">Hard</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field label="Points" htmlFor="q-points">
              <Input id="q-points" type="number" min={1} max={10} value={q.points ?? 1} onChange={e => set({ points: Number(e.target.value) })} />
            </Field>
          </div>

          <Field label={q.type === 'TRUE_FALSE' ? 'Statement' : 'Question'} htmlFor="q-prompt"
                 hint={q.type === 'FILL_BLANK' ? `Write ${BLANK} (three underscores) where each blank goes.` : undefined}>
            <Textarea id="q-prompt" required rows={2} maxLength={2000} value={q.prompt} onChange={e => set({ prompt: e.target.value })} />
          </Field>

          <TypeFields q={q} set={set} />

          <Field label="Explanation (shown after answering)" htmlFor="q-expl">
            <Textarea id="q-expl" rows={2} maxLength={2000} value={q.explanation ?? ''} onChange={e => set({ explanation: e.target.value })} />
          </Field>

          {save.isError && (
            <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{errorMessage(save.error)}</p>
          )}
        </form>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button type="submit" form="question-form" disabled={save.isPending}>
            {save.isPending && <LoaderCircle className="animate-spin" />} {editing ? 'Save changes' : 'Add question'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
