import { askQuestion } from '@/api/client'
import type { AskResponse } from '@/types/document'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
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

function renderAsk(path = '/app/ask') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AskPage />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('AskPage', () => {
  it('shows an empty state with three suggested prompts and makes no API call before a question is submitted', () => {
    renderAsk()
    expect(screen.getByRole('heading', { name: 'Ask your memory' })).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: /suggested questions/i })).getAllByRole('button')).toHaveLength(3)
    expect(askQuestion).not.toHaveBeenCalled()
  })

  it('prefills the composer from ?q= (the Search bridge) without sending it', () => {
    renderAsk('/app/ask?q=where%20is%20my%20lease')
    expect(screen.getByPlaceholderText('Ask a question about your memory...')).toHaveValue('where is my lease')
    expect(askQuestion).not.toHaveBeenCalled()
  })

  it('submits a suggested prompt with one click', async () => {
    vi.mocked(askQuestion).mockResolvedValue(makeResponse())
    renderAsk()

    await userEvent.click(screen.getByRole('button', { name: 'How much AWS credit did I have?' }))

    await waitFor(() =>
      expect(askQuestion).toHaveBeenCalledWith({ question: 'How much AWS credit did I have?', sessionId: undefined }),
    )
    expect(await screen.findByText(makeResponse().answer)).toBeInTheDocument()
  })

  it('shows the asked question and an answering skeleton while waiting', async () => {
    let resolve: (value: AskResponse) => void = () => {}
    vi.mocked(askQuestion).mockReturnValue(new Promise<AskResponse>((r) => (resolve = r)))
    renderAsk()

    await userEvent.type(screen.getByPlaceholderText('Ask a question about your memory...'), 'What is pending?')
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))

    expect(screen.getByText('What is pending?')).toBeInTheDocument()
    expect(screen.getByText(/looking through your memories/i)).toBeInTheDocument()

    resolve(makeResponse())
    expect(await screen.findByText(makeResponse().answer)).toBeInTheDocument()
    expect(screen.queryByText(/looking through your memories/i)).not.toBeInTheDocument()
  })

  it('submits a question with no sessionId on the first turn and renders the answer and citations', async () => {
    vi.mocked(askQuestion).mockResolvedValue(makeResponse())
    renderAsk()

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
    renderAsk()

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
    renderAsk()

    const input = screen.getByPlaceholderText('Ask a question about your memory...')
    await userEvent.type(input, 'first question')
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))
    await waitFor(() => expect(screen.getByText('internship-offer.pdf')).toBeInTheDocument())

    await userEvent.type(input, 'a follow up after expiry')
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))

    expect(await screen.findByText(/previous conversation expired/i)).toBeInTheDocument()
    // Their typed question is preserved in the composer, not lost.
    expect(screen.getByPlaceholderText('Ask a question about your memory...')).toHaveValue('a follow up after expiry')
    // The expired conversation's turn is cleared, not shown alongside a mismatched new answer.
    expect(screen.queryByText('internship-offer.pdf')).not.toBeInTheDocument()
    // Exactly one call was made for the failed follow-up — no transparent contextual retry.
    expect(askQuestion).toHaveBeenCalledTimes(2)
  })

  it('shows a friendly error with Retry for a non-session failure, keeping the thread and the question', async () => {
    vi.mocked(askQuestion).mockRejectedValueOnce(new Error('Bedrock ValidationException: boom')).mockResolvedValueOnce(makeResponse())
    renderAsk()

    await userEvent.type(screen.getByPlaceholderText('Ask a question about your memory...'), 'a question')
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong. Please try again.')
    expect(screen.queryByText(/bedrock/i)).not.toBeInTheDocument()
    expect(screen.getByPlaceholderText('Ask a question about your memory...')).toHaveValue('a question')

    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByText(makeResponse().answer)).toBeInTheDocument()
    expect(askQuestion).toHaveBeenCalledTimes(2)
  })

  it('presents retrieved items behind a decline as neutral "Context checked", not as Sources', async () => {
    vi.mocked(askQuestion).mockResolvedValue(
      makeResponse({
        answer: 'I can not provide an answer to the question as the search results do not contain information that can answer the question.',
      }),
    )
    renderAsk()
    await userEvent.type(screen.getByPlaceholderText('Ask a question about your memory...'), 'boiling point of mercury on Jupiter')
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))

    expect(await screen.findByText(/context checked · 1 item/i)).toBeInTheDocument()
    expect(screen.queryByText(/^Sources ·/)).not.toBeInTheDocument()
    // Still reachable (relabeled, never hidden) inside the collapsed details.
    expect(screen.getByText('internship-offer.pdf')).toBeInTheDocument()
  })

  it('keeps the numbered Sources presentation for a normal answer', async () => {
    vi.mocked(askQuestion).mockResolvedValue(makeResponse())
    renderAsk()
    await userEvent.type(screen.getByPlaceholderText('Ask a question about your memory...'), 'What did my offer say?')
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))

    expect(await screen.findByText('Sources · 1')).toBeInTheDocument()
    expect(screen.queryByText(/context checked/i)).not.toBeInTheDocument()
  })
})
