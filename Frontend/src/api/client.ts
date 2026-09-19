import { userManager } from '@/auth/userManager'
import type {
  AccessUrlResponse,
  AskRequest,
  AskResponse,
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

/** Carries the HTTP status and Docs/API.md §6 machine-readable error code (when the response
 * body parsed as that envelope) — callers that need to react to a specific failure (e.g.
 * Ask's ASK_SESSION_EXPIRED) can check `.code`/`.status` instead of string-matching `.message`. */
export class ApiError extends Error {
  status: number
  code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

/**
 * Retries `fn` with exponential backoff (500ms, 1000ms, 2000ms) when it throws an `ApiError`
 * with status 429 or 503 — the two "healthy, just temporarily busy" cases (Docs/API.md's
 * RATE_LIMITED and Bedrock upstream throttling). Any other error, or a 429/503 that still fails
 * after 3 attempts, propagates immediately. Only used for calls that are safe to repeat
 * (idempotent reads and search) — never wrapped around uploads or `/ask`, since a retried
 * `/ask` could start a second Bedrock session server-side.
 */
async function withRetry<T>(fn: () => Promise<T>, maxAttempts = 3): Promise<T> {
  const retryableStatuses = [429, 503]
  const baseDelayMs = 500
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      return await fn()
    } catch (error) {
      const isRetryable = error instanceof ApiError && retryableStatuses.includes(error.status)
      if (!isRetryable || attempt === maxAttempts) {
        throw error
      }
      await new Promise((resolve) => setTimeout(resolve, baseDelayMs * 2 ** (attempt - 1)))
    }
  }
  // Unreachable: the loop always either returns or throws.
  throw new Error('withRetry: exhausted attempts without returning or throwing')
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
    const fallbackMessage = `${path} failed with status ${response.status}`
    try {
      const body = (await response.json()) as { error?: { code?: string; message?: string } }
      throw new ApiError(body.error?.message ?? fallbackMessage, response.status, body.error?.code)
    } catch (parseError) {
      if (parseError instanceof ApiError) throw parseError
      throw new ApiError(fallbackMessage, response.status)
    }
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

/** Docs/API.md §15. Retried with backoff on 429/503 — a safe, idempotent read. */
export async function listDocuments(params: ListDocumentsParams = {}): Promise<DocumentsListResponse> {
  const query = new URLSearchParams()
  if (params.limit) query.set('limit', String(params.limit))
  if (params.cursor) query.set('cursor', params.cursor)
  if (params.category) query.set('category', params.category)
  if (params.status) query.set('status', params.status)
  const queryString = query.toString()

  return withRetry(async () => {
    const response = await authorizedFetch(`/api/v1/documents${queryString ? `?${queryString}` : ''}`)
    return (await response.json()) as DocumentsListResponse
  })
}

/** Docs/API.md §16. Retried with backoff on 429/503 — a safe, idempotent read. */
export async function getDocument(documentId: string): Promise<DocumentSummary> {
  return withRetry(async () => {
    const response = await authorizedFetch(`/api/v1/documents/${encodeURIComponent(documentId)}`)
    return (await response.json()) as DocumentSummary
  })
}

/** Docs/API.md §17. Retried with backoff on 429/503 — a safe, idempotent read. */
export async function getAccessUrl(documentId: string): Promise<AccessUrlResponse> {
  return withRetry(async () => {
    const response = await authorizedFetch(`/api/v1/documents/${encodeURIComponent(documentId)}/access-url`)
    return (await response.json()) as AccessUrlResponse
  })
}

/** Docs/API.md §18. Uses Retrieve server-side — never RetrieveAndGenerate. Retried with
 * backoff on 429/503 — a search query has no side effects, so repeating it is safe. */
export async function searchDocuments(request: SearchRequest): Promise<SearchResponse> {
  return withRetry(async () => {
    const response = await authorizedFetch('/api/v1/search', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    })
    return (await response.json()) as SearchResponse
  })
}

/** Docs/API.md §20. Uses RetrieveAndGenerate. `sessionId`, when present, must be a value this
 * API previously returned — never invent one client-side. A 409 ASK_SESSION_EXPIRED means the
 * conversation is gone; callers should drop it and start a new one, not retry with the same ID. */
export async function askQuestion(request: AskRequest): Promise<AskResponse> {
  const response = await authorizedFetch('/api/v1/ask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  return (await response.json()) as AskResponse
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
