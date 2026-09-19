import { Wordmark } from '@/components/brand/Logo'
import { cn } from '@/lib/utils'
import { Link } from 'react-router-dom'

const navLink =
  'rounded-[var(--radius-md)] px-3 py-2 text-sm text-text-secondary transition-colors hover:text-text-primary'

/** Shared header for the public pages (Landing, Engineering): brand, page links, sign-in and the primary CTA. */
export function PublicHeader({ current }: { current: 'landing' | 'engineering' }) {
  return (
    <header className="sticky top-0 z-30 border-b border-border/70 bg-background/80 backdrop-blur">
      <nav aria-label="Primary" className="mx-auto flex h-16 max-w-6xl items-center justify-between px-5">
        <Link to="/" aria-label="Recollect home">
          <Wordmark />
        </Link>
        <div className="flex items-center gap-1 sm:gap-2">
          <a
            href={current === 'landing' ? '#how-it-works' : '/#how-it-works'}
            className={cn(navLink, 'hidden md:inline')}
          >
            How it works
          </a>
          <Link
            to="/engineering"
            aria-current={current === 'engineering' ? 'page' : undefined}
            className={cn(navLink, 'hidden sm:inline', current === 'engineering' && 'text-text-primary')}
          >
            Engineering
          </Link>
          <Link to="/login" className={navLink}>
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
  )
}
