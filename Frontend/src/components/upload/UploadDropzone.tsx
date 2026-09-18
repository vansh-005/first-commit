import { Button } from '@/components/ui/button'
import { useRef, useState, type DragEvent } from 'react'

/** Docs/FRONTEND.md §13. Does not force file-type or destination selection. */
export function UploadDropzone({ onFilesSelected }: { onFilesSelected: (files: File[]) => void }) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [isDragging, setIsDragging] = useState(false)

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setIsDragging(false)
    const files = Array.from(event.dataTransfer.files)
    if (files.length > 0) onFilesSelected(files)
  }

  return (
    <div
      onDragOver={(event) => {
        event.preventDefault()
        setIsDragging(true)
      }}
      onDragLeave={() => setIsDragging(false)}
      onDrop={handleDrop}
      className={`flex flex-col items-center justify-center gap-3 rounded-[var(--radius-lg)] border border-dashed p-10 text-center transition-colors ${
        isDragging ? 'border-accent bg-accent-subtle' : 'border-border'
      }`}
    >
      <p className="text-text-primary">Drop your memories here</p>
      <p className="text-sm text-text-muted">PDFs, images, audio, video & docs</p>
      <Button onClick={() => inputRef.current?.click()}>Browse files</Button>
      <input
        ref={inputRef}
        type="file"
        multiple
        className="hidden"
        onChange={(event) => {
          const files = Array.from(event.target.files ?? [])
          if (files.length > 0) onFilesSelected(files)
          event.target.value = ''
        }}
      />
    </div>
  )
}
