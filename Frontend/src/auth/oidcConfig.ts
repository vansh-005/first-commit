import type { AuthProviderProps } from 'react-oidc-context'
import { userManager } from './userManager'

export const oidcConfig: AuthProviderProps = {
  userManager,
  onSigninCallback: () => {
    // Strip the ?code=&state= query params so a refresh doesn't replay the exchange.
    window.history.replaceState({}, document.title, window.location.pathname)
  },
}

/**
 * Cognito's logout endpoint is not standard OIDC (no `end_session_endpoint` in its
 * discovery document), so it's built manually rather than via the library's generic
 * `signoutRedirect()`.
 */
export function buildCognitoLogoutUrl(): string {
  const domain = import.meta.env.VITE_COGNITO_DOMAIN
  const clientId = import.meta.env.VITE_COGNITO_CLIENT_ID
  const logoutUri = `${window.location.origin}/`
  return `${domain}/logout?client_id=${clientId}&logout_uri=${encodeURIComponent(logoutUri)}`
}
