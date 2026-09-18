import type { ReactNode } from 'react'
import { useAuth } from 'react-oidc-context'
import { Navigate } from 'react-router-dom'

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const auth = useAuth()

  if (auth.isLoading) {
    return <main className="flex min-h-screen items-center justify-center text-text-secondary">Loading…</main>
  }

  if (!auth.isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}
