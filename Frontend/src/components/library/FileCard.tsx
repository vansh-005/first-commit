import { getAccessUrl } from '@/api/client'
import { useIntersectionOnce } from '@/hooks/useIntersectionOnce'
import type { DocumentSummary, DocumentStatus } from '@/types/document'
import { useState } from 'react'

const STATUS_LABEL: Record<DocumentStatus, string> = {
  // Not "Uploading" — that's the active local XHR queue's job (see useFileUpload /
  // UploadProgressList). A persisted UPLOAD_PENDING document (e.g. after a page reload)
  // just hasn't been picked up by Phase 4's ingestion pipeline yet.
  UPLOAD_PENDING: 'Awaiting processing',
  UPLOADED: 'Processing',
  INDEXING: 'Processing',
  READY: 'Ready',
  FAILED: 'Failed',
}

/** Docs/FRONTEND.md §14. Opens the file via a freshly-signed access URL rather than any
 * stored/cached link — Docs/API.md never returns a permanent URL. */
export function FileCard({ document }: { document: DocumentSummary }) {
  const [thumbnailUrl, setThumbnailUrl] = useState<string | null>(null)
  const [thumbnailFailed, setThumbnailFailed] = useState(false)

  // Only fetches once the card actually scrolls into view, and only for images — a
  // library with hundreds of files must not fire hundreds of access-url requests upfront.
  // The bucket stays private throughout; only a short-lived presigned URL ever reaches
  // the browser, never a raw S3 key.
  const thumbnailRef = useIntersectionOnce<HTMLDivElement>(() => {
    if (document.mediaCategory !== 'IMAGE') return
    getAccessUrl(document.documentId)
      .then((response) => setThumbnailUrl(response.url))
      .catch(() => setThumbnailFailed(true))
  })

  async function openFile() {
    try {
      const { url } = await getAccessUrl(document.documentId)
      window.open(url, '_blank', 'noopener,noreferrer')
    } catch {
      // No toast system yet in Phase 3 — silently ignored rather than a raw error.
    }
  }

  const showThumbnail = document.mediaCategory === 'IMAGE' && thumbnailUrl && !thumbnailFailed

  return (
    <button
      onClick={openFile}
      className="flex flex-col gap-2 rounded-[var(--radius-lg)] border border-border bg-surface-raised p-4 text-left transition-colors hover:border-border-strong"
    >
      <div ref={thumbnailRef} className="h-24 overflow-hidden rounded-[var(--radius-md)] bg-surface-muted">
        {showThumbnail ? (
          <img
            src={thumbnailUrl}
            alt={document.fileName}
            className="h-full w-full object-cover"
            onError={() => setThumbnailFailed(true)}
          />
        ) : (
          <div className="flex h-full items-center justify-center text-xs text-text-muted">{document.mediaCategory}</div>
        )}
      </div>
      <p className="truncate text-sm font-medium text-text-primary">{document.fileName}</p>
      <div className="flex items-center justify-between text-xs text-text-muted">
        <span>{new Date(document.createdAt).toLocaleDateString()}</span>
        <span>{STATUS_LABEL[document.status]}</span>
      </div>
    </button>
  )
}
