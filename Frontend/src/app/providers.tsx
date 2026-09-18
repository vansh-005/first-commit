import type { ReactNode } from 'react'

/**
 * Composition root for app-wide context providers. Empty in Phase 1 — theme, auth, and
 * query-client providers are added as those phases land, without callers needing to change.
 */
export function AppProviders({ children }: { children: ReactNode }) {
  return <>{children}</>
}
