import { getAccessUrl } from '@/api/client'
import { useIntersectionOnce } from '@/hooks/useIntersectionOnce'
import type { SearchResult } from '@/types/document'
import { FileText, Image as ImageIcon, Music, Video } from 'lucide-react'
import { useState } from 'react'

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

/** Docs/FRONTEND.md §15/§17. Deliberately does not render `match.score` — the API returns it
 * for ranking/debugging only, never as a user-facing confidence percentage. Opens the
 * original file through a freshly-signed /access-url, exactly like the Library's FileCard. */
export function SearchResultCard({ result }: { result: SearchResult }) {
  const { document, match } = result
  const [thumbnailUrl, setThumbnailUrl] = useState<string | null>(null)
  const [thumbnailFailed, setThumbnailFailed] = useState(false)

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
      // No toast system yet — silently ignored, matching FileCard's existing behavior.
    }
  }

  const showThumbnail = document.mediaCategory === 'IMAGE' && thumbnailUrl && !thumbnailFailed
  const Icon = CATEGORY_ICON[document.mediaCategory]

  return (
    <button
      onClick={openFile}
      className="flex w-full items-start gap-4 rounded-[var(--radius-lg)] border border-border bg-surface-raised p-4 text-left transition-colors hover:border-border-strong"
    >
      <div
        ref={thumbnailRef}
        className="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-[var(--radius-md)] bg-surface-muted"
      >
        {showThumbnail ? (
          <img
            src={thumbnailUrl}
            alt={document.fileName}
            className="h-full w-full object-cover"
            onError={() => setThumbnailFailed(true)}
          />
        ) : (
          <Icon className="size-6 text-text-muted" aria-hidden="true" />
        )}
      </div>

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-text-primary">{document.fileName}</p>
        <p className="mt-1 line-clamp-2 text-sm text-text-secondary">{match.snippet}</p>
        <div className="mt-2 flex items-center gap-2 text-xs text-text-muted">
          <span className="capitalize">{document.mediaCategory.toLowerCase()}</span>
          {match.mediaTimestamp && (
            <>
              <span aria-hidden="true">·</span>
              <span>
                {formatTimestamp(match.mediaTimestamp.startMs)} – {formatTimestamp(match.mediaTimestamp.endMs)}
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
