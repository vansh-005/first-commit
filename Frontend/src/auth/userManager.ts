import { UserManager, type UserManagerSettings } from 'oidc-client-ts'

// `/login` is both the sign-in entry point and the OAuth callback route: Cognito redirects
// back here with `?code=...&state=...`, and react-oidc-context finishes the exchange when
// AuthProvider mounts.
const redirectUri = `${window.location.origin}/login`

export const oidcSettings: UserManagerSettings = {
  authority: import.meta.env.VITE_COGNITO_AUTHORITY,
  client_id: import.meta.env.VITE_COGNITO_CLIENT_ID,
  redirect_uri: redirectUri,
  response_type: 'code',
  scope: `openid email profile ${import.meta.env.VITE_COGNITO_API_SCOPE}`,
  automaticSilentRenew: true,
}

// Single shared instance: react-oidc-context's AuthProvider is configured to reuse this
// exact instance (see oidcConfig.ts), and non-component code (api/client.ts) reads the
// current access token from it directly, outside of any React hook.
export const userManager = new UserManager(oidcSettings)
