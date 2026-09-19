import { cn } from '@/lib/utils'
import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

/** Shared empty state: icon, title, one line of guidance, and an optional action. */
export function EmptyState({
  icon: Icon,
  title,
  children,
  action,
  className,
}: {
  icon: LucideIcon
  title: string
  children?: ReactNode
  action?: ReactNode
  className?: string
}) {
  return (
    <div className={cn('flex flex-col items-center px-4 py-14 text-center', className)}>
      <span className="flex size-12 items-center justify-center rounded-full border border-border bg-surface-raised text-text-muted">
        <Icon className="size-5" aria-hidden="true" />
      </span>
      <h2 className="mt-4 text-base font-medium text-text-primary">{title}</h2>
      {children && <p className="mt-1.5 max-w-sm text-sm leading-relaxed text-text-muted">{children}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}
