// The chart's axis: cosine-similarity scores observed against the current test corpus.
const MIN = 0.5
const MAX = 0.9
const pct = (value: number) => `${((value - MIN) / (MAX - MIN)) * 100}%`

const BANDS = [
  { label: 'Topics that don’t exist', from: 0.52, to: 0.6, tone: 'bg-text-muted/60', note: 'best score ≤ 0.60' },
  { label: 'Natural paraphrases', from: 0.64, to: 0.73, tone: 'bg-accent/70', note: '0.64 – 0.73' },
  { label: 'Clear matches', from: 0.74, to: 0.85, tone: 'bg-accent', note: '0.74 – 0.85' },
]

/**
 * Where the relevance threshold sits. Illustrative aggregate ranges from labelled test queries — no per-user data.
 * Presented as corpus-calibrated and tunable, not as a universal constant.
 */
export function ScoreRangeChart({ threshold }: { threshold: number }) {
  return (
    <figure
      role="img"
      aria-label={`Similarity score ranges: topics that don't exist score up to 0.60, genuine matches score 0.64 to 0.85, and the relevance threshold is ${threshold}.`}
      className="rounded-[var(--radius-lg)] border border-border bg-surface p-5"
    >
      <div className="relative ml-0 mr-4 flex flex-col gap-4 pt-6 sm:ml-44">
        <div
          aria-hidden="true"
          className="absolute -top-0.5 bottom-6 z-10 border-l-2 border-dashed border-text-primary/70"
          style={{ left: pct(threshold) }}
        >
          <span className="absolute -top-6 left-1 whitespace-nowrap rounded-full border border-border bg-background px-2 py-0.5 text-[11px] font-medium text-text-primary">
            threshold {threshold}
          </span>
        </div>

        {BANDS.map((band) => (
          <div key={band.label} className="relative">
            <p className="mb-1 text-xs text-text-secondary sm:absolute sm:-left-44 sm:top-0 sm:mb-0 sm:w-40 sm:text-right sm:leading-5">
              {band.label}
            </p>
            <div aria-hidden="true" className="relative h-5 rounded-full bg-surface-muted">
              <div
                className={`absolute inset-y-0 rounded-full ${band.tone}`}
                style={{ left: pct(band.from), width: `${((band.to - band.from) / (MAX - MIN)) * 100}%` }}
              />
            </div>
            <p className="mt-1 text-[11px] text-text-muted">{band.note}</p>
          </div>
        ))}

        <div aria-hidden="true" className="relative mt-1 h-4 text-[11px] text-text-muted">
          {[0.5, 0.6, 0.7, 0.8, 0.9].map((tick) => (
            <span key={tick} className="absolute -translate-x-1/2" style={{ left: pct(tick) }}>
              {tick.toFixed(1)}
            </span>
          ))}
        </div>
      </div>
      <figcaption className="mt-4 text-xs text-text-muted">
        Similarity of the best-matching chunk per query · ~75 labelled queries on the current test corpus. Corpus-calibrated and
        tunable — not a universal constant.
      </figcaption>
    </figure>
  )
}
