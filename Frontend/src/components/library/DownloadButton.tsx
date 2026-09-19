import { cn } from '@/lib/utils'
import { Download, LoaderCircle } from 'lucide-react'

/** Compact icon button; positioned by the parent (never nested inside another button). */
export function DownloadButton({
  fileName,
  onClick,
  busy,
  className,
}: {
  fileName: string
  onClick: () => void
  busy: boolean
  className?: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={busy}
      aria-label={`Download ${fileName}`}
      title="Download"
      className={cn(
        'flex size-8 items-center justify-center rounded-[var(--radius-md)] border border-border bg-surface/90 text-text-secondary backdrop-blur transition-colors hover:border-accent/50 hover:text-text-primary disabled:opacity-60',
        className,
      )}
    >
      {busy ? <LoaderCircle className="size-4 animate-spin" aria-hidden="true" /> : <Download className="size-4" aria-hidden="true" />}
    </button>
  )
}
