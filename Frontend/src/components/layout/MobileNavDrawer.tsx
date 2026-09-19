import { Wordmark } from '@/components/brand/Logo'
import { NAV_ITEMS } from '@/components/layout/navItems'
import { useDialogA11y } from '@/hooks/useDialogA11y'
import { cn } from '@/lib/utils'
import { Plus, X } from 'lucide-react'
import { NavLink } from 'react-router-dom'

/** Docs/FRONTEND.md §20 — mobile falls back to a top-triggered drawer instead of the
 * resizable desktop sidebar. A real modal dialog: focus moves in, Tab is trapped, Escape closes
 * and focus returns to the menu button. */
export function MobileNavDrawer({
  open,
  onClose,
  onUploadClick,
}: {
  open: boolean
  onClose: () => void
  onUploadClick?: () => void
}) {
  const panelRef = useDialogA11y(open, onClose)

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 md:hidden">
      <button type="button" aria-label="Close navigation" tabIndex={-1} onClick={onClose} className="absolute inset-0 bg-black/50" />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label="Navigation"
        tabIndex={-1}
        className="relative flex h-full w-72 max-w-[85vw] flex-col border-r border-border bg-surface p-3"
      >
        <div className="flex h-12 items-center justify-between px-1">
          <Wordmark />
          <button
            type="button"
            onClick={onClose}
            aria-label="Close navigation"
            className="rounded-[var(--radius-sm)] p-1.5 text-text-muted hover:bg-surface-muted hover:text-text-primary"
          >
            <X className="size-4" aria-hidden="true" />
          </button>
        </div>

        <button
          type="button"
          onClick={() => {
            onClose()
            onUploadClick?.()
          }}
          className="mt-2 flex h-10 items-center gap-2 rounded-[var(--radius-md)] bg-accent px-3 text-sm font-medium text-white transition-colors hover:bg-accent-hover"
        >
          <Plus className="size-4" aria-hidden="true" />
          Upload
        </button>

        <nav aria-label="Main" className="mt-3 flex flex-col gap-0.5">
          {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              onClick={onClose}
              className={({ isActive }) =>
                cn(
                  'flex h-10 items-center gap-3 rounded-[var(--radius-md)] px-3 text-sm transition-colors',
                  isActive ? 'bg-surface-muted font-medium text-text-primary' : 'text-text-secondary hover:bg-surface-muted/60 hover:text-text-primary',
                )
              }
            >
              <Icon className="size-4 shrink-0" aria-hidden="true" />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
      </div>
    </div>
  )
}
