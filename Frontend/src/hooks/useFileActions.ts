import { getAccessUrl } from '@/api/client'
import { MAX_IN_MEMORY_DOWNLOAD_BYTES, saveFromUrl } from '@/lib/download'
import { useCallback, useState } from 'react'

/**
 * Open / Download for a document. Both go through the existing ownership-checked
 * `GET /documents/{id}/access-url`, fetching a *fresh* short-lived URL on every click — nothing
 * is cached, so an expired link can never be the cause of a failure.
 */
export function useFileActions(documentId: string, fileName: string, sizeBytes?: number) {
  const [busy, setBusy] = useState<'open' | 'download' | null>(null)
  const [error, setError] = useState<string | null>(null)

  const open = useCallback(async () => {
    setBusy('open')
    setError(null)
    try {
      const { url } = await getAccessUrl(documentId)
      window.open(url, '_blank', 'noopener,noreferrer')
    } catch {
      setError('We couldn’t open this file. Please try again.')
    } finally {
      setBusy(null)
    }
  }, [documentId])

  const download = useCallback(async () => {
    setBusy('download')
    setError(null)
    try {
      const { url } = await getAccessUrl(documentId)
      if (sizeBytes !== undefined && sizeBytes > MAX_IN_MEMORY_DOWNLOAD_BYTES) {
        window.open(url, '_blank', 'noopener,noreferrer')
      } else {
        await saveFromUrl(url, fileName)
      }
    } catch {
      setError('We couldn’t download this file. Please try again.')
    } finally {
      setBusy(null)
    }
  }, [documentId, fileName, sizeBytes])

  return { open, download, busy, error }
}
