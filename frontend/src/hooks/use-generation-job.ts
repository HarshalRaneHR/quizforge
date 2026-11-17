import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import type { JobView } from '@/types';

export const isFinished = (job?: JobView) => job?.status === 'SUCCEEDED' || job?.status === 'FAILED';

/** Polls a background generation job every 1.5 s until it succeeds or fails. */
export function useGenerationJob(jobId: string | null) {
  return useQuery({
    queryKey: ['generation-job', jobId],
    queryFn: () => api.get<JobView>(`/generation-jobs/${jobId}`).then(r => r.data),
    enabled: !!jobId,
    refetchInterval: query => (isFinished(query.state.data) ? false : 1500),
  });
}
