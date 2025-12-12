import { useState, useEffect, useCallback } from 'react';

type Theme = 'light' | 'dark' | 'system';

const THEME_KEY = 'sql2sql-theme';

export const useTheme = () => {
  const [theme, setThemeState] = useState<Theme>(() => {
    // 서버 사이드 렌더링 대응
    if (typeof window === 'undefined') return 'system';

    const saved = localStorage.getItem(THEME_KEY) as Theme | null;
    return saved || 'system';
  });

  const [resolvedTheme, setResolvedTheme] = useState<'light' | 'dark'>('light');

  // 시스템 테마 감지
  const getSystemTheme = useCallback((): 'light' | 'dark' => {
    if (typeof window === 'undefined') return 'light';
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }, []);

  // 실제 적용될 테마 계산
  const getResolvedTheme = useCallback((t: Theme): 'light' | 'dark' => {
    if (t === 'system') {
      return getSystemTheme();
    }
    return t;
  }, [getSystemTheme]);

  // DOM에 테마 적용
  const applyTheme = useCallback((resolved: 'light' | 'dark') => {
    const root = document.documentElement;
    if (resolved === 'dark') {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
    setResolvedTheme(resolved);
  }, []);

  // 테마 변경
  const setTheme = useCallback((newTheme: Theme) => {
    setThemeState(newTheme);
    localStorage.setItem(THEME_KEY, newTheme);
    applyTheme(getResolvedTheme(newTheme));
  }, [applyTheme, getResolvedTheme]);

  // 테마 토글 (light <-> dark, system은 현재 상태 기준으로 토글)
  const toggleTheme = useCallback(() => {
    const currentResolved = getResolvedTheme(theme);
    const newTheme = currentResolved === 'dark' ? 'light' : 'dark';
    setTheme(newTheme);
  }, [theme, getResolvedTheme, setTheme]);

  // 초기화 및 시스템 테마 변경 감지
  useEffect(() => {
    // 초기 테마 적용
    applyTheme(getResolvedTheme(theme));

    // 시스템 테마 변경 감지
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleChange = () => {
      if (theme === 'system') {
        applyTheme(getSystemTheme());
      }
    };

    mediaQuery.addEventListener('change', handleChange);
    return () => mediaQuery.removeEventListener('change', handleChange);
  }, [theme, applyTheme, getResolvedTheme, getSystemTheme]);

  return {
    theme,           // 사용자 설정 ('light' | 'dark' | 'system')
    resolvedTheme,   // 실제 적용된 테마 ('light' | 'dark')
    setTheme,        // 테마 직접 설정
    toggleTheme,     // 테마 토글
    isDark: resolvedTheme === 'dark',
  };
};