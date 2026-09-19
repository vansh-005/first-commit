import { getAccessUrl } from '@/api/client'
import { useIntersectionOnce } from '@/hooks/useIntersectionOnce'
import { CATEGORY_ICON, fileExtension } from '@/lib/fileTypes'
import type { MediaCategory } from '@/types/document'
import { Play } from 'lucide-react'
import { useRef, useState } from 'react'

interface FileThumbProps {
  documentId: string
  fileName: string
  mediaCategory: MediaCategory
  /** Sizing/rounding come from the parent; the thumb fills its container. */
  className?: string
}

/** Deterministic pseudo-waveform so each audio file gets its own stable-looking bars. */
function waveHeights(seed: string): number[] {
  let hash = 0
  for (const char of seed) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  return Array.from({ length: 16 }, (_, index) => {
    hash = (hash * 1664525 + 1013904223 + index) >>> 0
    return 18 + (hash % 64)
  })
}

/**
 * Type-specific preview used by Library, Search and Ask. Images load a real thumbnail through a
 * freshly signed access URL, fetched lazily only once the tile scrolls into view (the bucket stays
 * private — only a short-lived URL ever reaches the browser). Everything else, and any image that
 * fails to load, gets a designed placeholder instead of a generic grey box.
 */
export function FileThumb({ documentId, fileName, mediaCategory, className = '' }: FileThumbProps) {
  const [url, setUrl] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)
  const retriedRef = useRef(false)

  const ref = useIntersectionOnce<HTMLDivElement>(() => {
    if (mediaCategory !== 'IMAGE') return
    getAccessUrl(documentId)
      .then((response) => setUrl(response.url))
      .catch(() => setFailed(true))
  })

  // A signed URL that has expired mid-session shows up as an image error: fetch a fresh one once,
  // then fall back to the placeholder rather than a broken image.
  function handleImageError() {
    if (retriedRef.current) {
      setFailed(true)
      return
    }
    retriedRef.current = true
    getAccessUrl(documentId)
      .then((response) => setUrl(response.url))
      .catch(() => setFailed(true))
  }

  const Icon = CATEGORY_ICON[mediaCategory]
  const extension = fileExtension(fileName)

  return (
    <div ref={ref} className={`relative flex h-full w-full items-center justify-center overflow-hidden bg-surface-muted ${className}`}>
      {mediaCategory === 'IMAGE' && url && !failed ? (
        <img src={url} alt={fileName} className="h-full w-full object-cover" onError={handleImageError} />
      ) : mediaCategory === 'AUDIO' ? (
        <div aria-hidden="true" className="flex h-full w-full items-center justify-center gap-[3px] bg-accent-subtle px-3">
          {waveHeights(documentId).map((height, index) => (
            <span key={index} className="w-[3px] rounded-full bg-accent/80" style={{ height: `${height}%`, maxHeight: '70%' }} />
          ))}
        </div>
      ) : mediaCategory === 'VIDEO' ? (
        <div aria-hidden="true" className="flex h-full w-full items-center justify-center bg-[#12131a]">
          <span className="flex size-9 items-center justify-center rounded-full bg-white/15 text-white ring-1 ring-white/20">
            <Play className="size-4 translate-x-px fill-current" />
          </span>
        </div>
      ) : mediaCategory === 'DOCUMENT' || mediaCategory === 'OTHER' ? (
        <div aria-hidden="true" className="flex h-full w-full flex-col gap-1.5 bg-surface-raised p-3">
          <span className="h-1.5 w-2/3 rounded-full bg-border-strong" />
          <span className="h-1.5 w-full rounded-full bg-border" />
          <span className="h-1.5 w-full rounded-full bg-border" />
          <span className="h-1.5 w-5/6 rounded-full bg-border" />
          {extension && (
            <span className="mt-auto w-fit rounded bg-accent-subtle px-1.5 py-0.5 text-[10px] font-semibold tracking-wide text-accent-text">
              {extension}
            </span>
          )}
        </div>
      ) : (
        <Icon className="size-6 text-text-muted" aria-hidden="true" />
      )}
    </div>
  )
}
