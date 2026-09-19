import { listDocuments } from '@/api/client'
import { friendlyError } from '@/lib/errors'
import type { DocumentStatus, DocumentSummary, MediaCategory } from '@/types/document'
import { useCallback, useEffect, useState } from 'react'

const POLL_FAST_MS = 5_000
const POLL_SLOW_MS = 15_000
const SLOW_AFTER_MS = 120_000

export function isProcessing(status: DocumentStatus): boolean {
  return status !== 'READY' && status !== 'FAILED'
}

// Mirrors the API's own ordering (GSI1SK = createdAt#documentId, newest first).
function byNewest(a: DocumentSummary, b: DocumentSummary): number {
  return `${b.createdAt}#${b.documentId}`.localeCompare(`${a.createdAt}#${a.documentId}`)
}

function mergeById(existing: DocumentSummary[], incoming: DocumentSummary[]): DocumentSummary[] {
  const incomingIds = new Set(incoming.map((document) => document.documentId))
  return [...incoming, ...existing.filter((document) => !incomingIds.has(document.documentId))].sort(byNewest)
}

interface UseDocumentsOptions {
  category?: MediaCategory
  limit?: number
  /** Bump (e.g. to the number of finished uploads) to silently re-fetch the first page. */
  refreshSignal?: number
}

/**
 * Loads the user's documents (newest first, cursor-paged) and keeps their processing state
 * fresh: while any document is still non-terminal it re-fetches the first page every ~5s (backing
 * off to ~15s after two minutes), and does nothing at all while the tab is hidden or once
 * everything has settled at READY/FAILED — so an idle library costs zero API calls.
 */
export function useDocuments({ category, limit = 24, refreshSignal = 0 }: UseDocumentsOptions = {}) {
  const [items, setItems] = useState<DocumentSummary[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loadMoreFailed, setLoadMoreFailed] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    setItems([])
    setNextCursor(null)
    setLoadMoreFailed(false)
    listDocuments({ limit, category })
      .then((response) => {
        if (cancelled) return
        setItems(response.items)
        setNextCursor(response.nextCursor)
      })
      .catch((err) => {
        if (!cancelled) setError(friendlyError(err, 'We couldn’t load your library.'))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    // Guards against a stale response (e.g. from a fast category switch) overwriting a newer one.
    return () => {
      cancelled = true
    }
  }, [category, limit, reloadKey])

  const refresh = useCallback(async () => {
    try {
      const response = await listDocuments({ limit, category })
      setItems((previous) => mergeById(previous, response.items))
    } catch {
      // Silent: the next poll (or a manual retry) will catch up.
    }
  }, [category, limit])

  useEffect(() => {
    if (refreshSignal > 0) refresh()
  }, [refreshSignal, refresh])

  const processing = items.some((document) => isProcessing(document.status))

  useEffect(() => {
    if (!processing) return
    let stopped = false
    let timer: ReturnType<typeof setTimeout> | undefined
    const startedAt = Date.now()

    const schedule = () => {
      if (stopped) return
      const delay = Date.now() - startedAt > SLOW_AFTER_MS ? POLL_SLOW_MS : POLL_FAST_MS
      timer = setTimeout(tick, delay)
    }
    const tick = async () => {
      // Paused while the tab is hidden: keep the timer alive but make no request.
      if (!document.hidden) await refresh()
      schedule()
    }
    const onVisibilityChange = () => {
      if (!document.hidden) {
        clearTimeout(timer)
        tick()
      }
    }

    document.addEventListener('visibilitychange', onVisibilityChange)
    schedule()
    return () => {
      stopped = true
      clearTimeout(timer)
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [processing, refresh])

  const loadMore = useCallback(async () => {
    if (!nextCursor || loadingMore) return
    setLoadingMore(true)
    setLoadMoreFailed(false)
    try {
      const response = await listDocuments({ limit, category, cursor: nextCursor })
      setItems((previous) => mergeById(previous, response.items))
      setNextCursor(response.nextCursor)
    } catch {
      setLoadMoreFailed(true)
    } finally {
      setLoadingMore(false)
    }
  }, [nextCursor, loadingMore, limit, category])

  const reload = useCallback(() => setReloadKey((key) => key + 1), [])

  return { items, loading, error, processing, hasMore: nextCursor !== null, loadingMore, loadMoreFailed, loadMore, reload }
}
