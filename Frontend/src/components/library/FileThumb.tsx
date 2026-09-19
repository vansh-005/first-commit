import { getAccessUrl } from '@/api/client'
import { useIntersectionOnce } from '@/hooks/useIntersectionOnce'
import { fileExtension, previewKind, type PreviewKind } from '@/lib/fileTypes'
import type { MediaCategory } from '@/types/document'
import { File as FileIcon, FileText, Image as ImageIcon, Music, Play, Table2, Video } from 'lucide-react'
import { useRef, useState } from 'react'

/** xs: ~28px icon-only · sm: ~56-72px compact · md: card-sized hero tile. */
export type FileThumbSize = 'xs' | 'sm' | 'md'

interface FileThumbProps {
  documentId: string
  fileName: string
  mediaCategory: MediaCategory
  size?: FileThumbSize
  /** Sizing/rounding come from the parent; the thumb fills its container. */
  className?: string
}

const KIND_ICON: Record<PreviewKind, typeof FileText> = {
  image: ImageIcon,
  pdf: FileText,
  doc: FileText,
  sheet: Table2,
  audio: Music,
  video: Video,
  other: FileIcon,
}

/** Deterministic pseudo-waveform so each audio file gets its own stable-looking bars. */
function waveHeights(seed: string, count: number): number[] {
  let hash = 0
  for (const char of seed) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  return Array.from({ length: count }, (_, index) => {
    hash = (hash * 1664525 + 1013904223 + index) >>> 0
    return 22 + (hash % 68)
  })
}

function Lines({ widths }: { widths: string[] }) {
  return (
    <>
      {widths.map((width, index) => (
        <span key={index} className="h-1.5 shrink-0 rounded-full bg-border-strong/70" style={{ width }} />
      ))}
    </>
  )
}

/** The designed placeholder for a file type — used until (or instead of) a real image thumbnail. */
function TypePlaceholder({ kind, size, extension, seed }: { kind: PreviewKind; size: FileThumbSize; extension: string; seed: string }) {
  const Icon = KIND_ICON[kind]

  if (size === 'xs') {
    return <Icon className="size-3.5 text-text-muted" aria-hidden="true" />
  }
  const compact = size === 'sm'

  if (kind === 'audio') {
    return (
      <div aria-hidden="true" className="flex h-full w-full flex-col items-center justify-center gap-1 bg-accent-subtle px-2">
        <div className="flex h-[55%] items-center gap-[3px]">
          {waveHeights(seed, compact ? 9 : 18).map((height, index) => (
            <span key={index} className="w-[3px] rounded-full bg-accent/80" style={{ height: `${height}%` }} />
          ))}
        </div>
        {!compact && <Music className="size-3.5 text-accent-text" />}
      </div>
    )
  }

  if (kind === 'video') {
    return (
      <div aria-hidden="true" className="relative flex h-full w-full items-center justify-center bg-[#12131a]">
        {!compact && (
          <>
            <span className="absolute inset-x-0 top-0 flex h-2 justify-between px-1 opacity-30">
              {Array.from({ length: 9 }, (_, index) => (
                <span key={index} className="mt-0.5 h-1 w-1.5 rounded-[1px] bg-white" />
              ))}
            </span>
            <span className="absolute inset-x-3 bottom-2.5 h-0.5 rounded-full bg-white/15">
              <span className="block h-full w-1/3 rounded-full bg-accent" />
            </span>
          </>
        )}
        <span className={`flex items-center justify-center rounded-full bg-white/15 text-white ring-1 ring-white/25 ${compact ? 'size-7' : 'size-10'}`}>
          <Play className={`translate-x-px fill-current ${compact ? 'size-3' : 'size-4'}`} />
        </span>
      </div>
    )
  }

  if (kind === 'sheet') {
    return (
      <div aria-hidden="true" className="flex h-full w-full flex-col bg-surface-raised p-2">
        <div className="grid flex-1 grid-cols-3 gap-px overflow-hidden rounded-[3px] bg-border-strong/50">
          {Array.from({ length: compact ? 9 : 15 }, (_, index) => (
            <span key={index} className={index < 3 ? 'bg-accent-subtle' : 'bg-surface-raised'} />
          ))}
        </div>
        {extension && <span className="mt-1.5 w-fit text-[9px] font-semibold tracking-wide text-accent-text">{extension}</span>}
      </div>
    )
  }

  if (kind === 'pdf' || kind === 'doc' || kind === 'other') {
    // A little page: folded corner, text lines, and the file-type label.
    return (
      <div aria-hidden="true" className="relative flex h-full w-full items-center justify-center bg-surface-muted p-2">
        <div className="relative flex h-full max-h-full w-[68%] max-w-[120px] flex-col gap-1.5 overflow-hidden rounded-[3px] border border-border-strong/60 bg-surface-raised p-2 shadow-[var(--shadow-sm)]">
          <span className="absolute right-0 top-0 size-3 border-b border-l border-border-strong/60 bg-surface-muted [clip-path:polygon(0_0,100%_100%,0_100%)]" />
          {!compact && <Lines widths={['70%', '100%', '100%', '85%', '60%']} />}
          {compact && <Icon className="size-3.5 text-text-muted" />}
          {extension && (
            <span
              className={`mt-auto w-fit rounded px-1 py-px text-[9px] font-bold tracking-wide ${
                kind === 'pdf' ? 'bg-accent text-white' : 'bg-accent-subtle text-accent-text'
              }`}
            >
              {extension}
            </span>
          )}
        </div>
      </div>
    )
  }

  return <Icon className="size-6 text-text-muted" aria-hidden="true" />
}

/**
 * The one type-specific file preview used by Home, Library, Search and Ask. Images load a real
 * thumbnail through a freshly signed access URL, fetched lazily once the tile scrolls into view (the
 * bucket stays private — only a short-lived URL ever reaches the browser). Everything else, and any
 * image that fails to load, gets a designed per-type placeholder (PDF page, document, spreadsheet,
 * audio waveform, video frame) instead of a generic grey box.
 */
export function FileThumb({ documentId, fileName, mediaCategory, size = 'md', className = '' }: FileThumbProps) {
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

  const kind = previewKind(fileName, mediaCategory)

  return (
    <div ref={ref} className={`relative flex h-full w-full items-center justify-center overflow-hidden bg-surface-muted ${className}`}>
      {mediaCategory === 'IMAGE' && url && !failed ? (
        <img src={url} alt={fileName} className="h-full w-full object-cover" onError={handleImageError} />
      ) : (
        <TypePlaceholder kind={kind} size={size} extension={fileExtension(fileName)} seed={documentId} />
      )}
    </div>
  )
}
