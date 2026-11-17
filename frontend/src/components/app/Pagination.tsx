import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@/components/ui/button';

export function Pagination({ page, totalPages, onPageChange, disabled }: {
  page: number; totalPages: number; onPageChange: (page: number) => void; disabled?: boolean;
}) {
  if (totalPages <= 1) return null;
  return (
    <nav className="mt-6 flex items-center justify-between gap-4" aria-label="Pagination">
      <p className="text-sm text-muted-foreground">Page {page + 1} of {totalPages}</p>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" disabled={disabled || page === 0} onClick={() => onPageChange(page - 1)}>
          <ChevronLeft /> Previous
        </Button>
        <Button variant="outline" size="sm" disabled={disabled || page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}>
          Next <ChevronRight />
        </Button>
      </div>
    </nav>
  );
}
