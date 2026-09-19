import { FileCard, FileCardSkeleton } from '@/components/library/FileCard'
import { FileThumb } from '@/components/library/FileThumb'
import { StatusBadge } from '@/components/library/StatusBadge'
import { EmptyState } from '@/components/ui/empty-state'
import { ErrorState } from '@/components/ui/error-state'
import { Button } from '@/components/ui/button'
import { useUploads } from '@/components/upload/uploadContext'
import { useDocuments, isProcessing } from '@/hooks/useDocuments'
import { ArrowRight, FolderOpen, MessageSquare, Search, Upload } from 'lucide-react'
import { usePageTitle } from '@/hooks/usePageTitle'
import { useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { Link, useNavigate } from 'react-router-dom'

const EXAMPLE_SEARCHES = ['that AWS credits screenshot', 'my electricity bill', 'the lecture about fading']

function greeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}

/**
 * Docs/FRONTEND.md §12. Search is the primary interaction; below it, quick actions, anything
 * still processing (only when relevant), and the most recent memories. No fake analytics — every
 * element is real data or a real action.
 */
export function HomePage() {
  usePageTitle('Home')
  const auth = useAuth()
  const navigate = useNavigate()
  const { items: uploadItems, openDialog } = useUploads()
  const [query, setQuery] = useState('')

  const uploadedCount = uploadItems.filter((item) => item.status === 'uploaded').length
  const { items, loading, error, reload } = useDocuments({ limit: 24, refreshSignal: uploadedCount })

  const profile = auth.user?.profile
  const firstName = profile?.given_name ?? profile?.name?.split(' ')[0] ?? null

  const processingDocs = items.filter((document) => isProcessing(document.status))
  const recent = items.filter((document) => document.status === 'READY').slice(0, 8)

  function search(text: string) {
    const trimmed = text.trim()
    if (trimmed) navigate(`/app/search?q=${encodeURIComponent(trimmed)}`)
  }

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-10 px-4 py-8 sm:px-6 sm:py-12">
      <section className="animate-fade-up">
        <h1 className="text-3xl font-semibold tracking-tight text-text-primary sm:text-4xl">
          {greeting()}
          {firstName ? `, ${firstName}` : ''}
        </h1>
        <p className="mt-2 text-text-secondary">What are you trying to remember?</p>

        <form
          role="search"
          onSubmit={(event) => {
            event.preventDefault()
            search(query)
          }}
          className="mt-6 flex items-center gap-2 rounded-[var(--radius-lg)] border border-border-strong bg-surface p-2 pl-4 shadow-[var(--shadow-sm)] transition-colors focus-within:border-accent"
        >
          <Search className="size-5 shrink-0 text-text-muted" aria-hidden="true" />
          <label className="flex-1">
            <span className="sr-only">Describe what you’re looking for</span>
            <input
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Describe it — “the receipt from the laptop repair”"
              className="h-10 w-full bg-transparent text-base text-text-primary placeholder:text-text-muted focus:outline-none"
            />
          </label>
          <Button type="submit" disabled={!query.trim()}>
            Search
          </Button>
        </form>

        <div className="mt-3 flex flex-wrap items-center gap-2 text-sm">
          <span className="text-text-muted">Try:</span>
          {EXAMPLE_SEARCHES.map((example) => (
            <button
              key={example}
              type="button"
              onClick={() => search(example)}
              className="rounded-full border border-border px-3 py-1 text-text-secondary transition-colors hover:border-border-strong hover:text-text-primary"
            >
              {example}
            </button>
          ))}
        </div>
      </section>

      <section aria-label="Quick actions" className="grid gap-3 sm:grid-cols-2">
        <button
          type="button"
          onClick={openDialog}
          className="group flex items-center gap-4 rounded-[var(--radius-lg)] border border-border bg-surface p-4 text-left transition-colors hover:border-accent/50 hover:bg-surface-raised"
        >
          <span className="flex size-11 shrink-0 items-center justify-center rounded-[var(--radius-md)] bg-accent-subtle text-accent-text">
            <Upload className="size-5" aria-hidden="true" />
          </span>
          <span className="flex-1">
            <span className="block text-sm font-medium text-text-primary">Upload files</span>
            <span className="block text-sm text-text-muted">Screenshots, documents, recordings, videos</span>
          </span>
          <ArrowRight className="size-4 text-text-muted transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
        </button>
        <Link
          to="/app/ask"
          className="group flex items-center gap-4 rounded-[var(--radius-lg)] border border-border bg-surface p-4 transition-colors hover:border-accent/50 hover:bg-surface-raised"
        >
          <span className="flex size-11 shrink-0 items-center justify-center rounded-[var(--radius-md)] bg-accent-subtle text-accent-text">
            <MessageSquare className="size-5" aria-hidden="true" />
          </span>
          <span className="flex-1">
            <span className="block text-sm font-medium text-text-primary">Ask your memory</span>
            <span className="block text-sm text-text-muted">Get answers with sources you can open</span>
          </span>
          <ArrowRight className="size-4 text-text-muted transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
        </Link>
      </section>

      {error && <ErrorState message={error} onRetry={reload} />}

      {processingDocs.length > 0 && (
        <section aria-labelledby="processing-heading" aria-live="polite">
          <h2 id="processing-heading" className="mb-3 text-sm font-medium text-text-secondary">
            Processing · {processingDocs.length}
          </h2>
          <ul className="flex flex-col gap-2">
            {processingDocs.map((document) => (
              <li
                key={document.documentId}
                className="flex items-center gap-3 rounded-[var(--radius-md)] border border-border bg-surface px-3 py-2.5"
              >
                <div className="size-9 shrink-0 overflow-hidden rounded-[var(--radius-sm)] border border-border">
                  <FileThumb documentId={document.documentId} fileName={document.fileName} mediaCategory={document.mediaCategory} />
                </div>
                <span className="min-w-0 flex-1 truncate text-sm text-text-primary">{document.fileName}</span>
                <StatusBadge status={document.status} />
              </li>
            ))}
          </ul>
        </section>
      )}

      {loading ? (
        <section aria-label="Recent memories" aria-busy="true">
          <div className="skeleton mb-3 h-4 w-40 rounded-full" />
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            {Array.from({ length: 4 }, (_, index) => (
              <FileCardSkeleton key={index} />
            ))}
          </div>
        </section>
      ) : recent.length > 0 ? (
        <section aria-labelledby="recent-heading">
          <div className="mb-3 flex items-center justify-between">
            <h2 id="recent-heading" className="text-sm font-medium text-text-secondary">
              Recent memories
            </h2>
            <Link to="/app/library" className="flex items-center gap-1 text-sm text-accent-text text-accent-hover">
              View library <ArrowRight className="size-3.5" aria-hidden="true" />
            </Link>
          </div>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            {recent.map((document) => (
              <FileCard key={document.documentId} document={document} />
            ))}
          </div>
        </section>
      ) : (
        !error &&
        items.length === 0 && (
          <EmptyState
            icon={FolderOpen}
            title="Your memory is empty"
            action={<Button onClick={openDialog}>Upload your first files</Button>}
          >
            Upload screenshots, documents, audio or videos and find them later using natural language.
          </EmptyState>
        )
      )}
    </div>
  )
}
