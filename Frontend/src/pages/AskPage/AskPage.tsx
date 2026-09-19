import { ApiError, askQuestion } from '@/api/client'
import { LogoMark } from '@/components/brand/Logo'
import { CitationCard } from '@/components/ask/CitationCard'
import { Button } from '@/components/ui/button'
import { usePageTitle } from '@/hooks/usePageTitle'
import { friendlyError } from '@/lib/errors'
import type { Citation } from '@/types/document'
import { ArrowUp, CircleAlert, Info, RotateCcw, Sparkles } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

interface Turn {
  question: string
  answer: string
  citations: Citation[]
}

const SUGGESTED_PROMPTS = [
  'What did my internship offer say about relocation?',
  'How much AWS credit did I have?',
  'Summarize my most recent recording',
]

function UserBubble({ text }: { text: string }) {
  return (
    <div className="flex justify-end">
      <p className="max-w-[85%] whitespace-pre-wrap rounded-[var(--radius-lg)] rounded-br-sm border border-accent/20 bg-accent-subtle px-4 py-2.5 text-[15px] text-text-primary">
        {text}
      </p>
    </div>
  )
}

function AssistantRow({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex gap-3">
      <LogoMark className="mt-0.5 size-6" />
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  )
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
  usePageTitle('Ask')
  // Search's "Ask your memory" bridge passes the query along; it only prefills the composer and
  // never auto-sends (Ask calls a model, so it should be an explicit user action).
  const [searchParams] = useSearchParams()
  const [question, setQuestion] = useState(() => searchParams.get('q') ?? '')
  const [turns, setTurns] = useState<Turn[]>([])
  const [pending, setPending] = useState<string | null>(null)
  const [sessionId, setSessionId] = useState<string | undefined>(undefined)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<{ message: string; question: string } | null>(null)
  const [sessionExpiredNotice, setSessionExpiredNotice] = useState(false)
  const endRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (turns.length === 0 && !loading) return
    endRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'end' })
  }, [turns.length, loading, error])

  async function submit(text: string) {
    const trimmed = text.trim()
    if (!trimmed || loading) return

    setLoading(true)
    setPending(trimmed)
    setQuestion('')
    setError(null)
    setSessionExpiredNotice(false)

    try {
      const response = await askQuestion({ question: trimmed, sessionId })
      setSessionId(response.sessionId)
      setTurns((previous) => [...previous, { question: trimmed, answer: response.answer, citations: response.citations }])
    } catch (err) {
      // The question is never lost: it goes back into the composer on every failure.
      setQuestion(trimmed)
      if (err instanceof ApiError && err.code === 'ASK_SESSION_EXPIRED') {
        // Per the Phase 6 amendment: never transparently retry a contextual follow-up with
        // assumed history. Start a fresh conversation and let the user re-send.
        setSessionId(undefined)
        setTurns([])
        setSessionExpiredNotice(true)
      } else {
        setError({ message: friendlyError(err), question: trimmed })
      }
    } finally {
      setPending(null)
      setLoading(false)
    }
  }

  function newConversation() {
    setTurns([])
    setSessionId(undefined)
    setError(null)
    setSessionExpiredNotice(false)
  }

  const isEmpty = turns.length === 0 && pending === null

  return (
    <div className="mx-auto flex min-h-full w-full max-w-[860px] flex-col px-4 sm:px-6">
      {!isEmpty && (
        <div className="flex items-center justify-between pt-5">
          <h1 className="text-sm font-medium text-text-secondary">Ask your memory</h1>
          <Button variant="ghost" size="sm" onClick={newConversation} disabled={loading}>
            <RotateCcw className="size-3.5" aria-hidden="true" />
            New conversation
          </Button>
        </div>
      )}

      <div className="flex flex-1 flex-col py-6">
        {sessionExpiredNotice && (
          <div
            role="status"
            className="mb-6 flex items-start gap-2.5 rounded-[var(--radius-md)] border border-border bg-surface-raised px-4 py-3 text-sm text-text-secondary"
          >
            <Info className="mt-0.5 size-4 shrink-0 text-accent-text" aria-hidden="true" />
            <p>Your previous conversation expired, so we started a new one. Send your question again to continue.</p>
          </div>
        )}

        {isEmpty ? (
          <div className="animate-fade-up my-auto flex flex-col items-center py-10 text-center">
            <LogoMark className="size-12" />
            <h1 className="mt-6 text-3xl font-semibold tracking-tight text-text-primary">Ask your memory</h1>
            <p className="mt-2 max-w-md text-text-secondary">
              Get answers grounded in your own files — with sources you can open to check.
            </p>
            <ul className="mt-8 grid w-full max-w-xl gap-2.5" aria-label="Suggested questions">
              {SUGGESTED_PROMPTS.map((prompt) => (
                <li key={prompt}>
                  <button
                    type="button"
                    onClick={() => submit(prompt)}
                    disabled={loading}
                    className="flex w-full items-center gap-3 rounded-[var(--radius-lg)] border border-border bg-surface px-4 py-3 text-left text-sm text-text-primary transition-colors hover:border-accent/50 hover:bg-surface-raised disabled:opacity-50"
                  >
                    <Sparkles className="size-4 shrink-0 text-accent-text" aria-hidden="true" />
                    {prompt}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        ) : (
          <div className="flex flex-col gap-9">
            {turns.map((turn, index) => (
              <section key={index} aria-label={`Question ${index + 1}`} className="flex flex-col gap-4">
                <UserBubble text={turn.question} />
                <AssistantRow>
                  <p className="whitespace-pre-wrap text-[15px] leading-relaxed text-text-primary">{turn.answer}</p>
                  {turn.citations.length > 0 && (
                    <div className="mt-5 flex flex-col gap-2">
                      <p className="text-xs font-medium uppercase tracking-wider text-text-muted">
                        Sources · {turn.citations.length}
                      </p>
                      {turn.citations.map((citation, citationIndex) => (
                        <CitationCard key={citation.citationId} citation={citation} index={citationIndex + 1} />
                      ))}
                    </div>
                  )}
                </AssistantRow>
              </section>
            ))}

            {pending !== null && (
              <section className="flex flex-col gap-4" aria-label="Answering">
                <UserBubble text={pending} />
                <AssistantRow>
                  <div role="status" className="flex flex-col gap-2.5 pt-1">
                    <span className="sr-only">Looking through your memories…</span>
                    <span className="skeleton h-3.5 w-11/12 rounded-full" />
                    <span className="skeleton h-3.5 w-4/5 rounded-full" />
                    <span className="skeleton h-3.5 w-2/3 rounded-full" />
                  </div>
                </AssistantRow>
              </section>
            )}
          </div>
        )}

        {error && (
          <div
            role="alert"
            className="mt-6 flex flex-wrap items-center gap-3 rounded-[var(--radius-md)] border border-error/30 bg-error/10 px-4 py-3 text-sm text-error"
          >
            <CircleAlert className="size-4 shrink-0" aria-hidden="true" />
            <p className="flex-1">{error.message}</p>
            <Button variant="secondary" size="sm" onClick={() => submit(error.question)} disabled={loading}>
              Retry
            </Button>
          </div>
        )}

        <div ref={endRef} />
      </div>

      <div className="sticky bottom-0 -mx-4 bg-gradient-to-t from-background from-70% to-transparent px-4 pb-4 pt-6 sm:-mx-6 sm:px-6">
        <form
          onSubmit={(event) => {
            event.preventDefault()
            submit(question)
          }}
          className="flex items-center gap-2 rounded-[var(--radius-lg)] border border-border-strong bg-surface p-2 pl-4 shadow-[var(--shadow-md)] transition-colors focus-within:border-accent"
        >
          <label className="flex-1">
            <span className="sr-only">Your question</span>
            <input
              type="text"
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="Ask a question about your memory..."
              className="w-full bg-transparent py-1.5 text-[15px] text-text-primary placeholder:text-text-muted focus:outline-none"
            />
          </label>
          <Button type="submit" size="icon" aria-label="Ask" disabled={loading || !question.trim()}>
            <ArrowUp className="size-4" aria-hidden="true" />
          </Button>
        </form>
      </div>
    </div>
  )
}
