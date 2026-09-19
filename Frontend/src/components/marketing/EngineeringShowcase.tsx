import { ArrowRight, CloudUpload, Layers, Quote, ShieldCheck } from 'lucide-react'
import { Link } from 'react-router-dom'

const PROOF_POINTS = [
  { icon: CloudUpload, title: 'Direct-to-S3 uploads', body: 'Files go from your browser straight to storage.' },
  { icon: Layers, title: 'Async ingestion with backpressure', body: 'A queue absorbs bursts; indexing never blocks you.' },
  { icon: ShieldCheck, title: 'Tenant-isolated retrieval', body: 'Every search is filtered to your account, server-side.' },
  { icon: Quote, title: 'Grounded answers with citations', body: 'Answers come only from your files, with sources.' },
]

const FLOW = ['S3', 'SQS', 'Bedrock', 'S3 Vectors']

/** Landing-page teaser for /engineering: enough proof to earn the click, not documentation. Secondary to the main CTA. */
export function EngineeringShowcase() {
  return (
    <section aria-labelledby="engineering-showcase-title" className="border-t border-border">
      <div className="mx-auto max-w-6xl px-5 py-16 sm:py-20">
        <div className="grid items-start gap-10 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
          <div>
            <p className="text-sm font-medium text-accent-text">Engineering</p>
            <h2 id="engineering-showcase-title" className="mt-2 text-balance text-3xl font-semibold tracking-tight sm:text-4xl">
              Built like a real system, not a demo.
            </h2>
            <p className="mt-4 max-w-md text-pretty leading-relaxed text-text-secondary">
              Recollect uses direct-to-S3 uploads, asynchronous ingestion, tenant-scoped semantic retrieval, grounded
              generation and production observability on AWS.
            </p>
            <Link
              to="/engineering"
              className="mt-7 inline-flex h-11 items-center gap-2 rounded-[var(--radius-md)] border border-accent/40 bg-accent-subtle px-5 text-sm font-medium text-accent-text transition-colors hover:border-accent hover:text-text-primary"
            >
              Explore the architecture
              <ArrowRight className="size-4" aria-hidden="true" />
            </Link>
          </div>

          <ul className="grid gap-3 sm:grid-cols-2">
            {PROOF_POINTS.map(({ icon: Icon, title, body }) => (
              <li key={title} className="rounded-[var(--radius-lg)] border border-border bg-surface p-4">
                <span className="flex size-8 items-center justify-center rounded-[var(--radius-md)] bg-accent-subtle text-accent-text">
                  <Icon className="size-4" aria-hidden="true" />
                </span>
                <h3 className="mt-3 text-sm font-semibold">{title}</h3>
                <p className="mt-1 text-sm leading-relaxed text-text-secondary">{body}</p>
              </li>
            ))}
          </ul>
        </div>

        <div className="mt-8 rounded-[var(--radius-lg)] border border-border bg-surface px-5 py-4">
          <ol aria-label="Ingestion flow" className="flex flex-wrap items-center gap-x-3 gap-y-2 text-sm">
            {FLOW.map((step, index) => (
              <li key={step} className="flex items-center gap-3">
                <span className="rounded-full border border-accent/40 bg-accent-subtle px-3 py-1 font-medium text-accent-text">{step}</span>
                {index < FLOW.length - 1 && <ArrowRight className="size-4 text-text-muted" aria-hidden="true" />}
              </li>
            ))}
          </ol>
          <p className="mt-2.5 text-xs text-text-muted">Lambda orchestrates each step; DynamoDB holds the state.</p>
        </div>
      </div>
    </section>
  )
}
