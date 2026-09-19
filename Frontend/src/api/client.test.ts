import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/auth/userManager', () => ({
  userManager: {
    getUser: vi.fn().mockResolvedValue({ access_token: 'test-token' }),
  },
}))

import { listDocuments, searchDocuments } from './client'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('withRetry (via listDocuments/searchDocuments)', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    vi.useFakeTimers()
  })

  it('retries on 429 and succeeds once the underlying call recovers', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock
      .mockResolvedValueOnce(jsonResponse(429, { error: { code: 'RATE_LIMITED', message: 'busy' } }))
      .mockResolvedValueOnce(jsonResponse(200, { documents: [], nextCursor: null }))

    const resultPromise = listDocuments()
    await vi.runAllTimersAsync()
    const result = await resultPromise

    expect(result).toEqual({ documents: [], nextCursor: null })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('gives up after 3 attempts and throws the last 429 error', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockResolvedValue(jsonResponse(429, { error: { code: 'RATE_LIMITED', message: 'busy' } }))

    const resultPromise = searchDocuments({ query: 'x' })
    resultPromise.catch(() => {})
    await vi.runAllTimersAsync()

    await expect(resultPromise).rejects.toMatchObject({ status: 429 })
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it('does not retry a non-retryable error like 404', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockResolvedValue(jsonResponse(404, { error: { code: 'NOT_FOUND', message: 'missing' } }))

    const resultPromise = listDocuments()
    resultPromise.catch(() => {})
    await vi.runAllTimersAsync()

    await expect(resultPromise).rejects.toMatchObject({ status: 404 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
