import { useEffect, useRef } from 'react'

/**
 * Fires `onIntersect` at most once, the first time the returned ref's element becomes
 * visible (with a preloading margin), then stops observing. Used to lazily fetch
 * per-card data (e.g. a thumbnail's presigned URL) only as a card actually scrolls into
 * view, instead of firing one request per row up front for a library that could hold
 * hundreds of files.
 */
export function useIntersectionOnce<T extends Element>(onIntersect: () => void) {
  const ref = useRef<T>(null)
  const firedRef = useRef(false)
  // Keeps the observer-registration effect's dependency array empty (subscribe once)
  // while always calling the latest closure passed by the caller. Updated in its own
  // effect, not during render — refs must not be written while rendering.
  const callbackRef = useRef(onIntersect)
  useEffect(() => {
    callbackRef.current = onIntersect
  })

  useEffect(() => {
    const element = ref.current
    // No IntersectionObserver (very old browsers, jsdom): skip lazy loading; callers fall back
    // to their type placeholder rather than fetching everything up front.
    if (!element || typeof IntersectionObserver === 'undefined') return

    const observer = new IntersectionObserver(
      (entries) => {
        if (!firedRef.current && entries.some((entry) => entry.isIntersecting)) {
          firedRef.current = true
          callbackRef.current()
          observer.disconnect()
        }
      },
      { rootMargin: '200px' },
    )
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  return ref
}
