import { Link } from 'react-router-dom';
import { FileQuestion } from 'lucide-react';
import { EmptyState } from '@/components/app/EmptyState';
import { Button } from '@/components/ui/button';

export default function NotFoundPage() {
  return (
    <EmptyState icon={FileQuestion} title="Page not found" description="The page you're looking for doesn't exist."
                action={<Button asChild><Link to="/">Back to my quizzes</Link></Button>} />
  );
}
