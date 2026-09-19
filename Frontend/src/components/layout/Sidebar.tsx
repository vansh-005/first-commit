import { Wordmark } from '@/components/brand/Logo'
import { NAV_ITEMS } from '@/components/layout/navItems'
import { COLLAPSED_WIDTH, useSidebarState } from '@/hooks/useSidebarState'
import { cn } from '@/lib/utils'
import { PanelLeftClose, PanelLeftOpen, Plus } from 'lucide-react'
import { useRef } from 'react'
import { NavLink } from 'react-router-dom'

/**
 * Docs/FRONTEND.md §6. Resizable (drag the right edge, or arrow keys on the handle) and
 * collapsible (icon rail <-> expanded), remembering the viewer's choice locally via
 * useSidebarState. Keeps recognizable icons + tooltips when collapsed.
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

  function onResizeKeyDown(event: React.KeyboardEvent) {
    if (event.key === 'ArrowLeft') setWidth(Math.max(minWidth, width - 16))
    if (event.key === 'ArrowRight') setWidth(Math.min(maxWidth, width + 16))
  }

  return (
    <aside
      style={{ width: collapsed ? COLLAPSED_WIDTH : width }}
      className="relative hidden h-screen shrink-0 flex-col border-r border-border bg-surface transition-[width] duration-150 md:flex"
    >
      <div className={cn('flex h-14 items-center px-4', collapsed && 'justify-center px-0')}>
        <Wordmark showText={!collapsed} />
      </div>

      <div className="px-2 pb-2 pt-1">
        <button
          type="button"
          onClick={onUploadClick}
          aria-label="Upload"
          title={collapsed ? 'Upload' : undefined}
          className={cn(
            'flex h-9 w-full items-center gap-2 rounded-[var(--radius-md)] bg-accent px-3 text-sm font-medium text-white transition-colors hover:bg-accent-hover',
            collapsed && 'justify-center px-0',
          )}
        >
          <Plus className="size-4 shrink-0" aria-hidden="true" />
          {!collapsed && <span>Upload</span>}
        </button>
      </div>

      <nav aria-label="Main" className="flex flex-1 flex-col gap-0.5 px-2 py-1">
        {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            title={collapsed ? label : undefined}
            aria-label={collapsed ? label : undefined}
            className={({ isActive }) =>
              cn(
                'group flex h-9 items-center gap-3 rounded-[var(--radius-md)] px-3 text-sm transition-colors',
                collapsed && 'justify-center px-0',
                isActive
                  ? 'bg-surface-muted font-medium text-text-primary'
                  : 'text-text-secondary hover:bg-surface-muted/60 hover:text-text-primary',
              )
            }
          >
            {({ isActive }) => (
              <>
                <Icon
                  className={cn('size-4 shrink-0 transition-colors', isActive ? 'text-accent-text' : 'text-text-muted group-hover:text-text-secondary')}
                  aria-hidden="true"
                />
                {!collapsed && <span className="truncate">{label}</span>}
              </>
            )}
          </NavLink>
        ))}
      </nav>

      <div className="border-t border-border p-2">
        <button
          type="button"
          onClick={toggleCollapsed}
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          title={collapsed ? 'Expand sidebar' : undefined}
          className={cn(
            'flex h-9 w-full items-center gap-3 rounded-[var(--radius-md)] px-3 text-sm text-text-muted transition-colors hover:bg-surface-muted hover:text-text-primary',
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
          role="separator"
          aria-orientation="vertical"
          aria-label="Resize sidebar"
          aria-valuenow={width}
          aria-valuemin={minWidth}
          aria-valuemax={maxWidth}
          tabIndex={0}
          onMouseDown={startResize}
          onKeyDown={onResizeKeyDown}
          className="absolute right-0 top-0 h-full w-1 cursor-col-resize transition-colors hover:bg-accent-subtle focus-visible:bg-accent-subtle"
        />
      )}
    </aside>
  )
}
