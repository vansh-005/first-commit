import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { Upload } from 'lucide-react'
import { useRef, useState, type DragEvent } from 'react'

/** Docs/FRONTEND.md §13. Does not force file-type or destination selection. `compact` is a slim
 * single-row strip for pages (Library) where the grid, not the dropzone, is the hero. */
export function UploadDropzone({
  onFilesSelected,
  compact = false,
}: {
  onFilesSelected: (files: File[]) => void
  compact?: boolean
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [isDragging, setIsDragging] = useState(false)

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setIsDragging(false)
    const files = Array.from(event.dataTransfer.files)
    if (files.length > 0) onFilesSelected(files)
  }

  const fileInput = (
    <input
      ref={inputRef}
      type="file"
      multiple
      className="hidden"
      aria-label="Choose files to upload"
      onChange={(event) => {
        const files = Array.from(event.target.files ?? [])
        if (files.length > 0) onFilesSelected(files)
        event.target.value = ''
      }}
    />
  )

  return (
    <div
      onDragOver={(event) => {
        event.preventDefault()
        setIsDragging(true)
      }}
      onDragLeave={() => setIsDragging(false)}
      onDrop={handleDrop}
      className={cn(
        'rounded-[var(--radius-lg)] border border-dashed transition-colors',
        isDragging ? 'border-accent bg-accent-subtle' : 'border-border-strong hover:border-accent/50',
        compact ? 'flex items-center gap-3 px-4 py-3' : 'flex flex-col items-center justify-center gap-3 p-10 text-center',
      )}
    >
      {compact ? (
        <>
          <Upload className="size-4 shrink-0 text-text-muted" aria-hidden="true" />
          <p className="flex-1 text-sm text-text-secondary">{isDragging ? 'Release to upload' : 'Drop files here to add them'}</p>
          <Button variant="secondary" size="sm" onClick={() => inputRef.current?.click()}>
            Browse files
          </Button>
        </>
      ) : (
        <>
          <span className="flex size-12 items-center justify-center rounded-full bg-accent-subtle text-accent-text">
            <Upload className="size-5" aria-hidden="true" />
          </span>
          <p className="text-text-primary">Drop your memories here</p>
          <p className="text-sm text-text-muted">PDFs, images, audio, video & docs</p>
          <Button onClick={() => inputRef.current?.click()}>Browse files</Button>
        </>
      )}
      {fileInput}
    </div>
  )
}
