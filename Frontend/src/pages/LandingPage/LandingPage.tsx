import { Wordmark } from '@/components/brand/Logo'
import { AppWindowPreview } from '@/components/brand/ProductPreview'
import { ArrowRight, Lock, MessageSquare, Search, Upload } from 'lucide-react'
import { Link } from 'react-router-dom'

const VALUE_PROPS = [
  {
    icon: Upload,
    title: 'Upload anything',
    body: 'Drop in screenshots, PDFs, recordings and videos. No folders, tags or renaming — just add them.',
  },
  {
    icon: Search,
    title: 'Search naturally',
    body: 'Describe what you remember, not what you named it. Recollect finds the right file and the right moment.',
  },
  {
    icon: MessageSquare,
    title: 'Ask your memory',
    body: 'Ask a question and get an answer grounded in your own files, with a source you can open to check.',
  },
]

const primaryCta =
  'inline-flex h-11 items-center justify-center gap-2 rounded-[var(--radius-md)] bg-accent px-5 text-sm font-medium text-white transition-colors hover:bg-accent-hover'
const secondaryCta =
  'inline-flex h-11 items-center justify-center gap-2 rounded-[var(--radius-md)] border border-border bg-surface-raised px-5 text-sm font-medium text-text-primary transition-colors hover:border-border-strong'

export function LandingPage() {
  return (
    <div className="min-h-screen bg-background text-text-primary">
      <header className="sticky top-0 z-30 border-b border-border/70 bg-background/80 backdrop-blur">
        <nav aria-label="Primary" className="mx-auto flex h-16 max-w-6xl items-center justify-between px-5">
          <Link to="/" aria-label="Recollect home">
            <Wordmark />
          </Link>
          <div className="flex items-center gap-2 sm:gap-3">
            <a
              href="#how-it-works"
              className="hidden rounded-[var(--radius-md)] px-3 py-2 text-sm text-text-secondary transition-colors hover:text-text-primary sm:inline"
            >
              How it works
            </a>
            <Link
              to="/login"
              className="rounded-[var(--radius-md)] px-3 py-2 text-sm text-text-secondary transition-colors hover:text-text-primary"
            >
              Sign in
            </Link>
            <Link
              to="/login"
              aria-label="Start remembering"
              className="inline-flex h-9 items-center rounded-[var(--radius-md)] bg-accent px-4 text-sm font-medium text-white transition-colors hover:bg-accent-hover"
            >
              <span className="sm:hidden">Get started</span>
              <span className="hidden sm:inline">Start remembering</span>
            </Link>
          </div>
        </nav>
      </header>

      <main>
        <section className="relative overflow-hidden">
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-x-0 top-0 h-[520px] bg-[radial-gradient(60%_60%_at_50%_0%,var(--accent-subtle),transparent)]"
          />
          <div className="relative mx-auto max-w-6xl px-5 pb-16 pt-10 sm:pt-12">
            <div className="mx-auto max-w-4xl text-center">
              <h1 className="animate-fade-up text-balance text-4xl font-semibold tracking-tight sm:text-5xl sm:leading-[1.08]">
                Your digital life, remembered.
              </h1>
              <p className="animate-fade-up mx-auto mt-4 max-w-2xl text-pretty text-base leading-relaxed text-text-secondary [animation-delay:80ms] sm:text-lg">
                Upload screenshots, documents, recordings and videos. Later, find them by describing what you
                remember — or ask questions answered from your own files.
              </p>
              <div className="animate-fade-up mt-6 flex flex-col items-center justify-center gap-3 [animation-delay:160ms] sm:flex-row">
                <Link to="/login" className={primaryCta}>
                  Start remembering
                  <ArrowRight className="size-4" aria-hidden="true" />
                </Link>
                <a href="#how-it-works" className={secondaryCta}>
                  See how it works
                </a>
              </div>
            </div>

            <div className="animate-fade-up mx-auto mt-10 max-w-5xl [animation-delay:240ms]">
              <AppWindowPreview />
            </div>
          </div>
        </section>

        <section id="how-it-works" className="scroll-mt-16 border-t border-border">
          <div className="mx-auto max-w-6xl px-5 py-20">
            <div className="max-w-2xl">
              <p className="text-sm font-medium text-accent-text">How it works</p>
              <h2 className="mt-2 text-3xl font-semibold tracking-tight">Store it once. Find it the way you remember it.</h2>
            </div>
            <ul className="mt-12 grid gap-4 md:grid-cols-3">
              {VALUE_PROPS.map(({ icon: Icon, title, body }, index) => (
                <li key={title} className="rounded-[var(--radius-lg)] border border-border bg-surface p-6">
                  <div className="flex items-center justify-between">
                    <span className="flex size-10 items-center justify-center rounded-[var(--radius-md)] bg-accent-subtle text-accent-text">
                      <Icon className="size-5" aria-hidden="true" />
                    </span>
                    <span className="text-xs font-medium text-text-muted">0{index + 1}</span>
                  </div>
                  <h3 className="mt-5 text-lg font-semibold">{title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-text-secondary">{body}</p>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section className="border-t border-border bg-surface">
          <div className="mx-auto flex max-w-6xl flex-col items-start gap-5 px-5 py-14 sm:flex-row sm:items-center">
            <span className="flex size-11 shrink-0 items-center justify-center rounded-full border border-border bg-surface-raised text-text-secondary">
              <Lock className="size-5" aria-hidden="true" />
            </span>
            <div>
              <h2 className="text-lg font-semibold">Private by design</h2>
              <p className="mt-1 max-w-3xl text-sm leading-relaxed text-text-secondary">
                Your files are stored privately and encrypted at rest. Search and answers are scoped to your
                account.
              </p>
            </div>
          </div>
        </section>

        <section className="border-t border-border">
          <div className="mx-auto max-w-3xl px-5 py-24 text-center">
            <h2 className="text-3xl font-semibold tracking-tight sm:text-4xl">Stop searching folders. Start remembering.</h2>
            <p className="mt-4 text-text-secondary">Sign in with Google and add your first files in under a minute.</p>
            <div className="mt-8 flex justify-center">
              <Link to="/login" className={primaryCta}>
                Start remembering
                <ArrowRight className="size-4" aria-hidden="true" />
              </Link>
            </div>
          </div>
        </section>
      </main>

      <footer className="border-t border-border">
        <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-3 px-5 py-8 text-sm text-text-muted sm:flex-row sm:items-center">
          <Wordmark />
          <p>Your digital life, remembered.</p>
        </div>
      </footer>

    </div>
  )
}
