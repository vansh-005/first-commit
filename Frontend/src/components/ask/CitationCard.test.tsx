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
    await userEvent.click(screen.getByRole('button', { name: /internship-offer\.pdf/ }))

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

  it('renders no timestamp text when absent', () => {
    render(<CitationCard citation={makeCitation()} />)
    expect(screen.queryByText(/–/)).not.toBeInTheDocument()
  })
})
