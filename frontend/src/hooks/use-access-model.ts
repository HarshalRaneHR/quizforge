import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import type { AccessModel } from '@/types';

/** Roles, their default permissions, and which permissions admins may adjust (rarely changes, cached). */
export function useAccessModel(enabled = true) {
  return useQuery({
    queryKey: ['access-model'],
    queryFn: () => api.get<AccessModel>('/admin/users/access-model').then(r => r.data),
    staleTime: Infinity,
    enabled,
  });
}
