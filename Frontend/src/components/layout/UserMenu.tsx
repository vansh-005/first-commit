import { useTheme, type ThemePreference } from '@/app/theme'
import { buildCognitoLogoutUrl } from '@/auth/oidcConfig'
import { cn } from '@/lib/utils'
import { LogOut, Monitor, Moon, Sun } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { useAuth } from 'react-oidc-context'

const THEMES: { value: ThemePreference; label: string; icon: typeof Sun }[] = [
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'system', label: 'System', icon: Monitor },
]

/** Avatar button + popover: who is signed in, the Dark/Light/System theme control, and log out. */
export function UserMenu() {
  const auth = useAuth()
  const { preference, setPreference } = useTheme()
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const panelId = useId()

  const profile = auth.user?.profile
  const email = profile?.email
  const displayName = profile?.name ?? profile?.given_name ?? email ?? 'Account'
  const initial = (displayName[0] ?? '?').toUpperCase()

  useEffect(() => {
    if (!open) return
    function onPointerDown(event: MouseEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false)
        buttonRef.current?.focus()
      }
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  function handleLogout() {
    auth.removeUser().finally(() => {
      window.location.href = buildCognitoLogoutUrl()
    })
  }

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-haspopup="true"
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        aria-label="Account menu"
        className="flex size-8 items-center justify-center rounded-full bg-accent text-sm font-semibold text-white transition-colors hover:bg-accent-hover"
      >
        {initial}
      </button>

      {open && (
        <div
          id={panelId}
          className="animate-fade-up absolute right-0 top-11 z-40 w-64 rounded-[var(--radius-lg)] border border-border bg-surface-raised p-2 shadow-[var(--shadow-md)]"
        >
          <div className="border-b border-border px-2 pb-2.5 pt-1.5">
            <p className="truncate text-sm font-medium text-text-primary">{displayName}</p>
            {email && email !== displayName && <p className="truncate text-xs text-text-muted">{email}</p>}
          </div>

          <div className="px-2 py-2.5">
            <p id={`${panelId}-theme`} className="mb-1.5 text-xs font-medium text-text-muted">
              Theme
            </p>
            <div role="radiogroup" aria-labelledby={`${panelId}-theme`} className="grid grid-cols-3 gap-1 rounded-[var(--radius-md)] bg-surface-muted p-1">
              {THEMES.map(({ value, label, icon: Icon }) => (
                <button
                  key={value}
                  type="button"
                  role="radio"
                  aria-checked={preference === value}
                  onClick={() => setPreference(value)}
                  className={cn(
                    'flex items-center justify-center gap-1.5 rounded-[var(--radius-sm)] px-2 py-1.5 text-xs transition-colors',
                    preference === value
                      ? 'bg-surface-raised text-text-primary shadow-[var(--shadow-sm)]'
                      : 'text-text-secondary hover:text-text-primary',
                  )}
                >
                  <Icon className="size-3.5" aria-hidden="true" />
                  {label}
                </button>
              ))}
            </div>
          </div>

          <button
            type="button"
            onClick={handleLogout}
            className="flex w-full items-center gap-2 rounded-[var(--radius-md)] px-2 py-2 text-sm text-text-secondary transition-colors hover:bg-surface-muted hover:text-text-primary"
          >
            <LogOut className="size-4" aria-hidden="true" />
            Log out
          </button>
        </div>
      )}
    </div>
  )
}
