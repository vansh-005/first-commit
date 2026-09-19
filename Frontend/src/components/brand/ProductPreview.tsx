import { LogoMark } from '@/components/brand/Logo'
import { cn } from '@/lib/utils'
import { FileText, Home, Library, MessageSquare, Music, Search, Sparkles } from 'lucide-react'

/*
 * Static, decorative renderings of the real product UI (search result, memory card, grounded
 * answer with a citation) used on the Landing and Login pages. Built from the same tokens and
 * card language as the app itself so the marketing visual never drifts from the product.
 * Everything here is presentational only and hidden from assistive tech by its callers.
 */

const WAVE = [6, 12, 8, 18, 10, 22, 14, 8, 20, 12, 16, 7, 18, 10, 6, 14, 20, 9, 13, 7]

function Mark({ children }: { children: string }) {
  return <mark className="rounded-sm bg-accent-subtle px-0.5 text-text-primary">{children}</mark>
}

export function ScreenshotThumb({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        'relative flex shrink-0 flex-col gap-1.5 overflow-hidden rounded-[var(--radius-md)] border border-border bg-[#101827] p-2',
        className,
      )}
    >
      <span className="h-1.5 w-1/2 rounded-full bg-[#5b6cff]/70" />
      <span className="h-3 w-3/4 rounded-sm bg-white/85" />
      <span className="h-1 w-full rounded-full bg-white/20" />
      <span className="h-1 w-5/6 rounded-full bg-white/20" />
      <span className="mt-auto h-1.5 w-1/3 rounded-full bg-emerald-400/70" />
    </div>
  )
}

export function DocThumb({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        'flex shrink-0 flex-col gap-1.5 rounded-[var(--radius-md)] border border-border bg-surface-muted p-2.5',
        className,
      )}
    >
      <FileText className="size-3.5 text-text-muted" />
      <span className="h-1 w-full rounded-full bg-border-strong" />
      <span className="h-1 w-5/6 rounded-full bg-border-strong" />
      <span className="h-1 w-2/3 rounded-full bg-border-strong" />
    </div>
  )
}

export function AudioThumb({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        'flex shrink-0 items-center justify-center gap-[2px] rounded-[var(--radius-md)] border border-border bg-surface-muted px-2',
        className,
      )}
    >
      {WAVE.slice(0, 12).map((height, index) => (
        <span key={index} className="w-[2px] rounded-full bg-accent/80" style={{ height }} />
      ))}
    </div>
  )
}

export function PreviewSearchBar({ query }: { query: string }) {
  return (
    <div className="flex items-center gap-2 rounded-[var(--radius-md)] border border-border bg-surface-muted px-3 py-2 text-[13px] text-text-primary">
      <Search className="size-3.5 text-text-muted" />
      <span className="truncate">{query}</span>
      <span className="ml-auto hidden rounded border border-border px-1.5 text-[10px] text-text-muted sm:inline">⌘K</span>
    </div>
  )
}

function ResultRow({
  thumb,
  name,
  meta,
  children,
}: {
  thumb: React.ReactNode
  name: string
  meta: string
  children: React.ReactNode
}) {
  return (
    <div className="flex items-start gap-3 rounded-[var(--radius-lg)] border border-border bg-surface-raised p-3">
      {thumb}
      <div className="min-w-0 flex-1">
        <p className="truncate text-[13px] font-medium text-text-primary">{name}</p>
        <p className="mt-0.5 line-clamp-2 text-xs leading-relaxed text-text-secondary">{children}</p>
        <p className="mt-1.5 text-[11px] text-text-muted">{meta}</p>
      </div>
    </div>
  )
}

export function PreviewSearchResults() {
  return (
    <div className="flex flex-col gap-2.5">
      <ResultRow thumb={<ScreenshotThumb className="h-14 w-14" />} name="Screenshot_2026-09-03.png" meta="Image · Sep 3">
        …<Mark>AWS promotional credits</Mark> available: $200.00 — expires Dec 31…
      </ResultRow>
      <ResultRow thumb={<DocThumb className="h-14 w-14" />} name="Activate-offer-letter.pdf" meta="Document · Aug 28">
        Your <Mark>credits</Mark> apply to eligible services for twelve months from activation…
      </ResultRow>
      <ResultRow thumb={<AudioThumb className="h-14 w-14" />} name="hackathon-standup.m4a" meta="Audio · 2:05 – 2:21">
        …we still have the <Mark>AWS credits</Mark> from the Activate program, so no billing worries…
      </ResultRow>
    </div>
  )
}

