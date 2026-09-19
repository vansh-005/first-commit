import { Wordmark } from '@/components/brand/Logo'
import { GITHUB_URL } from '@/lib/links'
import { Link } from 'react-router-dom'

const footerLink = 'transition-colors hover:text-text-primary'

export function PublicFooter() {
  return (
    <footer className="border-t border-border">
      <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-4 px-5 py-8 text-sm text-text-muted sm:flex-row sm:items-center">
        <div className="flex flex-col gap-2">
          <Wordmark />
          <p>Your digital life, remembered.</p>
        </div>
        <nav aria-label="Footer" className="flex flex-wrap items-center gap-x-5 gap-y-2">
          <Link to="/engineering" className={footerLink}>
            Engineering
          </Link>
          <Link to="/login" className={footerLink}>
            Sign in
          </Link>
          <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className={footerLink}>
            GitHub
          </a>
        </nav>
      </div>
    </footer>
  )
}
