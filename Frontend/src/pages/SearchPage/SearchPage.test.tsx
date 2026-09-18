import { searchDocuments } from '@/api/client'
import type { SearchResult } from '@/types/document'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SearchPage } from './SearchPage'

vi.mock('@/api/client', () => ({
  searchDocuments: vi.fn(),
  getAccessUrl: vi.fn(),
}))

function makeResult(overrides: Partial<SearchResult> = {}): SearchResult {
  return {
    document: {
      documentId: 'doc-1',
      fileName: 'Screenshot_20260903.png',
      mediaCategory: 'IMAGE',
      mimeType: 'image/png',
    },
    match: {
      score: 0.87,
      snippet: 'AWS promotional credits available...',
      mediaTimestamp: null,
    },
    ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  class MockIntersectionObserver {
    observe = vi.fn()
    disconnect = vi.fn()
    unobserve = vi.fn()
  }
  // @ts-expect-error - minimal stub sufficient for these tests, not jsdom's real type
  global.IntersectionObserver = MockIntersectionObserver
})

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <SearchPage />
    </MemoryRouter>,
  )
}

describe('SearchPage', () => {
  it('does not call searchDocuments when there is no q param', () => {
    renderAt('/app/search')
    expect(searchDocuments).not.toHaveBeenCalled()
    expect(screen.getByText('Search your memory')).toBeInTheDocument()
  })

  it('calls searchDocuments with the q param and renders results', async () => {
    vi.mocked(searchDocuments).mockResolvedValue({ query: 'aws credits', results: [makeResult()] })

    renderAt('/app/search?q=aws+credits')

    await waitFor(() =>
      expect(searchDocuments).toHaveBeenCalledWith({ query: 'aws credits', filters: undefined }),
    )
    expect(await screen.findByText('Screenshot_20260903.png')).toBeInTheDocument()
    expect(screen.getByText('AWS promotional credits available...')).toBeInTheDocument()
  })

  it('never renders a raw numeric score for a result', async () => {
    vi.mocked(searchDocuments).mockResolvedValue({ query: 'aws credits', results: [makeResult()] })
    renderAt('/app/search?q=aws+credits')
    await screen.findByText('Screenshot_20260903.png')
    expect(screen.queryByText(/0\.87/)).not.toBeInTheDocument()
    expect(screen.queryByText(/87%/)).not.toBeInTheDocument()
  })

  it('shows an empty state when the search returns no results', async () => {
    vi.mocked(searchDocuments).mockResolvedValue({ query: 'nothing here', results: [] })
    renderAt('/app/search?q=nothing+here')
    expect(await screen.findByText('No results found.')).toBeInTheDocument()
  })

  it('shows an error state when the search request fails', async () => {
    vi.mocked(searchDocuments).mockRejectedValue(new Error('/api/v1/search failed with status 502'))
    renderAt('/app/search?q=aws+credits')
    expect(await screen.findByText(/Search is unavailable right now/)).toBeInTheDocument()
  })

  it('re-runs the search with a mediaCategories filter when a category pill is selected', async () => {
    vi.mocked(searchDocuments).mockResolvedValue({ query: 'aws credits', results: [makeResult()] })
    renderAt('/app/search?q=aws+credits')

    await waitFor(() =>
      expect(searchDocuments).toHaveBeenCalledWith({ query: 'aws credits', filters: undefined }),
    )

    await userEvent.click(screen.getByRole('button', { name: 'Photos' }))

    await waitFor(() =>
      expect(searchDocuments).toHaveBeenLastCalledWith({
        query: 'aws credits',
        filters: { mediaCategories: ['IMAGE'] },
      }),
    )
  })
})
