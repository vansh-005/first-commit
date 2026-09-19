import { getAccessUrl } from '@/api/client'
import type { Citation } from '@/types/document'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CitationCard } from './CitationCard'

vi.mock('@/api/client', () => ({
  getAccessUrl: vi.fn(),
}))

beforeEach(() => {
  vi.clearAllMocks()
  vi.spyOn(window, 'open').mockImplementation(() => null)
})

function makeCitation(overrides: Partial<Citation> = {}): Citation {
  return {
    citationId: 'c1',
    documentId: 'doc-1',
    fileName: 'internship-offer.pdf',
    mediaCategory: 'DOCUMENT',
    mimeType: 'application/pdf',
    snippet: 'Relocation expenses are covered up to $2,000.',
    mediaTimestamp: null,
    ...overrides,
  }
}

describe('CitationCard', () => {
  it('fetches a fresh access URL and opens it when clicked', async () => {
    vi.mocked(getAccessUrl).mockResolvedValue({
      documentId: 'doc-1',
      url: 'https://signed.example/internship-offer.pdf',
      expiresAt: '2026-09-18T01:00:00Z',
    })

    render(<CitationCard citation={makeCitation()} />)
    await userEvent.click(screen.getByRole('button', { name: /^internship-offer\.pdf/ }))

    await waitFor(() => expect(getAccessUrl).toHaveBeenCalledWith('doc-1'))
    expect(window.open).toHaveBeenCalledWith(
      'https://signed.example/internship-offer.pdf',
      '_blank',
      'noopener,noreferrer',
    )
  })

  it('renders a formatted media timestamp when present', () => {
    render(
      <CitationCard
        citation={makeCitation({ mediaCategory: 'VIDEO', mediaTimestamp: { startMs: 125000, endMs: 141000 } })}
      />,
    )
    expect(screen.getByText('2:05 – 2:21')).toBeInTheDocument()
  })

  it('cleans raw retrieval markup out of the snippet', () => {
    render(<CitationCard citation={makeCitation({ snippet: '# Offer\n<figure><figcaption>Signed page</figcaption></figure>\n**Relocation** is covered.' })} />)
    expect(screen.getByText('“Offer Signed page Relocation is covered.”')).toBeInTheDocument()
  })

  it('renders no timestamp text when absent', () => {
    render(<CitationCard citation={makeCitation()} />)
    expect(screen.queryByText(/–/)).not.toBeInTheDocument()
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

    render(<CitationCard citation={makeCitation()} />)
    await userEvent.click(screen.getByRole('button', { name: 'Download internship-offer.pdf' }))

    await waitFor(() => expect(savedAs).toBe('internship-offer.pdf'))
    expect(getAccessUrl).toHaveBeenCalledWith('doc-1')
    // Presigned URL only — no Authorization header is ever sent to S3.
    expect(fetchMock).toHaveBeenCalledWith('https://signed.example/file')
    expect(window.open).not.toHaveBeenCalled()
    vi.unstubAllGlobals()
  })

  it('shows an inline error when the download fails', async () => {
    vi.mocked(getAccessUrl).mockRejectedValue(new Error('nope'))
    render(<CitationCard citation={makeCitation()} />)
    await userEvent.click(screen.getByRole('button', { name: 'Download internship-offer.pdf' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/couldn’t download this file/i)
  })
})
