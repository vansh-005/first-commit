import { DownloadButton } from '@/components/library/DownloadButton'
import { FileThumb } from '@/components/library/FileThumb'
import { HighlightedText } from '@/components/search/HighlightedText'
import { useFileActions } from '@/hooks/useFileActions'
import { CATEGORY_LABEL, formatTimestamp } from '@/lib/fileTypes'
import { cleanSnippet } from '@/lib/snippet'
import type { SearchResult } from '@/types/document'
import { Clock, ExternalLink } from 'lucide-react'

/** Docs/FRONTEND.md §15/§17. Deliberately does not render `match.score` — the API returns it
 * for ranking/debugging only, never as a user-facing confidence percentage. Open and Download go
 * through the freshly-signed, ownership-checked /access-url, like the Library's FileCard. The
 * snippet is sanitized for display only (see cleanSnippet). `query` drives highlighting. */
export function SearchResultCard({ result, query = '' }: { result: SearchResult; query?: string }) {
  const { document, match } = result
  const { open, download, busy, error } = useFileActions(document.documentId, document.fileName)
  const snippet = cleanSnippet(match.snippet)

  return (
    <div>
      <div className="relative">
        <button
          onClick={open}
          className="group flex w-full items-start gap-4 rounded-[var(--radius-lg)] border border-border bg-surface-raised p-4 pr-16 text-left transition-all hover:border-accent/50 hover:bg-surface-muted"
        >
          <div className="size-[72px] shrink-0 overflow-hidden rounded-[var(--radius-md)] border border-border">
            <FileThumb documentId={document.documentId} fileName={document.fileName} mediaCategory={document.mediaCategory} />
          </div>

          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-text-primary">{document.fileName}</p>
            {snippet && (
              <p className="mt-1 line-clamp-3 text-sm leading-relaxed text-text-secondary">
                <HighlightedText text={snippet} query={query} />
              </p>
            )}
            <div className="mt-2 flex items-center gap-2 text-xs text-text-muted">
              <span>{CATEGORY_LABEL[document.mediaCategory]}</span>
              {match.mediaTimestamp && (
                <span className="flex items-center gap-1 rounded-full bg-accent-subtle px-2 py-0.5 text-accent-text">
                  <Clock className="size-3" aria-hidden="true" />
                  <span>
                    {formatTimestamp(match.mediaTimestamp.startMs)} – {formatTimestamp(match.mediaTimestamp.endMs)}
                  </span>
                </span>
              )}
            </div>
          </div>

          <span className="flex shrink-0 items-center gap-1.5 self-center rounded-[var(--radius-md)] border border-border px-3 py-1.5 text-xs font-medium text-text-secondary transition-colors group-hover:border-accent/50 group-hover:text-text-primary">
            {busy === 'open' ? 'Opening…' : 'Open'}
            <ExternalLink className="size-3" aria-hidden="true" />
          </span>
        </button>
        <DownloadButton
          fileName={document.fileName}
          onClick={download}
          busy={busy === 'download'}
          className="absolute right-4 top-1/2 -translate-y-1/2"
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

export function SearchResultSkeleton() {
  return (
    <div aria-hidden="true" className="flex items-start gap-4 rounded-[var(--radius-lg)] border border-border bg-surface-raised p-4">
      <div className="skeleton size-[72px] shrink-0 rounded-[var(--radius-md)]" />
      <div className="flex flex-1 flex-col gap-2.5 pt-1">
        <div className="skeleton h-3.5 w-1/3 rounded-full" />
        <div className="skeleton h-3 w-full rounded-full" />
        <div className="skeleton h-3 w-4/5 rounded-full" />
      </div>
    </div>
  )
}
