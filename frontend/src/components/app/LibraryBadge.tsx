import { Library } from 'lucide-react';
import { Badge } from '@/components/ui/badge';

/** Marks a built-in QuizForge library quiz (read-only, always available). */
export function LibraryBadge() {
  return (
    <Badge variant="outline" className="border-primary/30 bg-primary/10 text-primary">
      <Library aria-hidden="true" /> Library
    </Badge>
  );
}

export const LIBRARY_AUTHOR = 'QuizForge Library';
