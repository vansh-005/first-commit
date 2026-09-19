import { cn } from '@/lib/utils'
import type { MediaCategory } from '@/types/document'

export const CATEGORY_TABS: { label: string; value: MediaCategory | 'ALL' }[] = [
  { label: 'All', value: 'ALL' },
  { label: 'Documents', value: 'DOCUMENT' },
  { label: 'Photos', value: 'IMAGE' },
  { label: 'Videos', value: 'VIDEO' },
  { label: 'Audio', value: 'AUDIO' },
]

/** Docs/FRONTEND.md §14 — rounded segmented control, cool accent for the selected segment.
 * Shared by Library and Search so both filter the same way. */
export function CategoryTabs({
  value,
  onChange,
  label = 'Filter by type',
}: {
  value: MediaCategory | 'ALL'
  onChange: (value: MediaCategory | 'ALL') => void
  label?: string
}) {
  return (
    <div role="group" aria-label={label} className="inline-flex max-w-full gap-1 overflow-x-auto rounded-full border border-border bg-surface p-1">
      {CATEGORY_TABS.map((tab) => (
        <button
          key={tab.value}
          type="button"
          aria-pressed={value === tab.value}
          onClick={() => onChange(tab.value)}
          className={cn(
            'shrink-0 rounded-full px-3.5 py-1.5 text-sm transition-colors',
            value === tab.value
              ? 'bg-accent-subtle font-medium text-accent-text'
              : 'text-text-secondary hover:bg-surface-muted hover:text-text-primary',
          )}
        >
          {tab.label}
        </button>
      ))}
    </div>
  )
}
