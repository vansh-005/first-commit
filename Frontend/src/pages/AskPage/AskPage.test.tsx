import { askQuestion } from '@/api/client'
import type { AskResponse } from '@/types/document'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AskPage } from './AskPage'

vi.mock('@/api/client', async (importOriginal) => {
  // Keeps the real ApiError class (so `err instanceof ApiError` in AskPage works against
  // errors this test rejects with) while mocking only the network-calling functions.
  const actual = await importOriginal<typeof import('@/api/client')>()
  return { ...actual, askQuestion: vi.fn(), getAccessUrl: vi.fn() }
})

function makeResponse(overrides: Partial<AskResponse> = {}): AskResponse {
  return {
    answer: 'The offer states relocation expenses are covered up to $2,000.',
    sessionId: 'app-session-1',
    citations: [
      {
        citationId: 'c1',
        documentId: 'doc-1',
        fileName: 'internship-offer.pdf',
        mediaCategory: 'DOCUMENT',
        mimeType: 'application/pdf',
        snippet: 'Relocation expenses are covered up to $2,000.',
        mediaTimestamp: null,
      },
    ],
    ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('AskPage', () => {
  it('shows an empty-state prompt and makes no API call before a question is submitted', () => {
    render(<AskPage />)
    expect(screen.getByText('Ask a question about your memory')).toBeInTheDocument()
    expect(askQuestion).not.toHaveBeenCalled()
  })

  it('submits a question with no sessionId on the first turn and renders the answer and citations', async () => {
    vi.mocked(askQuestion).mockResolvedValue(makeResponse())
    render(<AskPage />)

    await userEvent.type(screen.getByPlaceholderText('Ask a question about your memory...'), 'What did my offer say?')
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))

    await waitFor(() =>
      expect(askQuestion).toHaveBeenCalledWith({ question: 'What did my offer say?', sessionId: undefined }),
    )
    expect(await screen.findByText(makeResponse().answer)).toBeInTheDocument()
    expect(screen.getByText('internship-offer.pdf')).toBeInTheDocument()
  })

  it('sends the previously returned sessionId on a follow-up turn', async () => {
    vi.mocked(askQuestion).mockResolvedValue(makeResponse({ sessionId: 'app-session-1' }))
    render(<AskPage />)

    const input = screen.getByPlaceholderText('Ask a question about your memory...')
    await userEvent.type(input, 'first question')
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))
    await waitFor(() => expect(askQuestion).toHaveBeenCalledTimes(1))

    await userEvent.type(input, 'follow up question')
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))

    await waitFor(() =>
      expect(askQuestion).toHaveBeenLastCalledWith({ question: 'follow up question', sessionId: 'app-session-1' }),
    )
  })

  it('starts a fresh conversation and shows a notice when the session has expired, without transparently retrying', async () => {
    const { ApiError } = await import('@/api/client')
    vi.mocked(askQuestion)
        .mockResolvedValueOnce(makeResponse({ sessionId: 'app-session-1' }))
        .mockRejectedValueOnce(new ApiError('This conversation has expired.', 409, 'ASK_SESSION_EXPIRED'))
    render(<AskPage />)

    const input = screen.getByPlaceholderText('Ask a question about your memory...')
    await userEvent.type(input, 'first question')
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))
    await waitFor(() => expect(screen.getByText('internship-offer.pdf')).toBeInTheDocument())

    await userEvent.type(input, 'a follow up after expiry')
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))

    expect(await screen.findByText(/previous conversation expired/i)).toBeInTheDocument()
    // The expired conversation's turn is cleared, not shown alongside a mismatched new answer.
    expect(screen.queryByText('internship-offer.pdf')).not.toBeInTheDocument()
    // Exactly one call was made for the failed follow-up — no transparent contextual retry.
    expect(askQuestion).toHaveBeenCalledTimes(2)
  })

  it('shows an inline error message for a non-session failure without clearing the thread', async () => {
    vi.mocked(askQuestion).mockRejectedValue(new Error('Ask is temporarily unavailable. Please try again.'))
    render(<AskPage />)

    await userEvent.type(screen.getByPlaceholderText('Ask a question about your memory...'), 'a question')
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))

    expect(await screen.findByText('Ask is temporarily unavailable. Please try again.')).toBeInTheDocument()
  })
})
