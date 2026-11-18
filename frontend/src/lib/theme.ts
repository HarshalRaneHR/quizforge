import { createContext } from 'react';

export type Theme = 'light' | 'dark' | 'system';
export type ResolvedTheme = 'light' | 'dark';

/** Same key next-themes used, so people keep the theme they already picked. */
export const THEME_STORAGE_KEY = 'theme';

export type ThemeContextValue = { theme: Theme; resolvedTheme: ResolvedTheme; setTheme: (theme: string) => void };

export const ThemeContext = createContext<ThemeContextValue | null>(null);

const systemQuery = () => window.matchMedia('(prefers-color-scheme: dark)');

export function readStoredTheme(): Theme {
  try {
    const saved = localStorage.getItem(THEME_STORAGE_KEY);
    if (saved === 'light' || saved === 'dark' || saved === 'system') return saved;
  } catch { /* storage blocked: fall back to system */ }
  return 'system';
}

export function storeTheme(theme: Theme) {
  try { localStorage.setItem(THEME_STORAGE_KEY, theme); } catch { /* not fatal */ }
}

export const resolveTheme = (theme: Theme): ResolvedTheme =>
  theme === 'system' ? (systemQuery().matches ? 'dark' : 'light') : theme;

export function watchSystemTheme(onChange: () => void) {
  const query = systemQuery();
  query.addEventListener('change', onChange);
  return () => query.removeEventListener('change', onChange);
}

/** Put the `dark` class on <html>, briefly disabling CSS transitions so colours switch at once. */
export function applyTheme(resolved: ResolvedTheme) {
  const root = document.documentElement;
  const pause = document.createElement('style');
  pause.textContent = '*,*::before,*::after{transition:none!important}';
  document.head.appendChild(pause);
  root.classList.toggle('dark', resolved === 'dark');
  root.style.colorScheme = resolved;
  window.getComputedStyle(document.body);   // force a reflow before transitions come back
  setTimeout(() => pause.remove(), 1);
}
