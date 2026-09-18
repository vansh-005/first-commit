import { userManager } from '@/auth/userManager'
import type {
  AccessUrlResponse,
  DocumentsListResponse,
  DocumentSummary,
  MediaCategory,
  DocumentStatus,
  SearchRequest,
  SearchResponse,
  UploadFileRequest,
  UploadResponse,
} from '@/types/document'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL as string | undefined

if (!API_BASE_URL) {
  // eslint-disable-next-line no-console
  console.warn('VITE_API_BASE_URL is not set; API calls will fail.')
}

async function authorizedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const user = await userManager.getUser()
  if (!user?.access_token) {
    throw new Error('Not authenticated')
  }
  const response = await fetch(`${API_BASE_URL ?? ''}${path}`, {
    ...init,
    headers: {
      ...init.headers,
      Authorization: `Bearer ${user.access_token}`,
    },
  })
  if (!response.ok) {
    throw new Error(`${path} failed with status ${response.status}`)
  }
  return response
}

export interface HealthResponse {
  status: string
  service: string
  version: string
}

export async function getHealth(): Promise<HealthResponse> {
  const response = await fetch(`${API_BASE_URL ?? ''}/api/v1/health`)
  if (!response.ok) {
    throw new Error(`Health check failed with status ${response.status}`)
  }
  return (await response.json()) as HealthResponse
}

export interface MeResponse {
  userId: string
}

/**
 * Calls the internal Phase 2 diagnostic route (see Docs/API.md) to prove the access token
 * is accepted end to end. Not a permanent product feature.
 */
export async function getMe(): Promise<MeResponse> {
  const response = await authorizedFetch('/api/v1/me')
  return (await response.json()) as MeResponse
}

/** Docs/API.md §10. Single-file and bulk upload share this one endpoint. */
export async function initUploads(files: UploadFileRequest[]): Promise<UploadResponse> {
  const response = await authorizedFetch('/api/v1/uploads', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ files }),
  })
  return (await response.json()) as UploadResponse
}

export interface ListDocumentsParams {
  limit?: number
  cursor?: string
  category?: MediaCategory
  status?: DocumentStatus
}

/** Docs/API.md §15. */
export async function listDocuments(params: ListDocumentsParams = {}): Promise<DocumentsListResponse> {
  const query = new URLSearchParams()
  if (params.limit) query.set('limit', String(params.limit))
  if (params.cursor) query.set('cursor', params.cursor)
  if (params.category) query.set('category', params.category)
  if (params.status) query.set('status', params.status)
  const queryString = query.toString()

  const response = await authorizedFetch(`/api/v1/documents${queryString ? `?${queryString}` : ''}`)
  return (await response.json()) as DocumentsListResponse
}

/** Docs/API.md §16. */
export async function getDocument(documentId: string): Promise<DocumentSummary> {
  const response = await authorizedFetch(`/api/v1/documents/${encodeURIComponent(documentId)}`)
  return (await response.json()) as DocumentSummary
}

/** Docs/API.md §17. */
export async function getAccessUrl(documentId: string): Promise<AccessUrlResponse> {
  const response = await authorizedFetch(`/api/v1/documents/${encodeURIComponent(documentId)}/access-url`)
  return (await response.json()) as AccessUrlResponse
}

/** Docs/API.md §18. Uses Retrieve server-side — never RetrieveAndGenerate. */
export async function searchDocuments(request: SearchRequest): Promise<SearchResponse> {
  const response = await authorizedFetch('/api/v1/search', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  return (await response.json()) as SearchResponse
}

/**
 * PUTs a single file's raw bytes directly to S3 using its presigned URL, reporting
 * upload progress. Uses XMLHttpRequest rather than fetch because fetch cannot report
 * upload (as opposed to download) progress.
 */
export function uploadFileToS3(
  url: string,
  headers: Record<string, string>,
  file: File,
  onProgress: (percent: number) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', url)
    Object.entries(headers).forEach(([name, value]) => xhr.setRequestHeader(name, value))

    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve()
      } else {
        reject(new Error(`Upload failed with status ${xhr.status}`))
      }
    }
    xhr.onerror = () => reject(new Error('Upload failed'))
    xhr.send(file)
  })
}
