import { getMe } from '@/api/client'
import { Button } from '@/components/ui/button'
import { useEffect, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { buildCognitoLogoutUrl } from '@/auth/oidcConfig'

/**
 * Minimal authenticated placeholder — the real Home/Library workspace is Phase 3+. This
 * exists to prove login, logout, and authenticated API calls all work end to end.
 */
export function HomePage() {
  const auth = useAuth()
  const [me, setMe] = useState<string | null>(null)
  const [meError, setMeError] = useState<string | null>(null)

  useEffect(() => {
    getMe()
      .then((response) => setMe(response.userId))
      .catch((error: Error) => setMeError(error.message))
  }, [])

  function handleLogout() {
    auth.removeUser().finally(() => {
      window.location.href = buildCognitoLogoutUrl()
    })
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 px-6 text-center">
      <h1 className="text-2xl font-semibold text-text-primary">
        Signed in as {auth.user?.profile.email ?? 'unknown'}
      </h1>
      {me && <p className="text-sm text-text-secondary">Authenticated userId (sub): {me}</p>}
      {meError && <p className="text-sm text-error">Could not verify API access: {meError}</p>}
      <Button variant="secondary" onClick={handleLogout}>
        Log out
      </Button>
    </main>
  )
}
