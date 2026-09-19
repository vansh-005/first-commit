import { useEffect } from 'react'

/** Sets a descriptive document title per page (helps tab switching and screen-reader users). */
export function usePageTitle(title: string) {
  useEffect(() => {
    const previous = document.title
    document.title = `${title} · Recollect`
    return () => {
      document.title = previous
    }
  }, [title])
}
