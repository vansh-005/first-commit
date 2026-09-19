import { getAccessUrl } from '@/api/client'
import type { Citation } from '@/types/document'
import { FileText, Image as ImageIcon, Music, Video } from 'lucide-react'

const CATEGORY_ICON = {
  IMAGE: ImageIcon,
  VIDEO: Video,
  AUDIO: Music,
  DOCUMENT: FileText,
  OTHER: FileText,
} as const

function formatTimestamp(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

/** Docs/FRONTEND.md §17. Carries no presigned URL — resolves a fresh one from the existing,
 * ownership-checked /access-url only when actually clicked, the same pattern SearchResultCard
 * uses (kept as a separate component rather than a shared one — Search and Ask stay
 * UI-decoupled per the approved Phase 6 plan). */
export function CitationCard({ citation }: { citation: Citation }) {
  async function openSource() {
    try {
      const { url } = await getAccessUrl(citation.documentId)
      window.open(url, '_blank', 'noopener,noreferrer')
    } catch {
      // No toast system yet — silently ignored, matching SearchResultCard's existing behavior.
    }
  }

  const Icon = CATEGORY_ICON[citation.mediaCategory]

  return (
    <button
      onClick={openSource}
      className="flex w-full items-start gap-4 rounded-[var(--radius-lg)] border border-border bg-surface-raised p-4 text-left transition-colors hover:border-border-strong"
    >
      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-[var(--radius-md)] bg-surface-muted">
        <Icon className="size-5 text-text-muted" aria-hidden="true" />
      </div>

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-text-primary">{citation.fileName}</p>
        <p className="mt-1 line-clamp-2 text-sm text-text-secondary">{citation.snippet}</p>
        <div className="mt-2 flex items-center gap-2 text-xs text-text-muted">
          <span className="capitalize">{citation.mediaCategory.toLowerCase()}</span>
          {citation.mediaTimestamp && (
            <>
              <span aria-hidden="true">·</span>
              <span>
                {formatTimestamp(citation.mediaTimestamp.startMs)} – {formatTimestamp(citation.mediaTimestamp.endMs)}
              </span>
            </>
          )}
        </div>
      </div>

      <span className="shrink-0 self-center rounded-[var(--radius-md)] border border-border px-3 py-1.5 text-xs font-medium text-text-secondary">
        Open
      </span>
    </button>
  )
}
