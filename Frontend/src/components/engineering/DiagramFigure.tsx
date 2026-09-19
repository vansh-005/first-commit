import { ExternalLink } from 'lucide-react'

interface DiagramFigureProps {
  /** The SVG markup, inlined so it inherits the site's theme tokens (dark/light, cobalt accent). */
  svg: string
  /** URL of the same SVG as a standalone file, for "open full size". */
  fullSizeUrl: string
  caption: string
  /** Numbered explanations matching the badges drawn in the diagram. */
  legend?: string[]
  /** Below this container width the diagram scrolls horizontally instead of shrinking illegibly. */
  minWidth?: number
}

/**
 * A canonical architecture diagram from Docs/diagrams. The SVG is our own static, build-time asset (never user
 * content), inlined so its colours follow the page's design tokens. The numbered legend doubles as the text
 * alternative; the SVG itself carries <title>/<desc>.
 */
export function DiagramFigure({ svg, fullSizeUrl, caption, legend, minWidth = 1000 }: DiagramFigureProps) {
  return (
    // Breaks out of the text column (up to 1400px, always 1.5rem from the viewport edges) so the diagram's small type
    // stays legible; below ~1000px it scrolls horizontally instead of shrinking further.
    <figure
      style={{ marginInline: 'calc((100% - min(1400px, 100vw - 3rem)) / 2)' }}
      className="rounded-[var(--radius-lg)] border border-border bg-surface p-3 sm:p-5"
    >
      <div className="overflow-x-auto rounded-[var(--radius-md)]" tabIndex={0} aria-label={`${caption} — scroll to explore`}>
        <div
          style={{ minWidth }}
          className="[&>svg]:block [&>svg]:h-auto [&>svg]:w-full"
          dangerouslySetInnerHTML={{ __html: svg }}
        />
      </div>

      <figcaption className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-text-muted">
        <span>{caption}</span>
        <a
          href={fullSizeUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1.5 text-accent-text transition-colors hover:text-text-primary"
        >
          Open full size <ExternalLink className="size-3.5" aria-hidden="true" />
        </a>
      </figcaption>

      {legend && (
        <ol className="mt-4 grid gap-x-10 gap-y-2.5 border-t border-border pt-4 sm:grid-cols-2">
          {legend.map((text, index) => (
            <li key={text} className="flex gap-3 text-sm leading-relaxed text-text-secondary">
              <span
                aria-hidden="true"
                className="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full bg-accent text-[11px] font-semibold text-white"
              >
                {index + 1}
              </span>
              <span>{text}</span>
            </li>
          ))}
        </ol>
      )}
    </figure>
  )
}
