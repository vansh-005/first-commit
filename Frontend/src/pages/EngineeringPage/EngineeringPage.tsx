import highLevelSvg from '../../../../Docs/diagrams/high-level-architecture.svg?raw'
import highLevelUrl from '../../../../Docs/diagrams/high-level-architecture.svg?url'
import searchAskSvg from '../../../../Docs/diagrams/search-ask.svg?raw'
import searchAskUrl from '../../../../Docs/diagrams/search-ask.svg?url'
import uploadSvg from '../../../../Docs/diagrams/upload-ingestion.svg?raw'
import uploadUrl from '../../../../Docs/diagrams/upload-ingestion.svg?url'
import { GithubIcon } from '@/components/brand/GithubIcon'
import { DiagramFigure } from '@/components/engineering/DiagramFigure'
import { EngineeringSection } from '@/components/engineering/EngineeringSection'
import { ScoreRangeChart } from '@/components/engineering/ScoreRangeChart'
import { PublicFooter } from '@/components/marketing/PublicFooter'
import { PublicHeader } from '@/components/marketing/PublicHeader'
import { usePageTitle } from '@/hooks/usePageTitle'
import { GITHUB_URL } from '@/lib/links'
import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import {
  ACCESS_PATTERNS,
  API_NOTES,
  API_ROWS,
  ARCHITECTURE_LEGEND,
  ASK_OUTCOMES,
  CHALLENGES,
  COST_NOTES,
  COST_ROWS,
  DATA_MODEL_ROWS,
  DECISIONS,
  FUTURE,
  HERO,
  INGESTION_LEGEND,
  RELIABILITY_CARDS,
  SEARCH_POINTS,
  SECURITY_CARDS,
  TRADEOFFS,
  UPLOAD_CALLOUTS,
  type Card,
} from './content'

const primaryCta =
  'inline-flex h-11 items-center justify-center gap-2 rounded-[var(--radius-md)] bg-accent px-5 text-sm font-medium text-white transition-colors hover:bg-accent-hover'
const secondaryCta =
  'inline-flex h-11 items-center justify-center gap-2 rounded-[var(--radius-md)] border border-border bg-surface-raised px-5 text-sm font-medium text-text-primary transition-colors hover:border-border-strong'

const TOC = [
  ['architecture', 'Architecture'],
  ['upload', 'Upload'],
  ['retrieval', 'Retrieval'],
  ['security', 'Security'],
  ['data-model', 'Data model'],
  ['reliability', 'Reliability'],
  ['decisions', 'Decisions & cost'],
  ['challenges', 'Challenges'],
  ['tradeoffs', 'Trade-offs'],
  ['api', 'API'],
] as const

function CardGrid({ cards, columns = 3 }: { cards: Card[]; columns?: 2 | 3 | 4 }) {
  const cols = columns === 2 ? 'sm:grid-cols-2' : columns === 4 ? 'sm:grid-cols-2 lg:grid-cols-4' : 'sm:grid-cols-2 lg:grid-cols-3'
  return (
    <ul className={`grid gap-4 ${cols}`}>
      {cards.map(({ icon: Icon, title, body }) => (
        <li key={title} className="rounded-[var(--radius-lg)] border border-border bg-surface p-5">
          <span className="flex size-9 items-center justify-center rounded-[var(--radius-md)] bg-accent-subtle text-accent-text">
            <Icon className="size-[18px]" aria-hidden="true" />
          </span>
          <h3 className="mt-4 text-base font-semibold">{title}</h3>
          <p className="mt-1.5 text-sm leading-relaxed text-text-secondary">{body}</p>
        </li>
      ))}
    </ul>
  )
}

function SubHeading({ children }: { children: React.ReactNode }) {
  return <h3 className="text-lg font-semibold tracking-tight">{children}</h3>
}

const th = 'px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-text-muted'
const td = 'px-4 py-3 align-top text-sm text-text-secondary'

