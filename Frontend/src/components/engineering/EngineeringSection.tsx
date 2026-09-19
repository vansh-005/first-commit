import type { ReactNode } from 'react'

/** One major section of the /engineering page: eyebrow, title, one-line lead, then content. */
export function EngineeringSection({
  id,
  eyebrow,
  title,
  lead,
  children,
}: {
  id: string
  eyebrow: string
  title: string
  lead?: string
  children: ReactNode
}) {
  return (
    <section id={id} aria-labelledby={`${id}-title`} className="scroll-mt-20 border-t border-border">
      <div className="mx-auto max-w-6xl px-5 py-16 sm:py-20">
        <p className="text-sm font-medium text-accent-text">{eyebrow}</p>
        <h2 id={`${id}-title`} className="mt-2 max-w-3xl text-3xl font-semibold tracking-tight sm:text-4xl">
          {title}
        </h2>
        {lead && <p className="mt-4 max-w-3xl text-pretty leading-relaxed text-text-secondary">{lead}</p>}
        <div className="mt-10 flex flex-col gap-8">{children}</div>
      </div>
    </section>
  )
}
