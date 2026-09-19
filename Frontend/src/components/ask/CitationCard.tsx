import { DownloadButton } from '@/components/library/DownloadButton'
import { FileThumb } from '@/components/library/FileThumb'
import { useFileActions } from '@/hooks/useFileActions'
import { CATEGORY_LABEL, formatTimestamp } from '@/lib/fileTypes'
import { cleanSnippet } from '@/lib/snippet'
import type { Citation } from '@/types/document'
import { Clock, ExternalLink } from 'lucide-react'

/** Docs/FRONTEND.md §17. Carries no presigned URL — resolves a fresh one from the existing,
 * ownership-checked /access-url only when Open or Download is clicked, the same pattern
 * SearchResultCard uses (kept as a separate component rather than a shared one — Search and Ask
 * stay UI-decoupled per the approved Phase 6 plan). `index` is the source's 1-based number. */
export function CitationCard({ citation, index }: { citation: Citation; index?: number }) {
  const { open, download, busy, error } = useFileActions(citation.documentId, citation.fileName)
  const snippet = cleanSnippet(citation.snippet)

  return (
    <div>
      <div className="relative">
        <button
          onClick={open}
          className="group flex w-full items-center gap-3.5 rounded-[var(--radius-lg)] border border-border bg-surface-raised p-3 pr-16 text-left transition-colors hover:border-accent/50 hover:bg-surface-muted"
        >
          {index !== undefined && (
            <span
              aria-hidden="true"
              className="flex size-5 shrink-0 items-center justify-center rounded-full bg-accent-subtle text-[11px] font-semibold text-accent-text"
            >
              {index}
            </span>
          )}
          <div className="size-14 shrink-0 overflow-hidden rounded-[var(--radius-md)] border border-border">
            <FileThumb
              size="sm"
              documentId={citation.documentId}
              fileName={citation.fileName}
              mediaCategory={citation.mediaCategory}
            />
          </div>

          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-text-primary">{citation.fileName}</p>
            {snippet && <p className="mt-0.5 line-clamp-2 text-[13px] leading-relaxed text-text-secondary">“{snippet}”</p>}
            <div className="mt-1.5 flex items-center gap-2 text-xs text-text-muted">
              <span>{CATEGORY_LABEL[citation.mediaCategory]}</span>
              {citation.mediaTimestamp && (
                <span className="flex items-center gap-1 rounded-full bg-accent-subtle px-2 py-0.5 text-accent-text">
                  <Clock className="size-3" aria-hidden="true" />
                  <span>
                    {formatTimestamp(citation.mediaTimestamp.startMs)} – {formatTimestamp(citation.mediaTimestamp.endMs)}
                  </span>
                </span>
              )}
            </div>
          </div>

          <span className="flex shrink-0 items-center gap-1.5 rounded-[var(--radius-md)] border border-border px-3 py-1.5 text-xs font-medium text-text-secondary transition-colors group-hover:border-accent/50 group-hover:text-text-primary">
            {busy === 'open' ? 'Opening…' : 'Open'}
            <ExternalLink className="size-3" aria-hidden="true" />
          </span>
        </button>
        <DownloadButton
          fileName={citation.fileName}
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
