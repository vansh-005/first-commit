import { getAccessUrl } from '@/api/client'
import { FileThumb } from '@/components/library/FileThumb'
import { StatusBadge } from '@/components/library/StatusBadge'
import { CATEGORY_LABEL } from '@/lib/fileTypes'
import { formatBytes } from '@/lib/format'
import type { DocumentSummary } from '@/types/document'
import { useState } from 'react'

/** Docs/FRONTEND.md §14. Opens the file via a freshly-signed access URL rather than any
 * stored/cached link — Docs/API.md never returns a permanent URL. */
export function FileCard({ document }: { document: DocumentSummary }) {
  const [openFailed, setOpenFailed] = useState(false)

  async function openFile() {
    setOpenFailed(false)
    try {
      const { url } = await getAccessUrl(document.documentId)
      window.open(url, '_blank', 'noopener,noreferrer')
    } catch {
      setOpenFailed(true)
    }
  }

  return (
    <div className="animate-fade-up">
      <button
        onClick={openFile}
        className="group flex w-full flex-col gap-3 rounded-[var(--radius-lg)] border border-border bg-surface-raised p-3 text-left transition-all hover:-translate-y-0.5 hover:border-border-strong hover:shadow-[var(--shadow-md)]"
      >
        <div className="relative aspect-[4/3] overflow-hidden rounded-[var(--radius-md)] border border-border">
          <FileThumb
            documentId={document.documentId}
            fileName={document.fileName}
            mediaCategory={document.mediaCategory}
            className="transition-transform duration-300 group-hover:scale-[1.03]"
          />
          <StatusBadge status={document.status} className="absolute left-2 top-2 backdrop-blur" />
        </div>
        <div className="min-w-0 px-0.5">
          <p className="truncate text-sm font-medium text-text-primary">{document.fileName}</p>
          {document.status === 'FAILED' ? (
            <p className="mt-0.5 text-xs text-error">We couldn’t process this file. Try uploading it again.</p>
          ) : (
            <p className="mt-0.5 truncate text-xs text-text-muted">
              {CATEGORY_LABEL[document.mediaCategory]} · {new Date(document.createdAt).toLocaleDateString()} ·{' '}
              {formatBytes(document.sizeBytes)}
            </p>
          )}
        </div>
      </button>
      {openFailed && (
        <p role="alert" className="mt-1 px-1 text-xs text-error">
          We couldn’t open this file. Please try again.
        </p>
      )}
    </div>
  )
}

export function FileCardSkeleton() {
  return (
    <div aria-hidden="true" className="flex flex-col gap-3 rounded-[var(--radius-lg)] border border-border bg-surface-raised p-3">
      <div className="skeleton aspect-[4/3] rounded-[var(--radius-md)]" />
      <div className="flex flex-col gap-2 px-0.5">
        <div className="skeleton h-3.5 w-3/4 rounded-full" />
        <div className="skeleton h-3 w-1/2 rounded-full" />
      </div>
    </div>
  )
}
