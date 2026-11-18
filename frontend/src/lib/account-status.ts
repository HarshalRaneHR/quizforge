import type { AccountState } from '@/types';

export const ACCOUNT_STATE_INFO: Record<AccountState, { label: string; className: string; hint: string }> = {
  ACTIVE: { label: 'Active', className: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400', hint: 'Can log in normally.' },
  PENDING: { label: 'Pending', className: 'border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-400', hint: 'Has a temporary password and must choose their own at next login.' },
  DEACTIVATED: { label: 'Deactivated', className: 'border-muted-foreground/30 bg-muted text-muted-foreground', hint: 'Cannot log in. Their results and quizzes are kept.' },
};

export const ACCOUNT_STATES = Object.keys(ACCOUNT_STATE_INFO) as AccountState[];
export const accountStateLabel = (s: AccountState) => ACCOUNT_STATE_INFO[s].label;
