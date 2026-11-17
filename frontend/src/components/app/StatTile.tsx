import type { LucideIcon } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';

/** A single headline number. (A number this important doesn't need a chart.) */
export function StatTile({ icon: Icon, label, value, hint }: { icon: LucideIcon; label: string; value: string; hint?: string }) {
  return (
    <Card className="py-5">
      <CardContent className="flex items-center gap-4">
        <span className="grid size-11 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary"><Icon className="size-5" aria-hidden="true" /></span>
        <div className="min-w-0">
          <p className="text-2xl font-bold tracking-tight tabular-nums">{value}</p>
          <p className="truncate text-sm text-muted-foreground">{label}</p>
          {hint && <p className="truncate text-xs text-muted-foreground">{hint}</p>}
        </div>
      </CardContent>
    </Card>
  );
}
