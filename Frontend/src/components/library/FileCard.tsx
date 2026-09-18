import { getAccessUrl } from '@/api/client'
import type { DocumentSummary, DocumentStatus } from '@/types/document'

const STATUS_LABEL: Record<DocumentStatus, string> = {
  UPLOAD_PENDING: 'Uploading',
  UPLOADED: 'Processing',
  INDEXING: 'Processing',
  READY: 'Ready',
  FAILED: 'Failed',
}

/** Docs/FRONTEND.md §14. Opens the file via a freshly-signed access URL rather than any
 * stored/cached link — Docs/API.md never returns a permanent URL. */
export function FileCard({ document }: { document: DocumentSummary }) {
  async function openFile() {
    try {
      const { url } = await getAccessUrl(document.documentId)
      window.open(url, '_blank', 'noopener,noreferrer')
    } catch {
      // No toast system yet in Phase 3 — silently ignored rather than a raw error.
    }
  }

  return (
    <button
      onClick={openFile}
      className="flex flex-col gap-2 rounded-[var(--radius-lg)] border border-border bg-surface-raised p-4 text-left transition-colors hover:border-border-strong"
    >
      <div className="flex h-24 items-center justify-center rounded-[var(--radius-md)] bg-surface-muted text-xs text-text-muted">
        {document.mediaCategory}
      </div>
      <p className="truncate text-sm font-medium text-text-primary">{document.fileName}</p>
      <div className="flex items-center justify-between text-xs text-text-muted">
        <span>{new Date(document.createdAt).toLocaleDateString()}</span>
        <span>{STATUS_LABEL[document.status]}</span>
      </div>
    </button>
  )
}
