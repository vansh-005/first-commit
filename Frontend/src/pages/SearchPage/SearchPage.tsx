import { searchDocuments } from '@/api/client'
import { SearchResultCard } from '@/components/search/SearchResultCard'
import type { MediaCategory, SearchResult } from '@/types/document'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

const CATEGORY_FILTERS: { label: string; value: MediaCategory | 'ALL' }[] = [
  { label: 'All', value: 'ALL' },
  { label: 'Documents', value: 'DOCUMENT' },
  { label: 'Photos', value: 'IMAGE' },
  { label: 'Videos', value: 'VIDEO' },
  { label: 'Audio', value: 'AUDIO' },
]

/**
 * Docs/FRONTEND.md §15 + Docs/TASKS.md Phase 5. Owns the single canonical call to
 * searchDocuments() for the whole app — the TopBar's global search only ever navigates
 * here with a `q` query param, it never calls the search API itself. Query text stays
 * visible above the results (per §15) rather than collapsing into a results-only view.
 */
export function SearchPage() {
  const [searchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''

  const [category, setCategory] = useState<MediaCategory | 'ALL'>('ALL')
  const [results, setResults] = useState<SearchResult[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [hasSearched, setHasSearched] = useState(false)

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
      .catch((err: Error) => {
        if (!cancelled) setError(err.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    // Guards against a stale response (e.g. from a fast query change) overwriting a
    // newer one that already resolved.
    return () => {
      cancelled = true
    }
  }, [query, category])

  return (
    <div className="flex flex-col gap-6 p-6">
      <div>
        <h1 className="text-2xl font-semibold text-text-primary">Search</h1>
        {query && <p className="mt-1 text-sm text-text-secondary">Results for "{query}"</p>}
      </div>

      {query && (
        <div className="flex gap-2">
          {CATEGORY_FILTERS.map((filter) => (
            <button
              key={filter.value}
              onClick={() => setCategory(filter.value)}
              className={
                category === filter.value
                  ? 'rounded-full bg-accent-subtle px-3 py-1.5 text-sm text-accent transition-colors'
                  : 'rounded-full px-3 py-1.5 text-sm text-text-secondary transition-colors hover:bg-surface-muted'
              }
            >
              {filter.label}
            </button>
          ))}
        </div>
      )}

      {!query && (
        <div className="flex flex-col items-center gap-2 py-16 text-center">
          <p className="text-text-primary">Search your memory</p>
          <p className="text-sm text-text-muted">Try "AWS promotional credits" or "beach trip photos".</p>
        </div>
      )}

      {loading && <p className="text-sm text-text-muted">Searching…</p>}
      {error && <p className="text-sm text-error">Search is unavailable right now: {error}</p>}

      {!loading && !error && hasSearched && results.length === 0 && (
        <div className="flex flex-col items-center gap-2 py-16 text-center">
          <p className="text-text-primary">No results found.</p>
          <p className="text-sm text-text-muted">Try a different phrase or remove the category filter.</p>
        </div>
      )}

      {!loading && !error && results.length > 0 && (
        <div className="flex flex-col gap-3">
          {results.map((result) => (
            <SearchResultCard key={result.document.documentId} result={result} />
          ))}
        </div>
      )}
    </div>
  )
}
