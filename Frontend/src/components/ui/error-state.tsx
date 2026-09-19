import { Button } from '@/components/ui/button'
import { CircleAlert } from 'lucide-react'

/** Inline, announced error with an optional Retry — never shows raw technical detail. */
export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div
      role="alert"
      className="flex flex-wrap items-center gap-3 rounded-[var(--radius-md)] border border-error/30 bg-error/10 px-4 py-3 text-sm text-error"
    >
      <CircleAlert className="size-4 shrink-0" aria-hidden="true" />
      <p className="flex-1">{message}</p>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          Retry
        </Button>
      )}
    </div>
  )
}
