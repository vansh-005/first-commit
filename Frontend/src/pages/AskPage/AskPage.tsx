import { ApiError, askQuestion } from '@/api/client'
import { CitationCard } from '@/components/ask/CitationCard'
import { Button } from '@/components/ui/button'
import type { Citation } from '@/types/document'
import { useState } from 'react'

interface Turn {
  question: string
  answer: string
  citations: Citation[]
}

/**
 * Docs/API.md §20 + Docs/FRONTEND.md §16. Question -> Answer -> Sources, with a lightweight
 * conversational thread for follow-ups.
 *
 * The session identifier is opaque and application-issued (Phase 6 amendment) — this page
 * never invents one and never surfaces the underlying Bedrock session to the user; it just
 * holds whatever the last /ask response returned and sends it back verbatim on the next turn.
 * No conversation history is persisted anywhere outside this component's own state — a page
 * refresh starts a new conversation, same as Search's session handling.
 */
export function AskPage() {
  const [question, setQuestion] = useState('')
  const [turns, setTurns] = useState<Turn[]>([])
  const [sessionId, setSessionId] = useState<string | undefined>(undefined)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sessionExpiredNotice, setSessionExpiredNotice] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    const trimmed = question.trim()
    if (!trimmed || loading) return

    setLoading(true)
    setError(null)
    setSessionExpiredNotice(false)

    try {
      const response = await askQuestion({ question: trimmed, sessionId })
      setSessionId(response.sessionId)
      setTurns((previous) => [...previous, { question: trimmed, answer: response.answer, citations: response.citations }])
      setQuestion('')
    } catch (err) {
      if (err instanceof ApiError && err.code === 'ASK_SESSION_EXPIRED') {
        // Per the Phase 6 amendment: never transparently retry a contextual follow-up with
        // assumed history. Start a fresh conversation and let the user re-send — their typed
        // question stays in the box so nothing is lost.
        setSessionId(undefined)
        setTurns([])
        setSessionExpiredNotice(true)
      } else {
        setError(err instanceof Error ? err.message : 'Something went wrong. Please try again.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex flex-col gap-6 p-6">
      <h1 className="text-2xl font-semibold text-text-primary">Ask</h1>

      {turns.length === 0 && !loading && (
        <div className="flex flex-col items-center gap-2 py-16 text-center">
          <p className="text-text-primary">Ask a question about your memory</p>
          <p className="text-sm text-text-muted">Try "What did my internship offer say about relocation?"</p>
        </div>
      )}

      {turns.length > 0 && (
        <div className="flex flex-col gap-8">
          {turns.map((turn, index) => (
            <div key={index} className="flex flex-col gap-3">
              <p className="text-sm font-medium text-text-secondary">{turn.question}</p>
              <p className="whitespace-pre-wrap text-text-primary">{turn.answer}</p>
              {turn.citations.length > 0 && (
                <div className="flex flex-col gap-2">
                  <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Sources</p>
                  {turn.citations.map((citation) => (
                    <CitationCard key={citation.citationId} citation={citation} />
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {loading && <p className="text-sm text-text-muted">Thinking…</p>}
      {sessionExpiredNotice && (
        <p className="text-sm text-text-muted">Your previous conversation expired. Starting a new one.</p>
      )}
      {error && <p className="text-sm text-error">{error}</p>}

      <form onSubmit={handleSubmit} className="flex gap-2">
        <input
          type="text"
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          placeholder="Ask a question about your memory..."
          className="flex-1 rounded-[var(--radius-md)] border border-border bg-surface-muted px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-accent focus:outline-none"
        />
        <Button type="submit" disabled={loading || !question.trim()}>
          Ask
        </Button>
      </form>
    </div>
  )
}