export function PreviewAsk() {
  return (
    <div className="flex flex-col gap-3">
      <div className="ml-auto max-w-[85%] rounded-[var(--radius-lg)] rounded-br-sm bg-accent-subtle px-3 py-2 text-[13px] text-text-primary">
        How much AWS credit did I have?
      </div>
      <div className="flex gap-2.5">
        <LogoMark className="mt-0.5 size-5" />
        <div className="min-w-0 flex-1">
          <p className="text-[13px] leading-relaxed text-text-primary">
            You had <strong className="font-semibold">$200</strong> in AWS promotional credits, valid through December 31.
          </p>
          <p className="mb-1.5 mt-3 text-[10px] font-medium uppercase tracking-wider text-text-muted">Sources</p>
          <div className="flex items-center gap-3 rounded-[var(--radius-lg)] border border-border bg-surface-raised p-2.5">
            <ScreenshotThumb className="h-10 w-10" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs font-medium text-text-primary">Screenshot_2026-09-03.png</p>
              <p className="truncate text-[11px] text-text-muted">“AWS promotional credits available: $200.00”</p>
            </div>
            <span className="shrink-0 rounded-[var(--radius-md)] border border-border px-2 py-1 text-[10px] font-medium text-text-secondary">
              Open
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}

/** The large landing-page product visual: a compact app window with search results beside a
 * grounded answer. Mirrors the real AppShell layout (icon rail, top bar, content). */
export function AppWindowPreview() {
  const rail = [Home, Library, Search, MessageSquare]
  return (
    <div
      aria-hidden="true"
      className="overflow-hidden rounded-[var(--radius-lg)] border border-border-strong bg-surface shadow-[0_30px_80px_-20px_rgba(0,0,0,0.55)]"
    >
      <div className="flex items-center gap-1.5 border-b border-border bg-surface-muted px-4 py-2.5">
        <span className="size-2.5 rounded-full bg-border-strong" />
        <span className="size-2.5 rounded-full bg-border-strong" />
        <span className="size-2.5 rounded-full bg-border-strong" />
      </div>
      <div className="flex">
        <div className="hidden w-14 shrink-0 flex-col items-center gap-2 border-r border-border py-4 sm:flex">
          <LogoMark className="mb-3 size-6" />
          {rail.map((Icon, index) => (
            <span
              key={index}
              className={cn(
                'flex size-8 items-center justify-center rounded-[var(--radius-md)]',
                index === 2 ? 'bg-accent-subtle text-accent-text' : 'text-text-muted',
              )}
            >
              <Icon className="size-4" />
            </span>
          ))}
        </div>
        <div className="grid min-w-0 flex-1 gap-6 p-4 sm:p-6 lg:grid-cols-[1.15fr_1fr]">
          <div className="flex flex-col gap-4">
            <PreviewSearchBar query="that AWS credits screenshot" />
            <PreviewSearchResults />
          </div>
          <div className="flex flex-col gap-3 rounded-[var(--radius-lg)] border border-border bg-background/50 p-4">
            <div className="flex items-center gap-1.5 text-[11px] font-medium text-text-muted">
              <Sparkles className="size-3.5 text-accent-text" />
              Ask your memory
            </div>
            <PreviewAsk />
          </div>
        </div>
      </div>
    </div>
  )
}

/** Login illustration: a loose, non-overlapping composition of the product's own memory cards. */
export function MemoryCollage() {
  const card = 'rounded-[var(--radius-lg)] border border-border bg-surface-raised p-3 shadow-[var(--shadow-md)]'
  return (
    <div aria-hidden="true" className="mx-auto flex w-full max-w-[560px] flex-col gap-4">
      <div className="w-[82%] animate-fade-up rounded-[var(--radius-lg)] border border-border bg-surface p-3 shadow-[var(--shadow-md)]">
        <PreviewSearchBar query="the lecture where they explained fading" />
      </div>

      <div className="grid grid-cols-3 gap-3">
        <div className={`${card} -rotate-1 animate-fade-up [animation-delay:120ms]`}>
          <ScreenshotThumb className="h-24 w-full" />
          <p className="mt-2 truncate text-xs font-medium text-text-primary">Screenshot_2026-09-03.png</p>
          <p className="text-[11px] text-text-muted">Image · Ready</p>
        </div>
        <div className={`${card} rotate-1 animate-fade-up [animation-delay:200ms]`}>
          <AudioThumb className="h-24 w-full" />
          <p className="mt-2 truncate text-xs font-medium text-text-primary">lecture-04-fading.m4a</p>
          <p className="flex items-center gap-1 text-[11px] text-text-muted">
            <Music className="size-3" /> 2:05 – 2:21
          </p>
        </div>
        <div className={`${card} -rotate-1 animate-fade-up [animation-delay:280ms]`}>
          <DocThumb className="h-24 w-full" />
          <p className="mt-2 truncate text-xs font-medium text-text-primary">Internship-offer.pdf</p>
          <p className="text-[11px] text-text-muted">Document · Ready</p>
        </div>
      </div>

      <div className="ml-auto w-[92%] animate-fade-up rounded-[var(--radius-lg)] border border-border-strong bg-surface p-3 shadow-[var(--shadow-md)] [animation-delay:360ms]">
        <PreviewAsk />
      </div>
    </div>
  )
}
