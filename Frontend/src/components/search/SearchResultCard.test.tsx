import { getAccessUrl } from '@/api/client'
import type { SearchResult } from '@/types/document'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SearchResultCard } from './SearchResultCard'

vi.mock('@/api/client', () => ({
  getAccessUrl: vi.fn(),
}))

beforeEach(() => {
  vi.clearAllMocks()
  class MockIntersectionObserver {
    observe = vi.fn()
    disconnect = vi.fn()
    unobserve = vi.fn()
  }
  // @ts-expect-error - minimal stub sufficient for these tests, not jsdom's real type
  global.IntersectionObserver = MockIntersectionObserver
  vi.spyOn(window, 'open').mockImplementation(() => null)
})

function makeResult(overrides: Partial<SearchResult> = {}): SearchResult {
  return {
    document: {
      documentId: 'doc-1',
      fileName: 'Lecture.mp4',
      mediaCategory: 'VIDEO',
      mimeType: 'video/mp4',
    },
    match: {
      score: 0.91,
      snippet: 'Discussion of AWS promotional credits begins here.',
      mediaTimestamp: null,
    },
    ...overrides,
  }
}

describe('SearchResultCard', () => {
  it('fetches a fresh access URL and opens it when clicked', async () => {
    vi.mocked(getAccessUrl).mockResolvedValue({
      documentId: 'doc-1',
      url: 'https://signed.example/Lecture.mp4',
      expiresAt: '2026-09-18T01:00:00Z',
    })

    render(<SearchResultCard result={makeResult()} />)
    await userEvent.click(screen.getByRole('button', { name: /^Lecture\.mp4/ }))

    await waitFor(() => expect(getAccessUrl).toHaveBeenCalledWith('doc-1'))
    expect(window.open).toHaveBeenCalledWith('https://signed.example/Lecture.mp4', '_blank', 'noopener,noreferrer')
  })

  it('renders a formatted media timestamp when present', () => {
    render(<SearchResultCard result={makeResult({ match: { score: 0.91, snippet: 'clip', mediaTimestamp: { startMs: 125000, endMs: 141000 } } })} />)
    expect(screen.getByText('2:05 – 2:21')).toBeInTheDocument()
  })

  it('does not render a raw numeric score', () => {
    render(<SearchResultCard result={makeResult()} />)
    expect(screen.queryByText(/0\.91/)).not.toBeInTheDocument()
  })

  it('downloads through a fresh access URL under the real filename, without opening a tab', async () => {
    vi.mocked(getAccessUrl).mockResolvedValue({ documentId: 'doc-1', url: 'https://signed.example/file', expiresAt: '2026-09-18T01:00:00Z' })
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, blob: async () => new Blob(['x']) })
    vi.stubGlobal('fetch', fetchMock)
    URL.createObjectURL = vi.fn(() => 'blob:x')
    URL.revokeObjectURL = vi.fn()
    let savedAs: string | undefined
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      savedAs = this.download
    })

    render(<SearchResultCard result={makeResult()} />)
    await userEvent.click(screen.getByRole('button', { name: 'Download Lecture.mp4' }))

    await waitFor(() => expect(savedAs).toBe('Lecture.mp4'))
    expect(getAccessUrl).toHaveBeenCalledWith('doc-1')
    // Presigned URL only — no Authorization header is ever sent to S3.
    expect(fetchMock).toHaveBeenCalledWith('https://signed.example/file')
    expect(window.open).not.toHaveBeenCalled()
    vi.unstubAllGlobals()
  })

  it('shows an inline error when the download fails', async () => {
    vi.mocked(getAccessUrl).mockRejectedValue(new Error('nope'))
    render(<SearchResultCard result={makeResult()} />)
    await userEvent.click(screen.getByRole('button', { name: 'Download Lecture.mp4' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/couldn’t download this file/i)
  })
})
