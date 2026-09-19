import { cn } from '@/lib/utils'

/** Recollect mark: a ring with a centre point — a memory being brought back into focus. */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 28 28" className={cn('size-7 shrink-0', className)} aria-hidden="true">
      <rect width="28" height="28" rx="8" fill="var(--accent)" />
      <circle cx="14" cy="14" r="6.25" fill="none" stroke="#fff" strokeWidth="2" strokeDasharray="30 9.3" strokeLinecap="round" />
      <circle cx="14" cy="14" r="2.1" fill="#fff" />
    </svg>
  )
}

export function Wordmark({ className, showText = true }: { className?: string; showText?: boolean }) {
  return (
    <span className={cn('inline-flex items-center gap-2.5', className)}>
      <LogoMark />
      {showText && <span className="text-[15px] font-semibold tracking-tight text-text-primary">Recollect</span>}
    </span>
  )
}
