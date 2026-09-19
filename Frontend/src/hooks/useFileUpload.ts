import { initUploads, uploadFileToS3 } from '@/api/client'
import { friendlyError } from '@/lib/errors'
import { useCallback, useState } from 'react'

export type UploadItemStatus = 'queued' | 'uploading' | 'uploaded' | 'failed'

export interface UploadItem {
  clientFileId: string
  file: File
  progress: number
  status: UploadItemStatus
  documentId?: string
  error?: string
}

const CONCURRENCY_LIMIT = 4

/**
 * Drives the Docs/API.md §10-11 upload flow: one POST /uploads for the whole batch, then
 * each file PUT directly to S3 with a capped number of concurrent transfers.
 *
 * "uploaded" here is a local-only UI state — the backend document record stays UPLOAD_PENDING
 * until the async ingestion pipeline processes the S3 ObjectCreated event, and the library
 * picks up the real status by polling. Nothing here persists that transition.
 */
export function useFileUpload() {
  const [items, setItems] = useState<UploadItem[]>([])

  const updateItem = useCallback((clientFileId: string, patch: Partial<UploadItem>) => {
    setItems((prev) => prev.map((item) => (item.clientFileId === clientFileId ? { ...item, ...patch } : item)))
  }, [])

  const uploadFiles = useCallback(
    async (files: File[]) => {
      const newItems: UploadItem[] = files.map((file) => ({
        clientFileId: crypto.randomUUID(),
        file,
        progress: 0,
        status: 'queued',
      }))
      setItems((prev) => [...newItems, ...prev])

      let response
      try {
        response = await initUploads(
          newItems.map((item) => ({
            clientFileId: item.clientFileId,
            fileName: item.file.name,
            contentType: item.file.type || 'application/octet-stream',
            sizeBytes: item.file.size,
          })),
        )
      } catch (error) {
        // The whole batch failed before any byte was sent — surface that on every file rather
        // than leaving them "Queued" forever.
        const message = friendlyError(error, 'We couldn’t start this upload. Please try again.')
        newItems.forEach((item) => updateItem(item.clientFileId, { status: 'failed', error: message }))
        return
      }
      const byClientId = new Map(response.uploads.map((result) => [result.clientFileId, result]))

      let nextIndex = 0
      async function worker() {
        while (nextIndex < newItems.length) {
          const item = newItems[nextIndex]
          nextIndex += 1

          const uploadResult = byClientId.get(item.clientFileId)
          if (!uploadResult) {
            updateItem(item.clientFileId, { status: 'failed', error: 'We couldn’t start this upload. Please try again.' })
            continue
          }

          updateItem(item.clientFileId, { status: 'uploading', documentId: uploadResult.documentId })
          try {
            await uploadFileToS3(uploadResult.upload.url, uploadResult.upload.headers, item.file, (percent) =>
              updateItem(item.clientFileId, { progress: percent }),
            )
            updateItem(item.clientFileId, { status: 'uploaded', progress: 100 })
          } catch {
            updateItem(item.clientFileId, { status: 'failed', error: 'This file didn’t upload. Please try again.' })
          }
        }
      }

      const workerCount = Math.min(CONCURRENCY_LIMIT, newItems.length)
      await Promise.all(Array.from({ length: workerCount }, () => worker()))
    },
    [updateItem],
  )

  return { items, uploadFiles }
}
