import { getHealth } from '@/api/client'
import { useEffect, useState } from 'react'

type Status = 'checking' | 'ok' | 'error'

/**
 * Dev-only smoke-test indicator for the deployed API's /health route. Not part of the
 * product UI — only rendered in local/dev builds, per Phase 1 scope.
 */
export function DevHealthBadge() {
  const [status, setStatus] = useState<Status>('checking')

  useEffect(() => {
    getHealth()
      .then(() => setStatus('ok'))
      .catch(() => setStatus('error'))
  }, [])

  const label =
    status === 'checking' ? 'Checking API…' : status === 'ok' ? 'API reachable' : 'API unreachable'
  const dotColor = status === 'ok' ? 'bg-success' : status === 'error' ? 'bg-error' : 'bg-warning'

  return (
    <div className="fixed bottom-4 right-4 flex items-center gap-2 rounded-[var(--radius-md)] border border-border bg-surface-raised px-3 py-2 text-xs text-text-secondary shadow-md">
      <span className={`h-2 w-2 rounded-full ${dotColor}`} />
      {label}
    </div>
  )
}
