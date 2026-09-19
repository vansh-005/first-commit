import { DownloadButton } from '@/components/library/DownloadButton'
import { FileThumb } from '@/components/library/FileThumb'
import { StatusBadge } from '@/components/library/StatusBadge'
import { useFileActions } from '@/hooks/useFileActions'
import { CATEGORY_LABEL } from '@/lib/fileTypes'
import { formatBytes } from '@/lib/format'
import type { DocumentSummary } from '@/types/document'

/** Docs/FRONTEND.md §14. Open and Download both resolve a freshly-signed access URL through the
 * ownership-checked API — Docs/API.md never returns a permanent URL. */
export function FileCard({ document }: { document: DocumentSummary }) {
  const { open, download, busy, error } = useFileActions(document.documentId, document.fileName, document.sizeBytes)

  return (
    <div className="animate-fade-up">
      <div className="group relative">
        <button
          onClick={open}
          className="flex w-full flex-col gap-3 rounded-[var(--radius-lg)] border border-border bg-surface-raised p-3 text-left transition-all hover:-translate-y-0.5 hover:border-border-strong hover:shadow-[var(--shadow-md)]"
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
        {/* A sibling of the card button (never nested). Always visible on touch; on hover/focus at sm+. */}
        <DownloadButton
          fileName={document.fileName}
          onClick={download}
          busy={busy === 'download'}
          className="absolute right-5 top-5 opacity-100 sm:opacity-0 sm:focus-visible:opacity-100 sm:group-focus-within:opacity-100 sm:group-hover:opacity-100"
        />
      </div>
      {error && (
        <p role="alert" className="mt-1 px-1 text-xs text-error">
          {error}
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
