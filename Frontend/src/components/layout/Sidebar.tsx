import { cn } from '@/lib/utils'
import { COLLAPSED_WIDTH, useSidebarState } from '@/hooks/useSidebarState'
import { Home, Library, MessageSquare, PanelLeftClose, PanelLeftOpen, Search, Upload } from 'lucide-react'
import { useRef } from 'react'
import { NavLink } from 'react-router-dom'

export const NAV_ITEMS = [
  { to: '/app', label: 'Home', icon: Home, end: true },
  { to: '/app/library', label: 'Library', icon: Library, end: false },
  { to: '/app/search', label: 'Search', icon: Search, end: false },
  { to: '/app/ask', label: 'Ask', icon: MessageSquare, end: false },
] as const

/**
 * Docs/FRONTEND.md §6. Resizable (drag the right edge) and collapsible (icon rail <->
 * expanded), remembering the viewer's choice locally via useSidebarState. Keeps recognizable
 * icons + tooltips when collapsed.
 */
export function Sidebar({ onUploadClick }: { onUploadClick?: () => void }) {
  const { collapsed, width, toggleCollapsed, setWidth, minWidth, maxWidth } = useSidebarState()
  const draggingRef = useRef(false)

  function startResize(event: React.MouseEvent) {
    if (collapsed) return
    event.preventDefault()
    draggingRef.current = true

    function onMouseMove(moveEvent: MouseEvent) {
      if (!draggingRef.current) return
      setWidth(Math.min(maxWidth, Math.max(minWidth, moveEvent.clientX)))
    }
    function onMouseUp() {
      draggingRef.current = false
      window.removeEventListener('mousemove', onMouseMove)
      window.removeEventListener('mouseup', onMouseUp)
    }
    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
  }

  return (
    <aside
      style={{ width: collapsed ? COLLAPSED_WIDTH : width }}
      className="relative hidden h-screen shrink-0 flex-col border-r border-border bg-surface transition-[width] duration-150 md:flex"
    >
      <div className={cn('flex h-14 items-center border-b border-border px-4', collapsed && 'justify-center px-0')}>
        {!collapsed && <span className="truncate text-sm font-semibold text-text-primary">Memory Layer</span>}
      </div>

      <nav className="flex flex-1 flex-col gap-1 p-2">
        {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            title={collapsed ? label : undefined}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 rounded-[var(--radius-md)] px-3 py-2 text-sm transition-colors',
                collapsed && 'justify-center px-0',
                isActive
                  ? 'bg-accent-subtle text-accent'
                  : 'text-text-secondary hover:bg-surface-muted hover:text-text-primary',
              )
            }
          >
            <Icon className="size-4 shrink-0" aria-hidden="true" />
            {!collapsed && <span className="truncate">{label}</span>}
          </NavLink>
        ))}
      </nav>

      <div className="p-2">
        <button
          type="button"
          onClick={onUploadClick}
          title={collapsed ? 'Upload' : undefined}
          className={cn(
            'flex w-full items-center gap-3 rounded-[var(--radius-md)] bg-accent px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-accent-hover',
            collapsed && 'justify-center px-0',
          )}
        >
          <Upload className="size-4 shrink-0" aria-hidden="true" />
          {!collapsed && <span>Upload</span>}
        </button>
      </div>

      <div className="border-t border-border p-2">
        <button
          type="button"
          onClick={toggleCollapsed}
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          className={cn(
            'flex w-full items-center gap-3 rounded-[var(--radius-md)] px-3 py-2 text-sm text-text-muted transition-colors hover:bg-surface-muted hover:text-text-primary',
            collapsed && 'justify-center px-0',
          )}
        >
          {collapsed ? (
            <PanelLeftOpen className="size-4 shrink-0" aria-hidden="true" />
          ) : (
            <PanelLeftClose className="size-4 shrink-0" aria-hidden="true" />
          )}
          {!collapsed && <span>Collapse</span>}
        </button>
      </div>

      {!collapsed && (
        <div
          onMouseDown={startResize}
          className="absolute right-0 top-0 h-full w-1 cursor-col-resize hover:bg-accent-subtle"
          aria-hidden="true"
        />
      )}
    </aside>
  )
}
