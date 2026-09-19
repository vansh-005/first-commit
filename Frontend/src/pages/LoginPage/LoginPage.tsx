import { Wordmark } from '@/components/brand/Logo'
import { MemoryCollage } from '@/components/brand/ProductPreview'
import { Button } from '@/components/ui/button'
import { usePageTitle } from '@/hooks/usePageTitle'
import { LoaderCircle, Lock, Mail } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { Link, Navigate, useLocation } from 'react-router-dom'

function GoogleMark() {
  return (
    <svg viewBox="0 0 48 48" className="size-5" aria-hidden="true">
      <path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.6l6.8-6.8C35.8 2.4 30.3 0 24 0 14.6 0 6.5 5.4 2.6 13.2l7.9 6.1C12.4 13.6 17.7 9.5 24 9.5z" />
      <path fill="#4285F4" d="M46.5 24.5c0-1.6-.1-3.1-.4-4.5H24v9h12.7c-.6 3-2.3 5.5-4.8 7.2l7.5 5.8c4.4-4.1 7.1-10.1 7.1-17.5z" />
      <path fill="#FBBC05" d="M10.5 28.7c-.5-1.4-.8-3-.8-4.7s.3-3.2.8-4.7l-7.9-6.1C.9 16.4 0 20.1 0 24s.9 7.6 2.6 10.8l7.9-6.1z" />
      <path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.5-5.8c-2.1 1.4-4.9 2.3-8.4 2.3-6.3 0-11.6-4.1-13.5-9.8l-7.9 6.1C6.5 42.6 14.6 48 24 48z" />
    </svg>
  )
}

export function LoginPage() {
  usePageTitle('Sign in')
  const auth = useAuth()
  const location = useLocation()
  const [redirecting, setRedirecting] = useState<'google' | 'email' | null>(null)

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
      setRedirecting(null)
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

  const busy = auth.isLoading || redirecting !== null

  function signIn(provider: 'google' | 'email') {
    setRedirecting(provider)
    const options = provider === 'google' ? { extraQueryParams: { identity_provider: 'Google' }, state: from } : { state: from }
    auth.signinRedirect(options).catch(() => setRedirecting(null))
  }

  return (
    <div className="flex min-h-screen bg-background">
      {/* Illustration pinned to the dark theme so it reads as a "scene" in either app theme. */}
      <aside
        data-theme="dark"
        className="relative hidden flex-col justify-between overflow-hidden bg-background p-12 text-text-primary lg:flex lg:w-[57%]"
      >
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 bg-[radial-gradient(50%_45%_at_70%_30%,var(--accent-subtle),transparent)]"
        />
        <Link to="/" className="relative w-fit" aria-label="Recollect home">
          <Wordmark />
        </Link>

        <div className="relative">
          <MemoryCollage />
        </div>

        <div className="relative max-w-md">
          <p className="text-2xl font-semibold leading-snug tracking-tight">
            Everything you’ve saved, one question away.
          </p>
          <p className="mt-2 text-sm text-text-secondary">
            Describe what you remember. Recollect finds the file — and the exact moment.
          </p>
        </div>
      </aside>

      <main className="flex flex-1 items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          <Link to="/" className="mb-10 block w-fit lg:hidden" aria-label="Recollect home">
            <Wordmark />
          </Link>

          <h1 className="text-2xl font-semibold tracking-tight text-text-primary">Welcome back</h1>
          <p className="mt-2 text-sm text-text-secondary">Sign in to search and ask across your memories.</p>

          <div className="mt-8 flex flex-col gap-4">
            <Button size="lg" disabled={busy} onClick={() => signIn('google')} className="gap-3">
              {redirecting === 'google' ? (
                <LoaderCircle className="size-5 animate-spin" aria-hidden="true" />
              ) : (
                <GoogleMark />
              )}
              Continue with Google
            </Button>

            <div className="flex items-center gap-3 text-xs text-text-muted" aria-hidden="true">
              <span className="h-px flex-1 bg-border" />
              or
              <span className="h-px flex-1 bg-border" />
            </div>

            <Button variant="secondary" size="lg" disabled={busy} onClick={() => signIn('email')} className="gap-3">
              {redirecting === 'email' ? (
                <LoaderCircle className="size-5 animate-spin" aria-hidden="true" />
              ) : (
                <Mail className="size-5" aria-hidden="true" />
              )}
              Sign in with email
            </Button>

            <div role="status" aria-live="polite" className="min-h-5 text-center text-sm">
              {auth.isLoading && !redirecting && <span className="text-text-muted">Signing you in…</span>}
              {redirecting && !auth.error && (
                <span className="text-text-muted">
                  Redirecting to {redirecting === 'google' ? 'Google' : 'secure sign-in'}…
                </span>
              )}
            </div>
            {auth.error && (
              <p role="alert" className="rounded-[var(--radius-md)] border border-error/30 bg-error/10 px-3 py-2 text-sm text-error">
                We couldn’t sign you in. Please try again.
              </p>
            )}
          </div>

          <p className="mt-10 flex items-start gap-2 text-xs leading-relaxed text-text-muted">
            <Lock className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
            Your memories stay private to your account.
          </p>
        </div>
      </main>
    </div>
  )
}
