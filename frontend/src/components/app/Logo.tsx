import { Zap } from 'lucide-react';
import { cn } from '@/lib/utils';

export function LogoMark({ className }: { className?: string }) {
  return (
    <span className={cn('grid size-8 shrink-0 place-items-center rounded-lg bg-linear-to-br from-violet-500 to-fuchsia-500 text-white shadow-sm', className)}>
      <Zap className="size-4" aria-hidden="true" />
    </span>
  );
}

export function Logo({ className, textClassName }: { className?: string; textClassName?: string }) {
  return (
    <span className={cn('flex items-center gap-2 font-semibold tracking-tight', className)}>
      <LogoMark />
      <span className={cn('text-base', textClassName)}>QuizForge</span>
    </span>
  );
}
