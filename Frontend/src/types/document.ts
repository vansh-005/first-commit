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
