import { Button } from '@/components/ui/button'
import { useEffect } from 'react'
import { useAuth } from 'react-oidc-context'
import { Navigate, useLocation } from 'react-router-dom'

export function LoginPage() {
  const auth = useAuth()
  const location = useLocation()

  // ProtectedRoute sets this when it redirects an unauthenticated visit to /login; absent
  // when the user landed here directly (e.g. typed the URL), in which case /app is the
  // correct fallback.
  const from = (location.state as { from?: string } | null)?.from

  // Cognito redirects back here with ?code=&state=; react-oidc-context finishes the token
  // exchange automatically, and this component just waits for isAuthenticated to flip.
  useEffect(() => {
    if (auth.error) {
      // eslint-disable-next-line no-console
      console.error('Sign-in failed', auth.error)
    }
  }, [auth.error])

  if (auth.isAuthenticated) {
    // auth.user.state round-trips whatever was passed to signinRedirect below, surviving
    // the full redirect to Cognito and back — the only way to recover "from" once we're
    // back on this page after a real OAuth round trip (router state doesn't survive a full
    // page navigation away to Cognito's hosted domain and back).
    const returnTo = (typeof auth.user?.state === 'string' && auth.user.state) || from || '/app'
    return <Navigate to={returnTo} replace />
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-6 px-6 text-center">
      <div className="flex w-full max-w-sm flex-col gap-4">
        <h1 className="text-2xl font-semibold text-text-primary">Sign in</h1>

        <Button
          size="lg"
          disabled={auth.isLoading}
          onClick={() => auth.signinRedirect({ extraQueryParams: { identity_provider: 'Google' }, state: from })}
        >
          Continue with Google
        </Button>

        <div className="flex items-center gap-3 text-xs text-text-muted">
          <span className="h-px flex-1 bg-border" />
          or
          <span className="h-px flex-1 bg-border" />
        </div>

        <Button
          variant="secondary"
          size="lg"
          disabled={auth.isLoading}
          onClick={() => auth.signinRedirect({ state: from })}
        >
          Sign in with email
        </Button>

        {auth.error && (
          <p className="text-sm text-error">Sign-in failed. Please try again.</p>
        )}
      </div>
    </main>
  )
}
