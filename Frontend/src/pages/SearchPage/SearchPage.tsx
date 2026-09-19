import { searchDocuments } from '@/api/client'
import { CategoryTabs } from '@/components/library/CategoryTabs'
import { SearchResultCard, SearchResultSkeleton } from '@/components/search/SearchResultCard'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/ui/empty-state'
import { ErrorState } from '@/components/ui/error-state'
import { usePageTitle } from '@/hooks/usePageTitle'
import { friendlyError } from '@/lib/errors'
import type { MediaCategory, SearchResult } from '@/types/document'
import { MessageSquare, Search, SearchX } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

const EXAMPLE_SEARCHES = ['that AWS credits screenshot', 'my electricity bill from August', 'the lecture about fading']

/**
 * Docs/FRONTEND.md §15 + Docs/TASKS.md Phase 5. Owns the single canonical call to
 * searchDocuments() for the whole app — the TopBar's global search only ever navigates
 * here with a `q` query param, it never calls the search API itself. Query text stays
 * visible above the results (per §15) rather than collapsing into a results-only view.
 */
export function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''
  usePageTitle(query ? `“${query}”` : 'Search')

  const [category, setCategory] = useState<MediaCategory | 'ALL'>('ALL')
  const [results, setResults] = useState<SearchResult[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [hasSearched, setHasSearched] = useState(false)
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    if (!query.trim()) {
      setResults([])
      setHasSearched(false)
      setError(null)
      return
    }

    let cancelled = false
    setLoading(true)
    setError(null)
    searchDocuments({
      query,
      filters: category === 'ALL' ? undefined : { mediaCategories: [category] },
    })
      .then((response) => {
        if (!cancelled) {
          setResults(response.results)
          setHasSearched(true)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(friendlyError(err, 'Search is unavailable right now. Please try again.'))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    // Guards against a stale response (e.g. from a fast query change) overwriting a
    // newer one that already resolved.
    return () => {
      cancelled = true
    }
  }, [query, category, retryKey])

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-8 sm:px-6">
      {query ? (
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-text-primary">
            Results for <span className="text-accent-text">“{query}”</span>
          </h1>
          <p className="mt-1 min-h-5 text-sm text-text-muted" aria-live="polite">
            {!loading && !error && hasSearched && `${results.length} ${results.length === 1 ? 'result' : 'results'}`}
          </p>
        </div>
      ) : (
        <h1 className="sr-only">Search</h1>
      )}

      {query && <CategoryTabs value={category} onChange={setCategory} label="Filter results by type" />}

      {!query && (
        <EmptyState icon={Search} title="Search your memory" className="py-16">
          Describe what you remember — not the filename. Try one of these:
          <span className="mt-4 flex flex-wrap justify-center gap-2">
            {EXAMPLE_SEARCHES.map((example) => (
              <button
                key={example}
                type="button"
                onClick={() => setSearchParams({ q: example })}
                className="rounded-full border border-border px-3 py-1.5 text-sm text-text-secondary transition-colors hover:border-accent/50 hover:text-text-primary"
              >
                {example}
              </button>
            ))}
          </span>
        </EmptyState>
      )}

      {loading && (
        <div aria-busy="true" aria-label="Searching" className="flex flex-col gap-3">
          {Array.from({ length: 4 }, (_, index) => (
            <SearchResultSkeleton key={index} />
          ))}
        </div>
      )}

      {error && <ErrorState message={error} onRetry={() => setRetryKey((key) => key + 1)} />}

      {!loading && !error && hasSearched && results.length === 0 && (
        <EmptyState
          icon={SearchX}
          title="No results found."
          action={
            <div className="flex flex-wrap justify-center gap-2">
              <Link
                to={`/app/ask?q=${encodeURIComponent(query)}`}
                className="inline-flex h-10 items-center gap-2 rounded-[var(--radius-md)] bg-accent px-4 text-sm font-medium text-white transition-colors hover:bg-accent-hover"
              >
                <MessageSquare className="size-4" aria-hidden="true" />
                Ask your memory
              </Link>
              {category !== 'ALL' && (
                <Button variant="secondary" onClick={() => setCategory('ALL')}>
                  Show all types
                </Button>
              )}
            </div>
          }
        >
          Try describing it differently, or ask a question instead — Recollect can look across everything you’ve added.
        </EmptyState>
      )}

      {!loading && !error && results.length > 0 && (
        <ul className="flex flex-col gap-3">
          {results.map((result, index) => (
            <li key={result.document.documentId} className="animate-fade-up" style={{ animationDelay: `${Math.min(index, 6) * 40}ms` }}>
              <SearchResultCard result={result} query={query} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
