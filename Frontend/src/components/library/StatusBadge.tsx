import { cn } from '@/lib/utils'
import type { DocumentStatus } from '@/types/document'
import { CircleAlert, CircleCheck, LoaderCircle } from 'lucide-react'

export const STATUS_LABEL: Record<DocumentStatus, string> = {
  // Not "Uploading" — that's the active local queue's job (see useFileUpload). A persisted
  // UPLOAD_PENDING document (e.g. after a page reload) just hasn't been picked up yet.
  UPLOAD_PENDING: 'Awaiting processing',
  UPLOADED: 'Processing',
  INDEXING: 'Processing',
  READY: 'Ready',
  FAILED: 'Failed',
}

/** Ready / Processing / Failed pill (Docs/FRONTEND.md §13/§19). Never conveyed by color alone —
 * every state has an icon and text. */
export function StatusBadge({ status, className }: { status: DocumentStatus; className?: string }) {
  const tone =
    status === 'READY'
      ? 'bg-success/15 text-success'
      : status === 'FAILED'
        ? 'bg-error/15 text-error'
        : 'bg-accent-subtle text-accent-text'
  const Icon = status === 'READY' ? CircleCheck : status === 'FAILED' ? CircleAlert : LoaderCircle

  return (
    <span className={cn('inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium', tone, className)}>
      <Icon className={cn('size-3', tone.includes('accent') && 'animate-spin')} aria-hidden="true" />
      {STATUS_LABEL[status]}
    </span>
  )
}
