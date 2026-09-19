import { DownloadButton } from '@/components/library/DownloadButton'
import { FileThumb } from '@/components/library/FileThumb'
import { StatusBadge } from '@/components/library/StatusBadge'
import { useFileActions } from '@/hooks/useFileActions'
import { CATEGORY_LABEL } from '@/lib/fileTypes'
import { formatBytes } from '@/lib/format'
import type { DocumentSummary } from '@/types/document'
import { ExternalLink } from 'lucide-react'

/** Full-width row for short lists (e.g. a handful of recent memories), where a card grid would
 * leave a lone tile floating in empty space. Same Open/Download behavior as FileCard. */
export function FileRow({ document }: { document: DocumentSummary }) {
  const { open, download, busy, error } = useFileActions(document.documentId, document.fileName, document.sizeBytes)

  return (
    <div className="animate-fade-up">
      <div className="relative">
        <button
          onClick={open}
          className="group flex w-full items-center gap-4 rounded-[var(--radius-lg)] border border-border bg-surface-raised p-3 pr-16 text-left transition-colors hover:border-accent/50 hover:bg-surface-muted"
        >
          <div className="size-16 shrink-0 overflow-hidden rounded-[var(--radius-md)] border border-border">
            <FileThumb documentId={document.documentId} fileName={document.fileName} mediaCategory={document.mediaCategory} />
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-text-primary">{document.fileName}</p>
            <p className="mt-0.5 truncate text-xs text-text-muted">
              {CATEGORY_LABEL[document.mediaCategory]} · {new Date(document.createdAt).toLocaleDateString()} ·{' '}
              {formatBytes(document.sizeBytes)}
            </p>
          </div>
          <StatusBadge status={document.status} className="hidden sm:inline-flex" />
          <span className="flex shrink-0 items-center gap-1.5 rounded-[var(--radius-md)] border border-border px-3 py-1.5 text-xs font-medium text-text-secondary transition-colors group-hover:border-accent/50 group-hover:text-text-primary">
            Open
            <ExternalLink className="size-3" aria-hidden="true" />
          </span>
        </button>
        <DownloadButton
          fileName={document.fileName}
          onClick={download}
          busy={busy === 'download'}
          className="absolute right-3 top-1/2 -translate-y-1/2"
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