/**
 * Public engineering showcase (no authentication, no API calls). Diagrams are the canonical SVGs from
 * Docs/diagrams, inlined so they follow the site's theme. Copy lives in ./content.ts. Nothing here may name an
 * account, bucket, ARN, pool/client id, email or user id.
 */
export function EngineeringPage() {
  usePageTitle('Engineering')

  return (
    <div className="min-h-screen bg-background text-text-primary">
      <PublicHeader current="engineering" />

      <main>
        {/* ---------------------------------------------------------------- Hero */}
        <section className="relative overflow-hidden">
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-x-0 top-0 h-[460px] bg-[radial-gradient(60%_60%_at_50%_0%,var(--accent-subtle),transparent)]"
          />
          <div className="relative mx-auto max-w-6xl px-5 pb-16 pt-16 sm:pt-24">
            <p className="animate-fade-up text-sm font-medium text-accent-text">{HERO.eyebrow}</p>
            <h1 className="animate-fade-up mt-3 max-w-4xl text-balance text-4xl font-semibold tracking-tight [animation-delay:60ms] sm:text-6xl sm:leading-[1.05]">
              {HERO.title}
            </h1>
            <p className="animate-fade-up mt-6 max-w-3xl text-pretty text-base leading-relaxed text-text-secondary [animation-delay:120ms] sm:text-lg">
              {HERO.lead}
            </p>

            <ul className="animate-fade-up mt-7 flex flex-wrap gap-2 [animation-delay:180ms]" aria-label="Highlights">
              {HERO.chips.map((chip) => (
                <li key={chip} className="rounded-full border border-border bg-surface-raised px-3.5 py-1.5 text-sm text-text-secondary">
                  {chip}
                </li>
              ))}
            </ul>

            <div className="animate-fade-up mt-9 flex flex-col gap-3 [animation-delay:240ms] sm:flex-row">
              <Link to="/login" className={primaryCta}>
                Try Recollect
                <ArrowRight className="size-4" aria-hidden="true" />
              </Link>
              <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className={secondaryCta}>
                <GithubIcon className="size-4" />
                View GitHub
              </a>
            </div>

            <dl className="mt-14 grid gap-px overflow-hidden rounded-[var(--radius-lg)] border border-border bg-border sm:grid-cols-2 lg:grid-cols-4">
              {HERO.facts.map((fact) => (
                <div key={fact.label} className="bg-surface p-5">
                  <dt className="text-xs font-medium uppercase tracking-wider text-text-muted">{fact.label}</dt>
                  <dd className="mt-1.5 text-sm font-medium">{fact.value}</dd>
                </div>
              ))}
            </dl>

            <nav aria-label="On this page" className="mt-8 flex gap-2 overflow-x-auto pb-1">
              {TOC.map(([id, label]) => (
                <a
                  key={id}
                  href={`#${id}`}
                  className="shrink-0 rounded-full px-3 py-1.5 text-sm text-text-muted transition-colors hover:bg-surface-muted hover:text-text-primary"
                >
                  {label}
                </a>
              ))}
            </nav>
          </div>
        </section>

        {/* ---------------------------------------------------------------- Architecture */}
        <EngineeringSection
          id="architecture"
          eyebrow="High-Level Architecture"
          title="Serverless from the browser to the vector index"
          lead="The browser gets the app from Amplify, identity from Cognito and everything else from one API. File bytes never touch the backend."
        >
          <DiagramFigure
            svg={highLevelSvg}
            fullSizeUrl={highLevelUrl}
            caption="Recollect high-level architecture (deployed system)"
            legend={ARCHITECTURE_LEGEND}
          />
        </EngineeringSection>

        {/* ---------------------------------------------------------------- Upload */}
        <EngineeringSection
          id="upload"
          eyebrow="From Upload to Memory"
          title="Direct uploads, one asynchronous pipeline"
          lead="Single and bulk uploads share the same path. Everything after the upload is asynchronous, buffered and observable."
        >
          <DiagramFigure
            svg={uploadSvg}
            fullSizeUrl={uploadUrl}
            caption="Upload and ingestion sequence, with the document state ladder and safety nets"
            legend={INGESTION_LEGEND}
          />
          <CardGrid cards={UPLOAD_CALLOUTS} columns={3} />
        </EngineeringSection>

        {/* ---------------------------------------------------------------- Retrieval */}
        <EngineeringSection
          id="retrieval"
          eyebrow="Retrieval — Semantic Search + Grounded Ask"
          title="Two paths, one rule: only what retrieval proves is relevant"
          lead="Search never calls a language model. Ask only answers from documents that pass the same relevance gate — and says so when it can’t."
        >
          <DiagramFigure
            svg={searchAskSvg}
            fullSizeUrl={searchAskUrl}
            caption="Search and Ask retrieval flow, including session context and the recovery retry"
          />

          <div className="grid items-start gap-8 lg:grid-cols-2">
            <div className="flex flex-col gap-4">
              <SubHeading>Semantic Search: a relevance gate, not a guess</SubHeading>
              <ul className="flex flex-col gap-2.5 text-sm leading-relaxed text-text-secondary">
                {SEARCH_POINTS.map((point) => (
                  <li key={point} className="flex gap-3">
                    <span aria-hidden="true" className="mt-2 size-1.5 shrink-0 rounded-full bg-accent" />
                    {point}
                  </li>
                ))}
              </ul>
            </div>
            <ScoreRangeChart threshold={0.62} />
          </div>

          <div className="flex flex-col gap-4">
            <SubHeading>Grounded Ask: four ways an answer is produced</SubHeading>
            <CardGrid cards={ASK_OUTCOMES} columns={4} />
          </div>

          <div className="rounded-[var(--radius-lg)] border border-accent/30 bg-accent-subtle p-6">
            <h3 className="text-base font-semibold">Follow-ups stay grounded</h3>
            <p className="mt-2 max-w-4xl text-sm leading-relaxed text-text-secondary">
              When a file lookup resolves files, the backend stores their ids in the AskSession — server-owned and never client-supplied. A
              later “explain this” or “summarize it” retrieves from those files instead of being gated globally, and once generation returns a
              Bedrock session id it is saved into the same session with the context kept. When Bedrock returns no reference objects, the
              source file is cited without inventing a snippet or timestamp.
            </p>
          </div>
        </EngineeringSection>

        {/* ---------------------------------------------------------------- Security */}
        <EngineeringSection
          id="security"
          eyebrow="Security & Tenant Isolation"
          title="Tenant-isolated by design"
          lead="Isolation is enforced on the server at every layer, so a bug in one place can’t widen what a user can reach."
        >
          <CardGrid cards={SECURITY_CARDS} columns={3} />
        </EngineeringSection>

        {/* ---------------------------------------------------------------- Data model */}
        <EngineeringSection
          id="data-model"
          eyebrow="DynamoDB Data Model"
          title="One table, three entities"
          lead="On-demand capacity, a single table, and keys designed so that ownership is a property of the lookup itself."
        >
          <div className="overflow-x-auto rounded-[var(--radius-lg)] border border-border">
            <table className="w-full min-w-[720px] border-collapse bg-surface">
              <caption className="sr-only">DynamoDB entities and their keys</caption>
              <thead className="border-b border-border">
                <tr>
                  <th scope="col" className={th}>
                    Entity
                  </th>
                  <th scope="col" className={th}>
                    Partition key
                  </th>
                  <th scope="col" className={th}>
                    Sort key
                  </th>
                  <th scope="col" className={th}>
                    Notes
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {DATA_MODEL_ROWS.map((row) => (
                  <tr key={row.entity}>
                    <th scope="row" className="px-4 py-3 text-left align-top text-sm font-medium">
                      {row.entity}
                    </th>
                    <td className={`${td} font-mono text-[13px] text-text-primary`}>{row.pk}</td>
                    <td className={`${td} font-mono text-[13px] text-text-primary`}>{row.sk}</td>
                    <td className={td}>{row.note}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="grid gap-8 lg:grid-cols-2">
            <div className="flex flex-col gap-3">
              <SubHeading>Access patterns</SubHeading>
              <ul className="flex flex-col gap-2 text-sm leading-relaxed text-text-secondary">
                {ACCESS_PATTERNS.map((pattern) => (
                  <li key={pattern} className="flex gap-3">
                    <span aria-hidden="true" className="mt-2 size-1.5 shrink-0 rounded-full bg-accent" />
                    {pattern}
                  </li>
                ))}
              </ul>
            </div>
            <p className="self-end text-sm leading-relaxed text-text-muted">
              Application state lives in DynamoDB; the Knowledge Base owns semantic retrieval. A TTL attribute cleans up job records and
              abandoned sessions with no scheduled sweeper.
            </p>
          </div>
        </EngineeringSection>

        {/* ---------------------------------------------------------------- Reliability */}
        <EngineeringSection
          id="reliability"
          eyebrow="Reliability & Observability"
          title="Designed to fail visibly and recover on its own"
          lead="Every asynchronous step has a retry story, a timeout, a cleanup path and an alarm."
        >
          <CardGrid cards={RELIABILITY_CARDS} columns={4} />
        </EngineeringSection>

        {/* ---------------------------------------------------------------- Decisions + cost */}
        <EngineeringSection
          id="decisions"
          eyebrow="Engineering Decisions + Cost"
          title="Choices we’d defend, and a bill that follows usage"
          lead="Managed services where they remove undifferentiated work; nothing that costs money while nobody is using it."
        >
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {DECISIONS.map((decision) => (
              <li key={decision.title} className="rounded-[var(--radius-lg)] border border-border bg-surface p-5">
                <h3 className="text-base font-semibold">{decision.title}</h3>
                <p className="mt-1.5 text-sm leading-relaxed text-text-secondary">{decision.why}</p>
              </li>
            ))}
          </ul>

          <div className="flex flex-col gap-4">
            <SubHeading>Cost-conscious by construction</SubHeading>
            <div className="overflow-x-auto rounded-[var(--radius-lg)] border border-border">
              <table className="w-full min-w-[560px] border-collapse bg-surface">
                <caption className="sr-only">What each service costs when idle and how it scales</caption>
                <thead className="border-b border-border">
                  <tr>
                    <th scope="col" className={th}>
                      Service
                    </th>
                    <th scope="col" className={th}>
                      When idle
                    </th>
                    <th scope="col" className={th}>
                      Scales with
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {COST_ROWS.map((row) => (
                    <tr key={row.service}>
                      <th scope="row" className="px-4 py-3 text-left text-sm font-medium">
                        {row.service}
                      </th>
                      <td className={td}>{row.idle}</td>
                      <td className={td}>{row.scales}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <ul className="flex flex-col gap-2 text-sm leading-relaxed text-text-secondary">
              {COST_NOTES.map((note) => (
                <li key={note} className="flex gap-3">
                  <span aria-hidden="true" className="mt-2 size-1.5 shrink-0 rounded-full bg-accent" />
                  {note}
                </li>
              ))}
            </ul>
          </div>
        </EngineeringSection>

        {/* ---------------------------------------------------------------- Challenges */}
        <EngineeringSection
          id="challenges"
          eyebrow="Engineering Challenges Solved"
          title="What broke, and what we did about it"
          lead="Most of these were found by running against the real services, not by unit tests — which is why the verification harness exists."
        >
          <ol className="grid gap-4 lg:grid-cols-2">
            {CHALLENGES.map((challenge, index) => (
              <li key={challenge.title} className="rounded-[var(--radius-lg)] border border-border bg-surface p-5">
                <div className="flex items-center gap-3">
                  <span
                    aria-hidden="true"
                    className="flex size-6 shrink-0 items-center justify-center rounded-full bg-accent-subtle text-xs font-semibold text-accent-text"
                  >
                    {index + 1}
                  </span>
                  <h3 className="text-base font-semibold">{challenge.title}</h3>
                </div>
                <dl className="mt-3 flex flex-col gap-2.5 text-sm leading-relaxed">
                  <div>
                    <dt className="text-xs font-medium uppercase tracking-wider text-text-muted">Problem</dt>
                    <dd className="mt-0.5 text-text-secondary">{challenge.problem}</dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium uppercase tracking-wider text-accent-text">Fix</dt>
                    <dd className="mt-0.5 text-text-secondary">{challenge.fix}</dd>
                  </div>
                </dl>
              </li>
            ))}
          </ol>
        </EngineeringSection>

        {/* ---------------------------------------------------------------- Trade-offs */}
        <EngineeringSection
          id="tradeoffs"
          eyebrow="Trade-offs / Future Evolution"
          title="Honest limits, and where it goes next"
        >
          <div className="grid gap-6 lg:grid-cols-2">
            <div className="rounded-[var(--radius-lg)] border border-border bg-surface p-6">
              <SubHeading>Trade-offs today</SubHeading>
              <ul className="mt-4 flex flex-col gap-3 text-sm leading-relaxed text-text-secondary">
                {TRADEOFFS.map((item) => (
                  <li key={item} className="flex gap-3">
                    <span aria-hidden="true" className="mt-2 size-1.5 shrink-0 rounded-full bg-text-muted" />
                    {item}
                  </li>
                ))}
              </ul>
            </div>
            <div className="rounded-[var(--radius-lg)] border border-border bg-surface p-6">
              <SubHeading>Next</SubHeading>
              <ul className="mt-4 flex flex-col gap-3 text-sm leading-relaxed text-text-secondary">
                {FUTURE.map((item) => (
                  <li key={item} className="flex gap-3">
                    <span aria-hidden="true" className="mt-2 size-1.5 shrink-0 rounded-full bg-accent" />
                    {item}
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </EngineeringSection>

        {/* ---------------------------------------------------------------- API + CTA */}
        <EngineeringSection
          id="api"
          eyebrow="API Surface"
          title="Small on purpose"
          lead="Seven routes cover the whole product. Single and bulk upload share one endpoint; Search and Ask stay separate."
        >
          <div className="overflow-x-auto rounded-[var(--radius-lg)] border border-border">
            <table className="w-full min-w-[640px] border-collapse bg-surface">
              <caption className="sr-only">Public API routes</caption>
              <thead className="border-b border-border">
                <tr>
                  <th scope="col" className={th}>
                    Method
                  </th>
                  <th scope="col" className={th}>
                    Path
                  </th>
                  <th scope="col" className={th}>
                    Purpose
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {API_ROWS.map((row) => (
                  <tr key={`${row.method} ${row.path}`}>
                    <td className="px-4 py-3 align-top font-mono text-[13px] font-medium text-accent-text">{row.method}</td>
                    <td className="px-4 py-3 align-top font-mono text-[13px] text-text-primary">{row.path}</td>
                    <td className={td}>{row.purpose}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <ul className="flex flex-col gap-2 text-sm leading-relaxed text-text-muted">
            {API_NOTES.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>

          <div className="mt-6 rounded-[var(--radius-lg)] border border-border bg-surface p-8 text-center sm:p-12">
            <h2 className="text-2xl font-semibold tracking-tight sm:text-3xl">See it work, or read the code</h2>
            <p className="mx-auto mt-3 max-w-xl text-text-secondary">
              Upload a few files, search for them by describing what you remember, and ask a question — then compare it with the
              architecture above.
            </p>
            <div className="mt-7 flex flex-col justify-center gap-3 sm:flex-row">
              <Link to="/login" className={primaryCta}>
                Try Recollect
                <ArrowRight className="size-4" aria-hidden="true" />
              </Link>
              <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className={secondaryCta}>
                <GithubIcon className="size-4" />
                View GitHub
              </a>
            </div>
          </div>
        </EngineeringSection>
      </main>

      <PublicFooter />
    </div>
  )
}
