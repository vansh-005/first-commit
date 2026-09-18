import { buildCognitoLogoutUrl } from '@/auth/oidcConfig'
import { Button } from '@/components/ui/button'
import { Menu, Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'

/**
 * Docs/FRONTEND.md §6/§12 — the global search bar lives here, once, shared across every
 * authenticated page. Submitting it only ever navigates to /app/search?q=... — the actual
 * search request is made by SearchPage itself, so there is exactly one place in the app that
 * calls the search API (no duplicated search logic per page).
 */
export function TopBar({ onOpenMobileNav }: { onOpenMobileNav: () => void }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const [query, setQuery] = useState(() => searchParams.get('q') ?? '')

  useEffect(() => {
    if (location.pathname === '/app/search') {
      setQuery(searchParams.get('q') ?? '')
    }
  }, [location.pathname, searchParams])

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    const trimmed = query.trim()
    if (!trimmed) return
    navigate(`/app/search?q=${encodeURIComponent(trimmed)}`)
  }

  function handleLogout() {
    auth.removeUser().finally(() => {
      window.location.href = buildCognitoLogoutUrl()
    })
  }

  return (
    <header className="flex h-14 shrink-0 items-center gap-3 border-b border-border bg-surface px-4">
      <button
        type="button"
        onClick={onOpenMobileNav}
        aria-label="Open navigation"
        className="rounded-[var(--radius-sm)] p-1.5 text-text-secondary hover:bg-surface-muted hover:text-text-primary md:hidden"
      >
        <Menu className="size-5" aria-hidden="true" />
      </button>

      <form onSubmit={handleSubmit} className="flex-1">
        <label className="relative block max-w-xl">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-text-muted"
            aria-hidden="true"
          />
          <span className="sr-only">Search your memory</span>
          <input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="What are you trying to remember?"
            className="w-full rounded-[var(--radius-md)] border border-border bg-surface-muted py-2 pl-9 pr-3 text-sm text-text-primary placeholder:text-text-muted focus:border-accent focus:outline-none"
          />
        </label>
      </form>

      <div className="flex items-center gap-3">
        {auth.user?.profile.email && (
          <span className="hidden max-w-[180px] truncate text-sm text-text-secondary sm:inline">
            {auth.user.profile.email}
          </span>
        )}
        <Button variant="ghost" size="sm" onClick={handleLogout}>
          Log out
        </Button>
      </div>
    </header>
  )
}
