import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

export type ThemePreference = 'dark' | 'light' | 'system'

const STORAGE_KEY = 'recollect-theme'

function readPreference(): ThemePreference {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored === 'dark' || stored === 'light' || stored === 'system') return stored
  } catch {
    // Storage can be unavailable (private mode); fall through to the default.
  }
  return 'dark'
}

function prefersDark(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia('(prefers-color-scheme: dark)').matches
}

function resolve(preference: ThemePreference): 'dark' | 'light' {
  if (preference === 'system') return prefersDark() ? 'dark' : 'light'
  return preference
}

interface ThemeContextValue {
  preference: ThemePreference
  setPreference: (preference: ThemePreference) => void
}

const ThemeContext = createContext<ThemeContextValue>({ preference: 'dark', setPreference: () => {} })

/** Dark-first theme control (Docs/FRONTEND.md §7): Dark / Light / System, persisted locally. */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreferenceState] = useState<ThemePreference>(readPreference)

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', resolve(preference))
    if (preference !== 'system' || typeof window.matchMedia !== 'function') return
    const query = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => document.documentElement.setAttribute('data-theme', resolve('system'))
    query.addEventListener('change', onChange)
    return () => query.removeEventListener('change', onChange)
  }, [preference])

  const setPreference = useCallback((next: ThemePreference) => {
    setPreferenceState(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Preference just won't persist.
    }
  }, [])

  const value = useMemo(() => ({ preference, setPreference }), [preference, setPreference])
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  return useContext(ThemeContext)
}
