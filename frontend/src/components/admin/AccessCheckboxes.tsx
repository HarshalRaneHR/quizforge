import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { PERMISSION_INFO } from '@/lib/access';
import { ROLE_LABELS } from '@/lib/roles';
import type { PermissionName, Role } from '@/types';

/** One checkbox per adjustable ability, noting which ones are the role's default. */
export function AccessCheckboxes({ adjustable, defaults, role, value, onChange, idPrefix }: {
  adjustable: PermissionName[]; defaults: PermissionName[]; role: Role;
  value: PermissionName[]; onChange: (next: PermissionName[]) => void; idPrefix: string;
}) {
  const toggle = (p: PermissionName, on: boolean) => onChange(on ? [...value, p] : value.filter(x => x !== p));
  return (
    <div className="grid gap-2">
      {adjustable.map(p => {
        const info = PERMISSION_INFO[p];
        const id = `${idPrefix}-${p}`;
        const isDefault = defaults.includes(p);
        return (
          <Label key={p} htmlFor={id}
                 className="flex cursor-pointer items-start gap-3 rounded-lg border p-3 font-normal has-data-[state=checked]:border-primary/60 has-data-[state=checked]:bg-accent/50">
            <Checkbox id={id} className="mt-0.5" checked={value.includes(p)} onCheckedChange={c => toggle(p, c === true)} />
            <span className="grid gap-0.5">
              <span className="text-sm font-medium">
                {info?.label ?? p}
                {isDefault && <span className="ml-2 text-xs font-normal text-muted-foreground">default for {ROLE_LABELS[role]}</span>}
              </span>
              {info && <span className="text-xs text-muted-foreground">{info.description}</span>}
            </span>
          </Label>
        );
      })}
    </div>
  );
}
