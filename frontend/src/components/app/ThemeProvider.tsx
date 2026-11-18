import { useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  ThemeContext, applyTheme, readStoredTheme, resolveTheme, storeTheme, watchSystemTheme, type Theme,
} from '@/lib/theme';

/**
 * Light / dark / system theme via a `dark` class on <html>, remembered in localStorage.
 * The first paint is handled by a small script in index.html, so there is no flash.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(readStoredTheme);
  const [, rerender] = useState(0);
  const resolvedTheme = resolveTheme(theme);   // cheap; re-read on every render so OS changes show up

  useEffect(() => applyTheme(resolvedTheme), [resolvedTheme]);
  useEffect(() => (theme === 'system' ? watchSystemTheme(() => rerender(n => n + 1)) : undefined), [theme]);

  const value = useMemo(() => ({
    theme,
    resolvedTheme,
    setTheme: (next: string) => {
      if (next !== 'light' && next !== 'dark' && next !== 'system') return;
      storeTheme(next);
      setThemeState(next);
    },
  }), [theme, resolvedTheme]);

  return <ThemeContext value={value}>{children}</ThemeContext>;
}
