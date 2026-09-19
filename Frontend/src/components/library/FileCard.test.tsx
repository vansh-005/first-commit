import { getAccessUrl } from '@/api/client'
import type { DocumentSummary } from '@/types/document'
import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { FileCard } from './FileCard'

vi.mock('@/api/client', () => ({
  getAccessUrl: vi.fn(),
}))

let intersectionCallback: (entries: Partial<IntersectionObserverEntry>[]) => void = () => {}

beforeEach(() => {
  vi.clearAllMocks()
  class MockIntersectionObserver {
    constructor(callback: (entries: Partial<IntersectionObserverEntry>[]) => void) {
      intersectionCallback = callback
    }
    observe = vi.fn()
    disconnect = vi.fn()
    unobserve = vi.fn()
  }
  // @ts-expect-error - minimal stub sufficient for these tests, not jsdom's real type
  global.IntersectionObserver = MockIntersectionObserver
})

function makeDocument(overrides: Partial<DocumentSummary> = {}): DocumentSummary {
  return {
    documentId: 'doc-1',
    fileName: 'photo.png',
    mediaCategory: 'IMAGE',
    mimeType: 'image/png',
    sizeBytes: 123,
    status: 'READY',
    createdAt: '2026-09-18T00:00:00Z',
    uploadedAt: null,
    updatedAt: '2026-09-18T00:00:00Z',
    failureReason: null,
    ...overrides,
  }
}

describe('FileCard', () => {
  it('does not fetch a thumbnail until the card actually scrolls into view', () => {
    render(<FileCard document={makeDocument()} />)
    expect(getAccessUrl).not.toHaveBeenCalled()
  })

  it('fetches and renders a thumbnail for IMAGE documents once visible', async () => {
    vi.mocked(getAccessUrl).mockResolvedValue({
      documentId: 'doc-1',
      url: 'https://signed.example/photo.png',
      expiresAt: '2026-09-18T01:00:00Z',
    })
    render(<FileCard document={makeDocument()} />)

    intersectionCallback([{ isIntersecting: true } as IntersectionObserverEntry])

    await waitFor(() => expect(getAccessUrl).toHaveBeenCalledWith('doc-1'))
    const img = await screen.findByRole('img', { name: 'photo.png' })
    expect(img).toHaveAttribute('src', 'https://signed.example/photo.png')
  })

  it('does not fetch a thumbnail for non-image documents even when visible', () => {
    render(<FileCard document={makeDocument({ mediaCategory: 'DOCUMENT', fileName: 'doc.pdf' })} />)
    intersectionCallback([{ isIntersecting: true } as IntersectionObserverEntry])
    expect(getAccessUrl).not.toHaveBeenCalled()
  })

  it('offers Download as a separate control from the card\'s Open action', () => {
    render(<FileCard document={makeDocument()} />)
    expect(screen.getByRole('button', { name: 'Download photo.png' })).toBeInTheDocument()
    // Two sibling buttons (Open on the card, Download beside it) — never nested.
    expect(screen.getAllByRole('button')).toHaveLength(2)
  })

  it('shows "Awaiting processing" for a persisted UPLOAD_PENDING document, not "Uploading"', () => {
    render(<FileCard document={makeDocument({ status: 'UPLOAD_PENDING' })} />)
    expect(screen.getByText('Awaiting processing')).toBeInTheDocument()
    expect(screen.queryByText('Uploading')).not.toBeInTheDocument()
  })
})
