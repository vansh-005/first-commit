import type { ReactNode } from 'react'
import { useAuth } from 'react-oidc-context'
import { Navigate, useLocation } from 'react-router-dom'

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const auth = useAuth()
  const location = useLocation()

  if (auth.isLoading) {
    return <main className="flex min-h-screen items-center justify-center text-text-secondary">Loading…</main>
  }

  if (!auth.isAuthenticated) {
    // Preserve the originally requested route so LoginPage can send the user back here
    // after a successful sign-in, instead of always landing on /app.
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <>{children}</>
}
