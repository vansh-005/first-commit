import { Link } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'

/**
 * Docs/FRONTEND.md §12. Now rendered inside AppShell (Section 6), so this owns page content
 * only, not the page chrome — navigation, search, and the user menu all live in the shared
 * shell. Kept intentionally light: the global search bar in the top bar is the primary
 * interaction, not a second search box duplicated here.
 */
export function HomePage() {
  const auth = useAuth()
  const firstName = auth.user?.profile.given_name ?? auth.user?.profile.name ?? null

  return (
    <div className="flex flex-col gap-8 p-6">
      <div>
        <h1 className="text-2xl font-semibold text-text-primary">
          {firstName ? `Welcome back, ${firstName}` : 'Welcome back'}
        </h1>
        <p className="mt-1 text-sm text-text-secondary">
          Use the search bar above to find anything you've uploaded, or jump into your library.
        </p>
      </div>

      <Link
        to="/app/library"
        className="w-fit rounded-[var(--radius-md)] border border-border bg-surface-raised px-4 py-2 text-sm font-medium text-text-primary transition-colors hover:border-border-strong"
      >
        Go to Library
      </Link>
    </div>
  )
}
