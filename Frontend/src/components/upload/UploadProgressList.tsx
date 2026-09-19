import type { UploadItem } from '@/hooks/useFileUpload'
import { isLikelySearchable } from '@/lib/fileTypes'
import { formatBytes } from '@/lib/format'
import { CircleAlert, CircleCheck, Clock, TriangleAlert } from 'lucide-react'

function StatusText({ item }: { item: UploadItem }) {
  switch (item.status) {
    case 'queued':
      return (
        <span className="flex items-center gap-1 text-text-muted">
          <Clock className="size-3.5" aria-hidden="true" /> Queued
        </span>
      )
    case 'uploading':
      return <span className="text-text-secondary">Uploading {item.progress}%</span>
    case 'uploaded':
      return (
        <span className="flex items-center gap-1 text-success">
          <CircleCheck className="size-3.5" aria-hidden="true" /> Uploaded
        </span>
      )
    case 'failed':
      return (
        <span className="flex items-center gap-1 text-error">
          <CircleAlert className="size-3.5" aria-hidden="true" /> Failed
        </span>
      )
  }
}

/** Progress bars reflect only real browser→S3 upload progress (Docs/FRONTEND.md §13); once the
 * bytes are up, the file simply reads "Uploaded" and processing continues in the library. */
export function UploadProgressList({ items }: { items: UploadItem[] }) {
  if (items.length === 0) {
    return null
  }

  return (
    <ul className="flex flex-col gap-2" aria-label="Uploads">
      {items.map((item) => (
        <li
          key={item.clientFileId}
          className="rounded-[var(--radius-md)] border border-border bg-surface-raised px-4 py-2.5 text-sm"
        >
          <div className="flex items-center justify-between gap-4">
            <div className="min-w-0">
              <p className="truncate text-text-primary">{item.file.name}</p>
              <p className="text-xs text-text-muted">{formatBytes(item.file.size)}</p>
            </div>
            <div className="shrink-0 text-xs">
              <StatusText item={item} />
            </div>
          </div>
          {item.status === 'uploading' && (
            <div
              role="progressbar"
              aria-label={`Uploading ${item.file.name}`}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={item.progress}
              className="mt-2 h-1 overflow-hidden rounded-full bg-surface-muted"
            >
              <div className="h-full rounded-full bg-accent transition-[width] duration-200" style={{ width: `${item.progress}%` }} />
            </div>
          )}
          {item.status === 'failed' && item.error && <p className="mt-1 text-xs text-error">{item.error}</p>}
          {item.status !== 'failed' && !isLikelySearchable(item.file.name) && (
            <p className="mt-1 flex items-center gap-1 text-xs text-warning">
              <TriangleAlert className="size-3" aria-hidden="true" />
              This file type may not be searchable, but it will still be saved.
            </p>
          )}
        </li>
      ))}
    </ul>
  )
}
