import { listDocuments } from '@/api/client'
import type { DocumentStatus, DocumentSummary } from '@/types/document'
import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useDocuments } from './useDocuments'

vi.mock('@/api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/client')>()
  return { ...actual, listDocuments: vi.fn() }
})

function doc(status: DocumentStatus, id = 'doc-1'): DocumentSummary {
  return {
    documentId: id,
    fileName: `${id}.pdf`,
    mediaCategory: 'DOCUMENT',
    mimeType: 'application/pdf',
    sizeBytes: 10,
    status,
    createdAt: '2026-09-18T00:00:00Z',
    uploadedAt: null,
    updatedAt: '2026-09-18T00:00:00Z',
    failureReason: null,
  }
}

function setHidden(hidden: boolean) {
  Object.defineProperty(document, 'hidden', { value: hidden, configurable: true })
}

async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms)
  })
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.mocked(listDocuments).mockReset()
  setHidden(false)
})

afterEach(() => {
  vi.useRealTimers()
  setHidden(false)
})

describe('useDocuments polling', () => {
  it('polls about every 5s while a document is processing, then stops once everything is settled', async () => {
    vi.mocked(listDocuments)
      .mockResolvedValueOnce({ items: [doc('INDEXING')], nextCursor: null })
      .mockResolvedValueOnce({ items: [doc('INDEXING')], nextCursor: null })
      .mockResolvedValue({ items: [doc('READY')], nextCursor: null })

    const { result } = renderHook(() => useDocuments())
    await advance(0)
    expect(listDocuments).toHaveBeenCalledTimes(1)
    expect(result.current.processing).toBe(true)

    await advance(5_000)
    expect(listDocuments).toHaveBeenCalledTimes(2)
    await advance(5_000)
    expect(listDocuments).toHaveBeenCalledTimes(3)
    expect(result.current.items[0].status).toBe('READY')
    expect(result.current.processing).toBe(false)

    await advance(60_000)
    expect(listDocuments).toHaveBeenCalledTimes(3)
  })

  it('never polls when every document is already terminal', async () => {
    vi.mocked(listDocuments).mockResolvedValue({ items: [doc('READY'), doc('FAILED', 'doc-2')], nextCursor: null })

    renderHook(() => useDocuments())
    await advance(60_000)

    expect(listDocuments).toHaveBeenCalledTimes(1)
  })

  it('makes no requests while the tab is hidden and catches up when it becomes visible', async () => {
    vi.mocked(listDocuments).mockResolvedValue({ items: [doc('INDEXING')], nextCursor: null })

    renderHook(() => useDocuments())
    await advance(0)
    expect(listDocuments).toHaveBeenCalledTimes(1)

    setHidden(true)
    await advance(30_000)
    expect(listDocuments).toHaveBeenCalledTimes(1)

    setHidden(false)
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'))
      await vi.advanceTimersByTimeAsync(0)
    })
    expect(listDocuments).toHaveBeenCalledTimes(2)
  })

  it('appends the next page with loadMore and surfaces a friendly error if the first load fails', async () => {
    vi.mocked(listDocuments)
      .mockResolvedValueOnce({ items: [doc('READY', 'a')], nextCursor: 'cursor-1' })
      .mockResolvedValueOnce({ items: [{ ...doc('READY', 'b'), createdAt: '2026-09-17T00:00:00Z' }], nextCursor: null })

    const { result } = renderHook(() => useDocuments())
    await advance(0)
    expect(result.current.hasMore).toBe(true)

    await act(async () => {
      await result.current.loadMore()
    })
    expect(result.current.items.map((item) => item.documentId)).toEqual(['a', 'b'])
    expect(result.current.hasMore).toBe(false)
    expect(listDocuments).toHaveBeenLastCalledWith({ limit: 24, category: undefined, cursor: 'cursor-1' })

    vi.mocked(listDocuments).mockRejectedValue(new Error('Bedrock exploded'))
    const failing = renderHook(() => useDocuments())
    await advance(0)
    expect(failing.result.current.error).toBe('We couldn’t load your library.')
  })
})
