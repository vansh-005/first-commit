import { Button } from '@/components/ui/button'
import { useEffect } from 'react'
import { useAuth } from 'react-oidc-context'
import { Navigate } from 'react-router-dom'

export function LoginPage() {
  const auth = useAuth()

  // Cognito redirects back here with ?code=&state=; react-oidc-context finishes the token
  // exchange automatically, and this component just waits for isAuthenticated to flip.
  useEffect(() => {
    if (auth.error) {
      // eslint-disable-next-line no-console
      console.error('Sign-in failed', auth.error)
    }
  }, [auth.error])

  if (auth.isAuthenticated) {
    return <Navigate to="/app" replace />
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-6 px-6 text-center">
      <div className="flex w-full max-w-sm flex-col gap-4">
        <h1 className="text-2xl font-semibold text-text-primary">Sign in</h1>

        <Button
          size="lg"
          disabled={auth.isLoading}
          onClick={() => auth.signinRedirect({ extraQueryParams: { identity_provider: 'Google' } })}
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
          onClick={() => auth.signinRedirect()}
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
