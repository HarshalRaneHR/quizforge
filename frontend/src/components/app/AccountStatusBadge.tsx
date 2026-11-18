import { Badge } from '@/components/ui/badge';
import { ACCOUNT_STATE_INFO } from '@/lib/account-status';
import { cn } from '@/lib/utils';
import type { AccountState } from '@/types';

export function AccountStatusBadge({ status, className }: { status: AccountState; className?: string }) {
  const s = ACCOUNT_STATE_INFO[status] ?? ACCOUNT_STATE_INFO.ACTIVE;   // older backend without statuses
  return <Badge variant="outline" title={s.hint} className={cn('font-normal', s.className, className)}>{s.label}</Badge>;
}
