import { cn } from '@/lib/utils'
import type { MediaCategory } from '@/types/document'

const TABS: { label: string; value: MediaCategory | 'ALL' }[] = [
  { label: 'All', value: 'ALL' },
  { label: 'Documents', value: 'DOCUMENT' },
  { label: 'Photos', value: 'IMAGE' },
  { label: 'Videos', value: 'VIDEO' },
  { label: 'Audio', value: 'AUDIO' },
]

/** Docs/FRONTEND.md §14 — rounded segmented control, cool accent for the selected pill. */
export function CategoryTabs({
  value,
  onChange,
}: {
  value: MediaCategory | 'ALL'
  onChange: (value: MediaCategory | 'ALL') => void
}) {
  return (
    <div className="flex gap-2">
      {TABS.map((tab) => (
        <button
          key={tab.value}
          onClick={() => onChange(tab.value)}
          className={cn(
            'rounded-full px-3 py-1.5 text-sm transition-colors',
            value === tab.value ? 'bg-accent-subtle text-accent' : 'text-text-secondary hover:bg-surface-muted',
          )}
        >
          {tab.label}
        </button>
      ))}
    </div>
  )
}
