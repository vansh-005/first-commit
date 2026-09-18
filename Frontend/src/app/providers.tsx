import { oidcConfig } from '@/auth/oidcConfig'
import type { ReactNode } from 'react'
import { AuthProvider } from 'react-oidc-context'

/** Composition root for app-wide context providers. */
export function AppProviders({ children }: { children: ReactNode }) {
  return <AuthProvider {...oidcConfig}>{children}</AuthProvider>
}
