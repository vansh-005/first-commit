import type { MediaCategory } from '@/types/document'
import { FileText, Image as ImageIcon, Music, Video } from 'lucide-react'

export const CATEGORY_ICON = {
  IMAGE: ImageIcon,
  VIDEO: Video,
  AUDIO: Music,
  DOCUMENT: FileText,
  OTHER: FileText,
} as const

export const CATEGORY_LABEL: Record<MediaCategory, string> = {
  IMAGE: 'Photo',
  VIDEO: 'Video',
  AUDIO: 'Audio',
  DOCUMENT: 'Document',
  OTHER: 'File',
}

// Formats the indexing pipeline is known to handle well. Anything else is still saved, but may
// not become searchable — so we say so up front instead of letting it fail silently later.
const LIKELY_SEARCHABLE = new Set([
  'pdf', 'txt', 'md', 'markdown', 'html', 'htm', 'csv', 'doc', 'docx', 'xls', 'xlsx',
  'png', 'jpg', 'jpeg',
  'mp3', 'wav', 'm4a', 'flac', 'ogg', 'amr',
  'mp4', 'mov', 'mkv', 'webm',
])

export function isLikelySearchable(fileName: string): boolean {
  const dot = fileName.lastIndexOf('.')
  return dot > 0 && LIKELY_SEARCHABLE.has(fileName.slice(dot + 1).toLowerCase())
}

export function fileExtension(fileName: string): string {
  const dot = fileName.lastIndexOf('.')
  return dot > 0 && dot < fileName.length - 1 ? fileName.slice(dot + 1, dot + 5).toUpperCase() : ''
}

export function formatTimestamp(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

export type PreviewKind = 'image' | 'pdf' | 'doc' | 'sheet' | 'audio' | 'video' | 'other'

/** Which designed preview a file gets: by media category, refined by extension for documents. */
export function previewKind(fileName: string, mediaCategory: MediaCategory): PreviewKind {
  if (mediaCategory === 'IMAGE') return 'image'
  if (mediaCategory === 'AUDIO') return 'audio'
  if (mediaCategory === 'VIDEO') return 'video'
  const extension = fileExtension(fileName)
  if (extension === 'PDF') return 'pdf'
  if (extension === 'CSV' || extension === 'XLS' || extension === 'XLSX') return 'sheet'
  return mediaCategory === 'DOCUMENT' ? 'doc' : 'other'
}
