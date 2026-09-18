import { userManager } from '@/auth/userManager'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL as string | undefined

if (!API_BASE_URL) {
  // eslint-disable-next-line no-console
  console.warn('VITE_API_BASE_URL is not set; API calls will fail.')
}

export interface HealthResponse {
  status: string
  service: string
  version: string
}

export async function getHealth(): Promise<HealthResponse> {
  const response = await fetch(`${API_BASE_URL ?? ''}/api/v1/health`)
  if (!response.ok) {
    throw new Error(`Health check failed with status ${response.status}`)
  }
  return (await response.json()) as HealthResponse
}

export interface MeResponse {
  userId: string
}

/**
 * Calls the internal Phase 2 diagnostic route (see Docs/API.md) to prove the access token
 * is accepted end to end. Not a permanent product feature.
 */
export async function getMe(): Promise<MeResponse> {
  const user = await userManager.getUser()
  if (!user?.access_token) {
    throw new Error('Not authenticated')
  }
  const response = await fetch(`${API_BASE_URL ?? ''}/api/v1/me`, {
    headers: { Authorization: `Bearer ${user.access_token}` },
  })
  if (!response.ok) {
    throw new Error(`/me failed with status ${response.status}`)
  }
  return (await response.json()) as MeResponse
}
