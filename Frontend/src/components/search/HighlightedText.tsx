import { queryTerms, splitByTerms } from '@/lib/highlight'

/** Renders `text` with the query's meaningful words wrapped in <mark>. Built from React nodes
 * (never innerHTML), so snippet content can't inject markup. */
export function HighlightedText({ text, query }: { text: string; query: string }) {
  const segments = splitByTerms(text, queryTerms(query))
  return (
    <>
      {segments.map((segment, index) =>
        index % 2 === 1 ? (
          <mark key={index} className="rounded-sm bg-accent-subtle px-0.5 font-medium text-text-primary">
            {segment}
          </mark>
        ) : (
          segment
        ),
      )}
    </>
  )
}
