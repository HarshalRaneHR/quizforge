import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { LoaderCircle } from 'lucide-react';
import { toast } from 'sonner';
import { api, errorMessage } from '@/api/client';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import type { QuizSettings, StudioQuiz } from '@/types';

type Form = {
  maxAttempts: string;      // '' = unlimited
  passPercentage: string;   // '' = no pass mark
  shuffleQuestions: boolean;
  shuffleOptions: boolean;
  resultsVisibility: QuizSettings['resultsVisibility'];
};

const toForm = (s: QuizSettings): Form => ({
  maxAttempts: s.maxAttempts ? String(s.maxAttempts) : '',
  passPercentage: s.passPercentage ? String(s.passPercentage) : '',
  shuffleQuestions: s.shuffleQuestions,
  shuffleOptions: s.shuffleOptions,
  resultsVisibility: s.resultsVisibility,
});

/** How learners take the quiz. Editable while the quiz is a draft. */
export function SettingsCard({ quiz, editable, onSaved }: { quiz: StudioQuiz; editable: boolean; onSaved: (q: StudioQuiz) => void }) {
  const [form, setForm] = useState<Form>(() => toForm(quiz.settings));
  const dirty = JSON.stringify(form) !== JSON.stringify(toForm(quiz.settings));
  const set = (patch: Partial<Form>) => setForm(f => ({ ...f, ...patch }));

  const save = useMutation({
    mutationFn: () => api.put<StudioQuiz>(`/studio/quizzes/${quiz.id}/settings`, {
      maxAttempts: form.maxAttempts ? Number(form.maxAttempts) : null,
      passPercentage: form.passPercentage ? Number(form.passPercentage) : null,
      shuffleQuestions: form.shuffleQuestions,
      shuffleOptions: form.shuffleOptions,
      resultsVisibility: form.resultsVisibility,
    }).then(r => r.data),
    onSuccess: q => { onSaved(q); toast.success('Settings saved'); },
    onError: e => toast.error('Could not save settings', { description: errorMessage(e) }),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Settings</CardTitle>
        <CardDescription>{editable ? 'How learners take this quiz.' : 'Locked while the quiz is not a draft.'}</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="grid gap-5" onSubmit={e => { e.preventDefault(); save.mutate(); }}>
          <fieldset disabled={!editable} className="grid gap-5 disabled:opacity-70">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="grid gap-2">
                <Label htmlFor="s-attempts">Max attempts per learner</Label>
                <Input id="s-attempts" type="number" min={1} max={100} placeholder="Unlimited"
                       value={form.maxAttempts} onChange={e => set({ maxAttempts: e.target.value })} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="s-pass">Pass mark (%)</Label>
                <Input id="s-pass" type="number" min={1} max={100} placeholder="No pass mark"
                       value={form.passPercentage} onChange={e => set({ passPercentage: e.target.value })} />
              </div>
            </div>
            <div className="grid gap-3">
              <Label className="flex items-center gap-2 font-normal">
                <Checkbox checked={form.shuffleQuestions} onCheckedChange={c => set({ shuffleQuestions: c === true })} />
                Shuffle question order for each attempt
              </Label>
              <Label className="flex items-center gap-2 font-normal">
                <Checkbox checked={form.shuffleOptions} onCheckedChange={c => set({ shuffleOptions: c === true })} />
                Shuffle answer options for each attempt (multiple choice / multi-select)
              </Label>
            </div>
            <div className="grid gap-2">
              <Label id="s-results">After submitting, learners see</Label>
              <RadioGroup aria-labelledby="s-results" value={form.resultsVisibility}
                          onValueChange={v => set({ resultsVisibility: v as Form['resultsVisibility'] })} className="grid gap-2 sm:grid-cols-2">
                {[
                  { value: 'FULL', label: 'Full results', hint: 'Score, correct answers and explanations' },
                  { value: 'SCORE_ONLY', label: 'Score only', hint: 'Exam mode: answers stay hidden' },
                ].map(o => (
                  <Label key={o.value} className="flex cursor-pointer items-start gap-2 rounded-lg border p-3 font-normal has-data-[state=checked]:border-primary has-data-[state=checked]:bg-accent">
                    <RadioGroupItem value={o.value} className="mt-0.5" />
                    <span><span className="block text-sm font-medium">{o.label}</span><span className="text-xs text-muted-foreground">{o.hint}</span></span>
                  </Label>
                ))}
              </RadioGroup>
            </div>
          </fieldset>
          {editable && dirty && (
            <div className="flex justify-end gap-2">
              <Button type="button" variant="ghost" onClick={() => setForm(toForm(quiz.settings))}>Discard</Button>
              <Button type="submit" disabled={save.isPending}>{save.isPending && <LoaderCircle className="animate-spin" />} Save settings</Button>
            </div>
          )}
        </form>
      </CardContent>
    </Card>
  );
}
