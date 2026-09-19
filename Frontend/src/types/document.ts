export type MediaCategory = 'IMAGE' | 'VIDEO' | 'AUDIO' | 'DOCUMENT' | 'OTHER'

export type DocumentStatus = 'UPLOAD_PENDING' | 'UPLOADED' | 'INDEXING' | 'READY' | 'FAILED'

export interface DocumentSummary {
  documentId: string
  fileName: string
  mediaCategory: MediaCategory
  mimeType: string
  sizeBytes: number
  status: DocumentStatus
  createdAt: string
  uploadedAt: string | null
  updatedAt: string
  failureReason: string | null
}

export interface DocumentsListResponse {
  items: DocumentSummary[]
  nextCursor: string | null
}

export interface UploadFileRequest {
  clientFileId: string
  fileName: string
  contentType: string
  sizeBytes: number
}

export interface UploadInstructions {
  method: string
  url: string
  headers: Record<string, string>
  expiresAt: string
}

export interface UploadResult {
  clientFileId: string
  documentId: string
  status: DocumentStatus
  upload: UploadInstructions
}

export interface UploadResponse {
  uploads: UploadResult[]
}

export interface AccessUrlResponse {
  documentId: string
  url: string
  expiresAt: string
}

/** Docs/API.md §18-19. */
export interface SearchRequest {
  query: string
  limit?: number
  filters?: {
    mediaCategories?: MediaCategory[]
  }
}

export interface MediaTimestamp {
  startMs: number
  endMs: number
}

export interface SearchResultDocument {
  documentId: string
  fileName: string
  mediaCategory: MediaCategory
  mimeType: string
}

export interface SearchResultMatch {
  score: number
  snippet: string
  mediaTimestamp: MediaTimestamp | null
}

export interface SearchResult {
  document: SearchResultDocument
  match: SearchResultMatch
}

export interface SearchResponse {
  query: string
  results: SearchResult[]
}

/** Docs/API.md §20. `sessionId` is an opaque, application-issued identifier previously
 * returned from a prior /ask call — never a raw Bedrock session ID. Omit it to start a new
 * conversation. */
export interface AskRequest {
  question: string
  sessionId?: string
}

/** Deliberately has no `accessUrl` — resolve a clickable source through the existing
 * `/documents/{id}/access-url`, the same way search results already do. */
export interface Citation {
  citationId: string
  documentId: string
  fileName: string
  mediaCategory: MediaCategory
  mimeType: string
  snippet: string
  mediaTimestamp: MediaTimestamp | null
}

export interface AskResponse {
  answer: string
  sessionId: string
  citations: Citation[]
}
