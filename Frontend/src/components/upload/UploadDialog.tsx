import { UploadDropzone } from '@/components/upload/UploadDropzone'
import { UploadProgressList } from '@/components/upload/UploadProgressList'
import { useUploads } from '@/components/upload/uploadContext'
import { useDialogA11y } from '@/hooks/useDialogA11y'
import { X } from 'lucide-react'

/** Global "Add to your memory" dialog, opened from the sidebar, Home and Library. Closing it
 * never cancels anything — the queue lives in UploadProvider. */
export function UploadDialog() {
  const { dialogOpen, closeDialog, items, uploadFiles } = useUploads()
  const panelRef = useDialogA11y(dialogOpen, closeDialog)

  if (!dialogOpen) return null

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center p-0 sm:items-center sm:p-4">
      <button type="button" aria-label="Close upload dialog" tabIndex={-1} onClick={closeDialog} className="absolute inset-0 bg-black/60" />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="upload-dialog-title"
        tabIndex={-1}
        className="animate-fade-up relative flex max-h-[90vh] w-full max-w-xl flex-col gap-4 overflow-y-auto rounded-t-[var(--radius-lg)] border border-border bg-surface p-5 shadow-[var(--shadow-md)] sm:rounded-[var(--radius-lg)]"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="upload-dialog-title" className="text-lg font-semibold text-text-primary">
              Add to your memory
            </h2>
            <p className="mt-0.5 text-sm text-text-secondary">Drop files in — Recollect sorts and indexes them for you.</p>
          </div>
          <button
            type="button"
            onClick={closeDialog}
            aria-label="Close"
            className="rounded-[var(--radius-sm)] p-1.5 text-text-muted hover:bg-surface-muted hover:text-text-primary"
          >
            <X className="size-4" aria-hidden="true" />
          </button>
        </div>

        <UploadDropzone onFilesSelected={uploadFiles} />
        <UploadProgressList items={items} />

        {items.length > 0 && (
          <p className="text-xs text-text-muted">
            You can close this — uploads keep going, and files appear as Processing until they’re ready.
          </p>
        )}
      </div>
    </div>
  )
}
