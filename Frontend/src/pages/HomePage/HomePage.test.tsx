import { listDocuments } from '@/api/client'
import { UploadContext, type UploadContextValue } from '@/components/upload/uploadContext'
import type { DocumentStatus, DocumentSummary } from '@/types/document'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { HomePage } from './HomePage'

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({ user: { profile: { given_name: 'Ada', name: 'Ada Lovelace' } } }),
}))
vi.mock('@/api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/client')>()
  return { ...actual, listDocuments: vi.fn(), getAccessUrl: vi.fn() }
})

function doc(status: DocumentStatus, id: string, name: string): DocumentSummary {
  return {
    documentId: id,
    fileName: name,
    mediaCategory: 'DOCUMENT',
    mimeType: 'application/pdf',
    sizeBytes: 2048,
    status,
    createdAt: '2026-09-18T00:00:00Z',
    uploadedAt: null,
    updatedAt: '2026-09-18T00:00:00Z',
    failureReason: null,
  }
}

const openDialog = vi.fn()
const uploads: UploadContextValue = {
  items: [],
  uploadFiles: vi.fn(),
  dialogOpen: false,
  openDialog,
  closeDialog: vi.fn(),
}

function SearchLocation() {
  const location = useLocation()
  return <div>search-page {location.search}</div>
}

function renderHome() {
  return render(
    <UploadContext.Provider value={uploads}>
      <MemoryRouter initialEntries={['/app']}>
        <Routes>
          <Route path="/app" element={<HomePage />} />
          <Route path="/app/search" element={<SearchLocation />} />
        </Routes>
      </MemoryRouter>
    </UploadContext.Provider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('HomePage', () => {
  it('greets the user by first name and shows recent ready memories', async () => {
    vi.mocked(listDocuments).mockResolvedValue({ items: [doc('READY', 'a', 'offer-letter.pdf')], nextCursor: null })
    renderHome()

    expect(await screen.findByText('offer-letter.pdf')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/Good (morning|afternoon|evening), Ada/)
    expect(screen.getByRole('heading', { name: 'Recent memories' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /processing/i })).not.toBeInTheDocument()
  })

  it('shows a Processing section only while documents are processing', async () => {
    vi.mocked(listDocuments).mockResolvedValue({
      items: [doc('INDEXING', 'b', 'lecture.m4a'), doc('READY', 'a', 'offer-letter.pdf')],
      nextCursor: null,
    })
    renderHome()

    expect(await screen.findByRole('heading', { name: /processing · 1/i })).toBeInTheDocument()
    expect(screen.getByText('lecture.m4a')).toBeInTheDocument()
  })

  it('shows an empty state whose action opens the global upload dialog', async () => {
    vi.mocked(listDocuments).mockResolvedValue({ items: [], nextCursor: null })
    renderHome()

    await userEvent.click(await screen.findByRole('button', { name: /upload your first files/i }))
    expect(openDialog).toHaveBeenCalled()
  })

  it('sends a typed search to the shared search page', async () => {
    vi.mocked(listDocuments).mockResolvedValue({ items: [], nextCursor: null })
    renderHome()

    await userEvent.type(screen.getByRole('searchbox'), 'aws credits')
    await userEvent.click(screen.getByRole('button', { name: 'Search' }))

    expect(await screen.findByText('search-page ?q=aws%20credits')).toBeInTheDocument()
  })

  it('shows a friendly error with Retry when the library cannot load', async () => {
    vi.mocked(listDocuments).mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce({ items: [], nextCursor: null })
    renderHome()

    expect(await screen.findByRole('alert')).toHaveTextContent('We couldn’t load your library.')
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('button', { name: /upload your first files/i })).toBeInTheDocument()
  })
})
