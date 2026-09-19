import { CategoryTabs, CATEGORY_TABS } from '@/components/library/CategoryTabs'
import { FileCard, FileCardSkeleton } from '@/components/library/FileCard'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/ui/empty-state'
import { ErrorState } from '@/components/ui/error-state'
import { UploadDropzone } from '@/components/upload/UploadDropzone'
import { UploadProgressList } from '@/components/upload/UploadProgressList'
import { useUploads } from '@/components/upload/uploadContext'
import { useDocuments } from '@/hooks/useDocuments'
import { usePageTitle } from '@/hooks/usePageTitle'
import type { MediaCategory } from '@/types/document'
import { FolderOpen, LoaderCircle, Plus, Upload } from 'lucide-react'
import { useState, type DragEvent } from 'react'

function hasFiles(event: DragEvent) {
  return Array.from(event.dataTransfer.types).includes('Files')
}

/**
 * Docs/FRONTEND.md §14. The grid is the hero: a slim dropzone, a page-wide drop target, and the
 * shared upload dialog all feed one upload queue. Files uploaded in this session appear in the
 * progress list while they transfer, then in the grid as Processing → Ready — the status refreshes
 * on its own (see useDocuments), no manual reload.
 */
export function LibraryPage() {
  usePageTitle('Library')
  const [category, setCategory] = useState<MediaCategory | 'ALL'>('ALL')
  const [dragging, setDragging] = useState(false)
  const { items: uploadItems, uploadFiles, openDialog } = useUploads()

  const uploadedCount = uploadItems.filter((item) => item.status === 'uploaded').length
  const { items, loading, error, processing, hasMore, loadingMore, loadMoreFailed, loadMore, reload } = useDocuments({
    category: category === 'ALL' ? undefined : category,
    limit: 24,
    refreshSignal: uploadedCount,
  })

  // Finished uploads graduate into the grid (as Processing); only in-flight/failed ones stay here.
  const activeUploads = uploadItems.filter((item) => item.status !== 'uploaded')
  const categoryLabel = CATEGORY_TABS.find((tab) => tab.value === category)?.label.toLowerCase() ?? 'files'

  return (
    <div
      className="relative mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-8 sm:px-6"
      onDragOver={(event) => {
        if (hasFiles(event)) {
          event.preventDefault()
          setDragging(true)
        }
      }}
      onDragLeave={(event) => {
        if (!event.relatedTarget) setDragging(false)
      }}
      onDrop={(event) => {
        if (!hasFiles(event)) return
        event.preventDefault()
        setDragging(false)
        const files = Array.from(event.dataTransfer.files)
        if (files.length > 0) uploadFiles(files)
      }}
    >
      {dragging && (
        <div
          aria-hidden="true"
          className="pointer-events-none fixed inset-0 z-40 flex items-center justify-center bg-background/80 backdrop-blur-sm"
        >
          <div className="flex flex-col items-center gap-3 rounded-[var(--radius-lg)] border-2 border-dashed border-accent bg-accent-subtle px-16 py-12 text-text-primary">
            <Upload className="size-8 text-accent-text" />
            <p className="text-lg font-medium">Drop to add to your memory</p>
          </div>
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-text-primary">Library</h1>
          <p className="mt-0.5 text-sm text-text-muted" aria-live="polite">
            {processing ? 'Some files are still processing — this updates automatically.' : 'Everything you’ve added, newest first.'}
          </p>
        </div>
        <Button onClick={openDialog}>
          <Plus className="size-4" aria-hidden="true" />
          Upload
        </Button>
      </div>

      <UploadDropzone onFilesSelected={uploadFiles} compact />
      <UploadProgressList items={activeUploads} />

      <CategoryTabs value={category} onChange={setCategory} />

      {error && <ErrorState message={error} onRetry={reload} />}

      {loading ? (
        <div aria-busy="true" aria-label="Loading your library" className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
          {Array.from({ length: 10 }, (_, index) => (
            <FileCardSkeleton key={index} />
          ))}
        </div>
      ) : (
        !error &&
        items.length === 0 && (
          <EmptyState
            icon={FolderOpen}
            title={category === 'ALL' ? 'Your memory is empty' : `No ${categoryLabel} yet`}
            action={
              <Button onClick={openDialog}>
                <Upload className="size-4" aria-hidden="true" />
                {category === 'ALL' ? 'Upload your first files' : 'Upload files'}
              </Button>
            }
          >
            {category === 'ALL'
              ? 'Upload screenshots, documents, audio or videos and find them later using natural language.'
              : 'Nothing of this type has been added. Try another filter, or upload something new.'}
          </EmptyState>
        )
      )}

      {items.length > 0 && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
          {items.map((document) => (
            <FileCard key={document.documentId} document={document} />
          ))}
        </div>
      )}

      {hasMore && (
        <div className="flex flex-col items-center gap-2 pt-2">
          {loadMoreFailed && <p role="alert" className="text-sm text-error">We couldn’t load more files. Please try again.</p>}
          <Button variant="secondary" onClick={loadMore} disabled={loadingMore}>
            {loadingMore && <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />}
            {loadingMore ? 'Loading…' : 'Load more'}
          </Button>
        </div>
      )}
    </div>
  )
}
