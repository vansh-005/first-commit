import { ApiError } from '@/api/client'

const GENERIC = 'Something went wrong. Please try again.'

/**
 * Maps any thrown value to short, actionable, non-technical copy (Docs/FRONTEND.md §19).
 * Never surfaces upstream/service names or raw status lines. Only a 400's own message is passed
 * through, since Docs/API.md §6 makes validation messages client-facing.
 */
export function friendlyError(error: unknown, fallback: string = GENERIC): string {
  if (error instanceof ApiError) {
    if (error.status === 401 || error.status === 403) return 'Your session has expired. Please sign in again.'
    if (error.status === 429 || error.status === 503) return 'Recollect is busy right now. Please try again in a moment.'
    if (error.status === 400 && error.message) return error.message
    return fallback
  }
  if (error instanceof TypeError) return 'We couldn’t reach Recollect. Check your connection and try again.'
  return fallback
}
