import { cn } from '@/lib/utils'
import { NAV_ITEMS } from '@/components/layout/Sidebar'
import { X } from 'lucide-react'
import { NavLink } from 'react-router-dom'

/** Docs/FRONTEND.md §20 — mobile falls back to a top-triggered drawer instead of the
 * resizable desktop sidebar. */
export function MobileNavDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 md:hidden">
      <button
        type="button"
        aria-label="Close navigation"
        onClick={onClose}
        className="absolute inset-0 bg-black/50"
      />
      <div className="relative flex h-full w-64 flex-col border-r border-border bg-surface p-2">
        <div className="flex h-14 items-center justify-between px-2">
          <span className="text-sm font-semibold text-text-primary">Memory Layer</span>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close navigation"
            className="rounded-[var(--radius-sm)] p-1.5 text-text-muted hover:bg-surface-muted hover:text-text-primary"
          >
            <X className="size-4" aria-hidden="true" />
          </button>
        </div>
        <nav className="flex flex-col gap-1">
          {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              onClick={onClose}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-[var(--radius-md)] px-3 py-2 text-sm transition-colors',
                  isActive
                    ? 'bg-accent-subtle text-accent'
                    : 'text-text-secondary hover:bg-surface-muted hover:text-text-primary',
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
