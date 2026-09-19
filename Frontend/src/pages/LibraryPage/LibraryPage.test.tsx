import { listDocuments } from '@/api/client'
import type { UploadItem } from '@/hooks/useFileUpload'
import { UploadContext, type UploadContextValue } from '@/components/upload/uploadContext'
import type { DocumentStatus, DocumentSummary } from '@/types/document'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LibraryPage } from './LibraryPage'

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
function renderLibrary(items: UploadItem[] = []) {
  const uploads: UploadContextValue = { items, uploadFiles: vi.fn(), dialogOpen: false, openDialog, closeDialog: vi.fn() }
  return render(
    <UploadContext.Provider value={uploads}>
      <MemoryRouter>
        <LibraryPage />
      </MemoryRouter>
    </UploadContext.Provider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('LibraryPage', () => {
  it('shows a loading skeleton, then cards with Ready / Processing / Failed badges', async () => {
    vi.mocked(listDocuments).mockResolvedValue({
      items: [doc('READY', 'a', 'ready.pdf'), doc('INDEXING', 'b', 'busy.pdf'), doc('FAILED', 'c', 'broken.pdf')],
      nextCursor: null,
    })
    renderLibrary()

    expect(screen.getByLabelText('Loading your library')).toBeInTheDocument()
    expect(await screen.findByText('ready.pdf')).toBeInTheDocument()
    expect(screen.getByText('Ready')).toBeInTheDocument()
    expect(screen.getByText('Processing')).toBeInTheDocument()
    expect(screen.getByText('Failed')).toBeInTheDocument()
    expect(screen.getByText(/we couldn’t process this file/i)).toBeInTheDocument()
  })

  it('shows an empty state whose action opens the upload dialog', async () => {
    vi.mocked(listDocuments).mockResolvedValue({ items: [], nextCursor: null })
    renderLibrary()

    await userEvent.click(await screen.findByRole('button', { name: /upload your first files/i }))
    expect(openDialog).toHaveBeenCalled()
  })

  it('re-queries with the chosen category', async () => {
    vi.mocked(listDocuments).mockResolvedValue({ items: [], nextCursor: null })
    renderLibrary()
    await screen.findByText(/your memory is empty/i)

    await userEvent.click(screen.getByRole('button', { name: 'Photos' }))

    await waitFor(() => expect(listDocuments).toHaveBeenLastCalledWith({ limit: 24, category: 'IMAGE' }))
    expect(await screen.findByText(/no photos yet/i)).toBeInTheDocument()
  })

  it('loads more with the API cursor', async () => {
    vi.mocked(listDocuments)
      .mockResolvedValueOnce({ items: [doc('READY', 'a', 'first.pdf')], nextCursor: 'next-1' })
      .mockResolvedValueOnce({ items: [{ ...doc('READY', 'b', 'second.pdf'), createdAt: '2026-09-17T00:00:00Z' }], nextCursor: null })
    renderLibrary()

    await userEvent.click(await screen.findByRole('button', { name: 'Load more' }))

    expect(await screen.findByText('second.pdf')).toBeInTheDocument()
    expect(listDocuments).toHaveBeenLastCalledWith({ limit: 24, category: undefined, cursor: 'next-1' })
    expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument()
  })

  it('lists in-flight and failed uploads with a friendly message, but not finished ones', async () => {
    vi.mocked(listDocuments).mockResolvedValue({ items: [], nextCursor: null })
    const file = (name: string) => new File(['x'], name)
    renderLibrary([
      { clientFileId: '1', file: file('failed.png'), progress: 0, status: 'failed', error: 'This file didn’t upload. Please try again.' },
      { clientFileId: '2', file: file('done.png'), progress: 100, status: 'uploaded' },
    ])

    expect(await screen.findByText('failed.png')).toBeInTheDocument()
    expect(screen.getByText('This file didn’t upload. Please try again.')).toBeInTheDocument()
    expect(screen.queryByText('done.png')).not.toBeInTheDocument()
  })

  it('shows a friendly error with Retry when loading fails', async () => {
    vi.mocked(listDocuments).mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce({ items: [doc('READY', 'a', 'back.pdf')], nextCursor: null })
    renderLibrary()

    expect(await screen.findByRole('alert')).toHaveTextContent('We couldn’t load your library.')
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByText('back.pdf')).toBeInTheDocument()
  })
})
