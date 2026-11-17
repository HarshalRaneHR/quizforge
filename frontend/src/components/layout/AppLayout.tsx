import { Suspense } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { ThemeToggle } from '@/components/app/ThemeToggle';
import { Separator } from '@/components/ui/separator';
import { SidebarInset, SidebarProvider, SidebarTrigger } from '@/components/ui/sidebar';
import { Skeleton } from '@/components/ui/skeleton';
import { AppSidebar } from './AppSidebar';
import { titleFor } from './nav';

function PageSkeleton() {
  return (
    <div className="space-y-4" aria-hidden="true">
      <Skeleton className="h-9 w-64" />
      <Skeleton className="h-5 w-96 max-w-full" />
      <Skeleton className="h-48 w-full rounded-xl" />
    </div>
  );
}

export default function AppLayout() {
  const { pathname } = useLocation();
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <header className="sticky top-0 z-20 flex h-14 shrink-0 items-center gap-2 border-b bg-background/80 px-4 backdrop-blur">
          <SidebarTrigger className="-ml-1" />
          <Separator orientation="vertical" className="mr-1 data-[orientation=vertical]:h-4" />
          <span className="text-sm font-medium">{titleFor(pathname)}</span>
          <div className="ml-auto">
            <ThemeToggle />
          </div>
        </header>
        <main className="flex-1 px-4 py-6 md:px-8 md:py-8">
          <div className="mx-auto w-full max-w-5xl">
            {/* Pages are lazy-loaded: each route downloads its own JS chunk on first visit. */}
            <Suspense fallback={<PageSkeleton />}>
              <Outlet />
            </Suspense>
          </div>
        </main>
      </SidebarInset>
    </SidebarProvider>
  );
}
