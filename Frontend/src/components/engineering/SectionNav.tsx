import { cn } from '@/lib/utils'
import { useEffect, useRef, useState } from 'react'

export interface SectionNavItem {
  id: string
  label: string
}

// Below the sticky site header (h-16 = 64px) plus this bar; a section counts as "current" once its top passes here.
const ACTIVE_OFFSET = 150

/**
 * Contained, sticky index of the page's sections. Stays in the first viewport (placed right under the hero), highlights
 * the section being read, and scrolls sideways on narrow screens instead of wrapping.
 */
export function SectionNav({ items }: { items: readonly SectionNavItem[] }) {
  const [active, setActive] = useState<string | null>(null)
  const listRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let frame = 0
    const update = () => {
      frame = 0
      let current: string | null = null
      for (const { id } of items) {
        const rect = document.getElementById(id)?.getBoundingClientRect()
        if (rect && rect.top <= ACTIVE_OFFSET && rect.bottom > ACTIVE_OFFSET) {
          current = id
        }
      }
      setActive(current)
    }
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update)
    }
    update()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
      if (frame) cancelAnimationFrame(frame)
    }
  }, [items])

  // On narrow screens keep the highlighted item visible inside the horizontal scroller (without moving the page).
  useEffect(() => {
    const list = listRef.current
    const link = active ? list?.querySelector<HTMLElement>(`[data-section="${active}"]`) : null
    if (list && link && list.scrollWidth > list.clientWidth) {
      list.scrollTo?.({ left: link.offsetLeft - (list.clientWidth - link.offsetWidth) / 2, behavior: 'smooth' })
    }
  }, [active])

  return (
    <div className="sticky top-16 z-20 border-b border-border/70 bg-background/85 py-3 backdrop-blur">
      <nav aria-label="On this page" className="mx-auto max-w-6xl px-5">
        <div
          ref={listRef}
          className="flex gap-1 overflow-x-auto rounded-[var(--radius-lg)] border border-border bg-surface p-1.5 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        >
          {items.map(({ id, label }) => (
            <a
              key={id}
              href={`#${id}`}
              data-section={id}
              aria-current={active === id ? 'true' : undefined}
              className={cn(
                'shrink-0 whitespace-nowrap rounded-[var(--radius-md)] px-3.5 py-2 text-sm transition-colors',
                active === id
                  ? 'bg-accent-subtle font-medium text-accent-text'
                  : 'text-text-secondary hover:bg-surface-muted hover:text-text-primary',
              )}
            >
              {label}
            </a>
          ))}
        </div>
      </nav>
    </div>
  )
}
