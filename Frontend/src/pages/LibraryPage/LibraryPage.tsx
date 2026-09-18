import { listDocuments } from '@/api/client'
import { CategoryTabs } from '@/components/library/CategoryTabs'
import { FileCard } from '@/components/library/FileCard'
import { UploadDropzone } from '@/components/upload/UploadDropzone'
import { UploadProgressList } from '@/components/upload/UploadProgressList'
import { useFileUpload } from '@/hooks/useFileUpload'
import type { DocumentSummary, MediaCategory } from '@/types/document'
import { useEffect, useState } from 'react'

/**
 * Docs/FRONTEND.md §14 + Docs/TASKS.md Phase 3. Newly uploaded files show a local
 * "Uploaded" state in the progress list (see useFileUpload) — the fetched library grid
 * below still reflects whatever the backend actually persisted, which stays UPLOAD_PENDING
 * until Phase 4 processes the S3 ObjectCreated event.
 */
export function LibraryPage() {
  const [category, setCategory] = useState<MediaCategory | 'ALL'>('ALL')
  const [documents, setDocuments] = useState<DocumentSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const { items: uploadItems, uploadFiles } = useFileUpload()

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    listDocuments(category === 'ALL' ? {} : { category })
      .then((response) => {
        if (!cancelled) setDocuments(response.items)
      })
      .catch((err: Error) => {
        if (!cancelled) setError(err.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    // Guards against a stale response (e.g. from a fast category switch) overwriting a
    // newer one that already resolved.
    return () => {
      cancelled = true
    }
  }, [category])

  return (
    <div className="flex flex-col gap-6 p-6">
      <h1 className="text-2xl font-semibold text-text-primary">Library</h1>

      <UploadDropzone onFilesSelected={uploadFiles} />
      <UploadProgressList items={uploadItems} />

      <CategoryTabs value={category} onChange={setCategory} />

      {loading && <p className="text-sm text-text-muted">Loading…</p>}
      {error && <p className="text-sm text-error">Could not load your library: {error}</p>}

      {!loading && !error && documents.length === 0 && (
        <div className="flex flex-col items-center gap-2 py-16 text-center">
          <p className="text-text-primary">Your memory is empty.</p>
          <p className="text-sm text-text-muted">
            Upload screenshots, documents, audio or videos and find them later using natural language.
          </p>
        </div>
      )}

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
        {documents.map((document) => (
          <FileCard key={document.documentId} document={document} />
        ))}
      </div>
    </div>
  )
}
