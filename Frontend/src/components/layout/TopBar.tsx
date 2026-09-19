import { UserMenu } from '@/components/layout/UserMenu'
import { Menu, Search } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'

const IS_MAC = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform)

/**
 * Docs/FRONTEND.md §6/§12 — the global search bar lives here, once, shared across every
 * authenticated page. Submitting it only ever navigates to /app/search?q=... — the actual
 * search request is made by SearchPage itself, so there is exactly one place in the app that
 * calls the search API (no duplicated search logic per page). Cmd/Ctrl+K focuses it from anywhere.
 */
export function TopBar({ onOpenMobileNav }: { onOpenMobileNav: () => void }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const [query, setQuery] = useState(() => searchParams.get('q') ?? '')
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (location.pathname === '/app/search') {
      setQuery(searchParams.get('q') ?? '')
    }
  }, [location.pathname, searchParams])

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        inputRef.current?.focus()
        inputRef.current?.select()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    const trimmed = query.trim()
    if (!trimmed) return
    navigate(`/app/search?q=${encodeURIComponent(trimmed)}`)
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

      <form onSubmit={handleSubmit} role="search" className="flex flex-1 justify-center">
        <label className="relative block w-full max-w-xl">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-text-muted"
            aria-hidden="true"
          />
          <span className="sr-only">Search your memory</span>
          <input
            ref={inputRef}
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="What are you trying to remember?"
            className="h-9 w-full rounded-[var(--radius-md)] border border-border bg-surface-muted pl-9 pr-14 text-sm text-text-primary transition-colors placeholder:text-text-muted focus:border-accent focus:outline-none"
          />
          <kbd
            aria-hidden="true"
            className="pointer-events-none absolute right-2.5 top-1/2 hidden -translate-y-1/2 rounded border border-border px-1.5 py-0.5 font-sans text-[10px] text-text-muted md:block"
          >
            {IS_MAC ? '⌘ K' : 'Ctrl K'}
          </kbd>
        </label>
      </form>

      <UserMenu />
    </header>
  )
}
