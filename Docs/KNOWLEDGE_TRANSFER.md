# KNOWLEDGE_TRANSFER.md

## Purpose

This is the fast "boot sequence" for a fresh Claude Code session (or any new
engineer) opening this repository. It answers: **if you know nothing about
this project yet, what do you need to know to continue safely?**

It is deliberately operational, not architectural. `Docs/ARCHITECTURE.md`,
`Docs/API.md`, `Docs/DATA_MODEL.md` remain the detailed source of truth —
this file points at them rather than repeating them. Keep this file short;
if a section is growing into an essay, that content belongs in one of the
other docs instead.

---

## Current Project State

- **What this is:** **Recollect** (repo/AWS resources still say "Memory Layer") — a multimodal personal memory layer.
  Users upload files (PDFs, images, audio, video, documents), the system
  indexes them via a Bedrock Knowledge Base, and users later search/ask
  over their own corpus in natural language. See `Docs/PRODUCT.md`.
- **Current deployed environment:** live in AWS account `889168907297`,
  region `ap-south-1`. All 6 app stacks are deployed and healthy.
- **Phase status** (see `Docs/TASKS.md` for full detail):
  - Phases 1–7: **implemented, tested, and deployed.**
  - Phase 7 (reliability/operational safety — alarms, stale-document
    cleanup, structured logging, deployment safeguards) was the most
    recently completed and deployed phase.
  - Phase 8 (UX polish, frontend only) is **pushed to `main` (Amplify deploy) but NOT closed** —
    awaiting a real-login review with actual data + one final polish pass; see "Phase 8 (UX polish) — status" below.
- **Current commit:** `22fd9db` on `main` ("Add Phase 7: reliability and
  operational safety"). Run `git log -1` to confirm this is still current
  before trusting it.
- **Frontend:** deployed via Amplify, auto-builds from `git push` to `main`.
  Last build (job 12) `SUCCEED`ed.

---

## Repository Map

```text
Amazon/
├── Backend/    Java 21 Spring Boot Lambda (API) + plain-handler Lambdas (ingestion)
├── Frontend/   React + TypeScript, Vite, vitest
├── Infra/      AWS CDK (Java) — all persistent infrastructure
├── Docs/       Product/architecture/data-model/API/ops/tasks specs
├── AGENTS.md   Repo-wide rules for any coding agent
└── CLAUDE.md   Claude Code operating instructions
```

Don't introduce a new top-level directory without a clear reason (`AGENTS.md`).

---

## AWS Environment

Region: `ap-south-1`. Account: `889168907297`.

Deployed CDK stacks (all currently healthy, `cdk diff` clean as of Phase 7):

```text
MemoryLayerAuthStack        Cognito user pool, Google IdP, app client
MemoryLayerDataStack        DynamoDB table, S3 uploads bucket, SQS + DLQ
MemoryLayerIngestionStack   Coordinator/Reconciler/StaleCleanup Lambdas, KB, data sources
MemoryLayerApiStack         API Gateway HTTP API + Java Lambda (SnapStart)
MemoryLayerFrontendStack    Amplify app (config only — content deploys via git push)
MemoryLayerAlarmsStack      SNS topic + 8 CloudWatch alarms (Phase 7)
```

Key deployed resource identifiers (none of these are secrets):

| Resource | Value |
|---|---|
| Cognito user pool ID | `ap-south-1_EcoZ4MroP` |
| Cognito app client ID | `2pa5i5dfkq23gok012n92t5okl` (`memory-layer-spa`) |
| Cognito hosted domain | `memory-layer-auth-907297` |
| DynamoDB table | `MemoryLayer` (single-table design, TTL attribute `expiresAt`, enabled) |
| Uploads bucket | `memorylayerdatastack-uploadsbucket5e5e9b64-eek8gy7afvw7` |
| Ingestion queue / DLQ | `MemoryLayerDataStack-IngestionQueue9CC91140-*` / `...IngestionDLQF0F102AE-*` |
| Bedrock Knowledge Base ID | `MESMX1P9DN` |
| KB data sources | multimodal `FIO4MGIGSJ`, text `ZOWKOQGHGB` |
| KB embedding model | `amazon.titan-embed-text-v2:0` |
| `/ask` generation model | Amazon Nova Lite via APAC cross-region inference profile (`apac.amazon.nova-lite-v1:0`) |
| API Gateway endpoint | `https://0sby0h3d1a.execute-api.ap-south-1.amazonaws.com` |
| Amplify app ID / domain | `d28nd6lc9fjyiv` / `d28nd6lc9fjyiv.amplifyapp.com` |
| SNS alarm topic | `memory-layer-alarms` (subscriber: see `ALARM_EMAIL` below) |

To re-verify any of these live rather than trust this table, use the AWS CLI
(`aws cloudformation describe-stacks`, `aws cognito-idp describe-user-pool`,
`aws bedrock-agent list-data-sources`, `aws amplify get-app`, etc.) — this
table can drift if someone deploys outside this file's discipline.

---

## Required Environment Variables

| Variable | Used by | Where it comes from |
|---|---|---|
| `GOOGLE_OAUTH_CLIENT_ID` | `InfraApp` (CDK synth) | The real Google OAuth client ID already registered against Cognito's Google IdP. Retrievable without a login via `aws cognito-idp describe-identity-provider --user-pool-id ap-south-1_EcoZ4MroP --provider-name Google --region ap-south-1 --query "IdentityProvider.ProviderDetails.client_id"` — it is not secret (only the OAuth client *secret* is). |
| `ALARM_EMAIL` | `InfraApp` (CDK synth) | Address that should receive CloudWatch alarm notifications. Currently `vansharcade324@gmail.com`. Changing it and redeploying `MemoryLayerAlarmsStack` removes the old SNS subscription and sends a new confirmation email to the new address. |
| `SMOKE_ACCESS_TOKEN` | `Infra/smoke-test.ps1` (optional) | A manually obtained Cognito access token (sign in through the real app, copy from browser devtools). Not required for the public `/health` check; required to exercise `/documents`, `/search`, `/ask` against the real JWT authorizer. |

**Never** commit real values for these, and never let `InfraApp` fall back to
a placeholder if one is missing — it fails synth outright by design (see
"Known Incidents" below).

---

## Deployment Procedure

**`Infra/deploy.ps1` is the only sanctioned way to deploy.** Full detail in
`Docs/OPERATIONS.md` §4; the essentials:

```powershell
$env:GOOGLE_OAUTH_CLIENT_ID = "<real client ID>"
$env:ALARM_EMAIL = "<alarm recipient>"
cd Infra
./deploy.ps1 -Stacks MemoryLayerApiStack
# or multiple: ./deploy.ps1 -Stacks MemoryLayerIngestionStack,MemoryLayerApiStack
```

It always: rebuilds `Backend/target/backend.jar` (`mvn clean package`), runs
Backend + Infra tests, shows `cdk diff` for exactly the named stacks, requires
typed confirmation (`deploy`), then deploys with `--exclusively`.

**IMPORTANT:**
- Always let it rebuild the jar — never deploy without a fresh `mvn clean
  package`. A stale jar shipping to production is a real incident this
  project already had (see below).
- Never deploy with `GOOGLE_OAUTH_CLIENT_ID`/`ALARM_EMAIL` unset — `InfraApp`
  fails synth immediately if either is missing, but don't try to work around it.
- Always use `--exclusively` (the script does this for you) — without it,
  `cdk deploy` can silently pull in an unrelated dependency stack.
- Always inspect the `cdk diff` output before typing `deploy`. `AuthStack`
  and `DataStack` should normally show **zero diff** unless you deliberately
  changed something in them — an unexpected diff there is a signal to stop
  and understand why before proceeding, not to shrug and deploy.
- If `AlarmsStack` (or anything else) references a getter on another stack's
  construct (e.g. `dataStack.getIngestionQueue()`), the **source** stack
  must also be in the `-Stacks` list the first time that reference is added,
  even if the source stack's own resources aren't otherwise changing — CDK
  needs to add a CloudFormation Export to it. Forgetting this produces "No
  export named ... found" and a clean rollback (see Known Incidents).
- Invoking `deploy.ps1` via `powershell.exe -File` from a non-PowerShell
  caller: pass `-Stacks` as a single comma-joined string
  (`-Stacks StackA,StackB`) — the script normalizes it internally. Don't
  pass space-separated stack names as separate arguments; that does not
  bind correctly under `-File` invocation.
- The frontend is **not** deployed via `cdk deploy` — push to `main` and
  Amplify auto-builds (`MemoryLayerFrontendStack` only manages the Amplify
  app's own config, not its content).

---

## Known AWS Gotchas

- **Lambda account concurrency is capped at 10** (unreserved, unmodifiable
  reserved-concurrency in this account/region as of Phase 7 — checked via
  `aws lambda get-account-settings`). This is a hard ceiling on how many
  Lambda invocations can run simultaneously across *all* functions in this
  account. Don't reserve per-function concurrency casually; it eats into
  this same shared budget.
- **A Bedrock Knowledge Base allows only one ingestion job at a time**,
  KB-wide, across every data source (not per data source). The Ingestion
  Coordinator handles this via `ConflictException` → leave documents at
  `UPLOADED`, let the SQS message retry later (see `Docs/ARCHITECTURE.md` §7.3.2).
- **`bedrock:Retrieve` is the correct IAM action for `/search`** — not a
  `bedrock-agent-runtime:*` namespace action. Found live in Phase 5; unit
  tests didn't catch it.
- **`bedrock:RetrieveAndGenerate` alone does not cover invoking a
  cross-region inference profile.** Also grant `bedrock:GetInferenceProfile`
  and `bedrock:InvokeModel*` as a separate `Resource: "*"` statement (kept
  apart from the KB-ARN-scoped `Retrieve` grant). Found live in Phase 6 via
  a real `AccessDeniedException`.
- **Anthropic models on this account require a manually-gated model-access
  form** — Amazon Nova Lite does not, and produced accurate grounded
  answers in testing, which is why `/ask` uses Nova Lite via the APAC
  cross-region inference profile instead of Claude.
- **BDA/multimodal parsing requires `supplementalDataStorageConfiguration`
  on the Knowledge Base, set on first creation** — it's immutable after
  creation. If a future KB ever needs `parsingModality: MULTIMODAL` +
  `parsingStrategy: BEDROCK_DATA_AUTOMATION`, this must be present from the
  very first deploy, not added later.
- **Bedrock's `NumberDocument.unwrap()` returns a decimal string** (e.g.
  `"6360.0"`), not an integer string — feeding it straight into
  `Long.parseLong` throws and silently drops every real audio/video media
  timestamp. Found live in Phase 5.
- **A `COMPLETE` ingestion job status does not mean every document in it
  indexed successfully** — check `statistics().numberOfDocumentsFailed()`.
  Bedrock does not identify *which* document(s) failed, so this project
  conservatively marks the whole job's documents `FAILED` if that count is
  nonzero, rather than guessing (Phase 4).
- **SnapStart publish is slower than a normal Lambda code update** (~90s
  observed vs. ~10-30s for other functions) — don't assume a hung deploy
  just because `MemoryLayerApiStack` is taking longer than the others.
- **Maven jar builds are not byte-reproducible** (`mvn clean package` on
  identical source can still produce a different jar hash — timestamps
  embedded in the zip). This means `deploy.ps1`'s always-rebuild-first
  discipline can show a "changed" Lambda code diff even with zero real
  source changes. Harmless, just an extra redeploy — not a bug to chase.
- **Windows PowerShell 5.1 needs a UTF-8 BOM (or pure ASCII) to parse `.ps1`
  files correctly** — a bare em-dash (`—`) or other non-ASCII character in
  a string literal (not a comment) can corrupt parsing with confusing
  "missing closing brace" errors far from the real problem. Keep
  `Infra/*.ps1` pure ASCII.
- **`powershell.exe -File` does not bind `[string[]]` params the way the
  interactive shell does** — see the deploy-procedure note above.
- **Git Bash mangles any argument starting with `/`** (MSYS path
  conversion) — e.g. `aws logs ... --log-group-name "/aws/lambda/foo"` fails
  with a cryptic regex-validation error unless run with `MSYS_NO_PATHCONV=1`
  prefixed, or the path is otherwise escaped.

---

## Data Flow

```text
Upload:  browser -> presigned PUT -> S3 -> S3 event -> SQS -> IngestionCoordinatorHandler
         -> stage into kb/multimodal/ or kb/text/ (+ .metadata.json sidecar)
         -> StartIngestionJob -> document status UPLOADED -> INDEXING
         -> StatusReconcilerHandler polls GetIngestionJob (~1/min)
         -> COMPLETE (0 failures) -> document status READY
         -> FAILED / COMPLETE with failures -> document status FAILED

Search:  GET/POST /api/v1/search -> Bedrock Retrieve (server-injected userId filter) -> ranked chunks

Ask:     POST /api/v1/ask -> Bedrock RetrieveAndGenerate (same server-side filter,
         app-issued session ID, never a raw Bedrock session ID) -> grounded answer + citations

Stale cleanup (Phase 7, separate from the Reconciler, every 15 min):
         UPLOAD_PENDING > 30 min + no S3 object -> FAILED
         UPLOADED > 2 hours, not covered by an active job -> FAILED
         INDEXING > 1 hour -> warn only, never auto-failed
```

Full detail: `Docs/ARCHITECTURE.md` §7, `Docs/OPERATIONS.md` §8.

---

## Data Model

Single DynamoDB table (`MemoryLayer`). Key entities:

- **Document** — `PK=USER#<sub>`, `SK=DOC#<documentId>`. Statuses:
  `UPLOAD_PENDING -> UPLOADED -> INDEXING -> READY | FAILED`. Never add a
  new status casually (`AGENTS.md`).
- **IngestionJob** — `PK=SYSTEM#INGESTION`, `SK=JOB#<jobId>`. Tracks a
  Bedrock ingestion job's covered documents (`userId#documentId` refs) and
  status. TTL-cleaned 7 days after completion.
- **AskSession** — `PK=USER#<sub>`, `SK=ASK_SESSION#<appSessionId>`. Maps an
  application-issued session ID to a real Bedrock session ID, scoped to the
  owning user's own partition. Never exposes the raw Bedrock session ID.

Full schema and access patterns: `Docs/DATA_MODEL.md`.

---

## Security Invariants

These are load-bearing — see `AGENTS.md` "Security Rules" for the complete list:

- `userId` is always the validated JWT `sub`. Never trust a client-supplied
  `userId`/`ownerId`/`tenantId` for authorization.
- Every Bedrock retrieval call injects the authenticated user's metadata
  filter **server-side**. The client never controls the tenant filter.
- Citations/downloads resolve only through `GET /documents/{id}/access-url`
  after verifying the document belongs to the authenticated user's
  partition — never a raw staging S3 URI or KB internal path.
- An application-issued Ask session ID is never the raw Bedrock session ID.
- Never log JWTs, presigned URLs, `/ask`/`/search` question/query/answer
  content, raw file content, or raw Bedrock session IDs (`Docs/OPERATIONS.md` §5).

---

## Known Incidents / Lessons

(Full narrative in commit messages for Phases 5–7 and `Docs/TASKS.md`.)

- **Stale deployed jar (Phase 5):** `cdk deploy` was run without rebuilding
  `Backend/target/backend.jar` first, deploying code that predated the
  feature being shipped. → `Infra/deploy.ps1` now always rebuilds first.
- **Placeholder Google OAuth client ID (Phase 5):** `GOOGLE_OAUTH_CLIENT_ID`
  was unset, so `InfraApp` silently fell back to a placeholder that then
  got deployed to the real Cognito Google IdP, briefly breaking Google
  Sign-In — compounded by `cdk deploy` pulling `MemoryLayerAuthStack` in as
  an undeclared dependency because `--exclusively` wasn't used. → Phase 7
  removed the silent fallback entirely (hard fail-fast) and `deploy.ps1`
  always uses `--exclusively`.
- **Ingestion job record ordering race (Phase 4):** the `IngestionJob`
  record used to be saved only *after* marking every covered document
  `INDEXING`. Since `StartIngestionJob` is a real, non-retractable side
  effect, a failure between the two steps could orphan a running Bedrock
  job with no tracking record. Fixed by saving the job record first.
- **`StaleDocumentCleanupHandler` review fix (Phase 7):** the first version
  auto-failed any `UPLOADED` document past 2 hours regardless of whether it
  was still covered by an active, in-flight ingestion job (possible if that
  document's own status write to `INDEXING` failed after `StartIngestionJob`
  already succeeded). Fixed by checking `StaleDocumentPolicy.activeJobDocumentRefs`
  first. A rarer residual case — a document orphaned by a job that already
  reached `COMPLETE`/`FAILED` before its own status write ever landed — is
  a known, documented, unfixed limitation (`Docs/OPERATIONS.md` §8).
- **`MemoryLayerAlarmsStack` rollback (Phase 7 deploy):** its first deploy
  attempt failed with "No export named ... found" because it references
  `dataStack.getIngestionQueue()`/`getIngestionDeadLetterQueue()`, but
  `MemoryLayerDataStack` wasn't included in that deploy's `-Stacks` list, so
  CDK couldn't add the new export. Rolled back cleanly — nothing was
  created, no SNS email was sent. Fixed by including `MemoryLayerDataStack`
  in the deploy.
- **`deploy.ps1` / `smoke-test.ps1` encoding and argument-binding bugs
  (Phase 7 deploy):** em-dash characters in string literals broke PS 5.1
  parsing; `powershell.exe -File` doesn't bind `[string[]]` params the way
  the interactive shell does. See "Known AWS Gotchas" above for the fixes.

---

## Current Known Issues / Deferred Work

- Expired presigned URL handling — not implemented (Phase 7 explicitly left
  this open; flagged in `Docs/TASKS.md`, not silently skipped).
- Unsupported file type UX — not implemented, same reasoning.
- The rare "orphaned by a completed job" stale-`UPLOADED` edge case
  described above under Known Incidents remains unfixed by design (out of
  scope for Phase 7's specific review ask).
- No CloudWatch Synthetics or X-Ray — explicitly out of scope per the
  approved Phase 7 plan.

---

## Phase 8 (UX polish) — status

Frontend-only; no backend/infra/API changes. Implemented and locally validated (`tsc`, 75 vitest
tests, `oxlint`, `vite build`) and pushed to `main` (Amplify auto-builds; there is no `cdk deploy`
for this). Still open: real-login review of Home/Library/Search/Ask, then a final polish pass.

What changed, in one paragraph: brand is **Recollect**; new landing + split-screen login; AppShell with
a global upload dialog (single upload queue via `UploadProvider`), Cmd/Ctrl+K, avatar menu with
Dark/Light/System theme (`recollect-theme` in localStorage); Ask/Home/Library/Search rebuilt on shared
primitives (`FileThumb`, `StatusBadge`, `EmptyState`, `ErrorState`, `friendlyError`); library status
updates via `useDocuments` polling. Full checklist: `Docs/TASKS.md` Phase 8.

Things to know when touching the frontend:
- **Polling contract:** `useDocuments` polls `GET /documents` every ~5s only while a document is
  `UPLOAD_PENDING/UPLOADED/INDEXING`, backs off to 15s after 2 min, pauses on hidden tabs. Don't add
  other pollers.
- **Tests that mock `@/api/client`** must keep the real `ApiError` (`importOriginal`), because
  `friendlyError` does `instanceof ApiError`. A bare `vi.mock` factory makes error paths throw.
- jsdom has no `IntersectionObserver`; `useIntersectionOnce` no-ops without it.
- Small accent-coloured text uses `text-accent-text` (AA-safe), not `text-accent`.
- Routes under `/app` are lazy-loaded in `app/router.tsx`.
- **Snippets are display-sanitized** (`lib/snippet.ts`) — raw KB chunks contain Markdown/BDA `<figure>` markup.
  Never render `snippet` directly; use `cleanSnippet`.
- **Ask decline gotcha (verified live):** `RetrieveAndGenerate` gives a decline the *same shape* as a real
  answer (no guardrail action; one citation spanning the text) and attaches all retrieved chunks. The UI
  relabels those as "Context checked" via a text heuristic (`lib/askAnswer.ts`). Deterministic fix = a
  prompt-template marker + backend flag (Phase 9).
- **Download** = fresh `/access-url` -> `fetch` -> Blob (`lib/download.ts`, `useFileActions`); needs the
  uploads bucket's CORS `GET` for the app origin (already configured). Delete is deferred to Phase 9.
- **Deployed:** the relevance-gate/Ask-pipeline backend is live (Lambda `live` alias v15) and was verified against
  the deployed API; verification method = invoke `memory-layer-api:live` with a synthetic API Gateway v2
  JWT-authorizer event (no browser needed). Phase 8 remains open pending browser review.
- **Relevance gate (backend):** all retrieval goes through `KnowledgeBaseRetriever`, which drops chunks below
  `MIN_RELEVANCE_SCORE` (default **0.62**, measured; optional env override, no infra change needed) *before*
  dedup. Search returns `[]` for absent topics. Ask runs a preflight `Retrieve` (same filter + gate), and
  skips `RetrieveAndGenerate` entirely when nothing is relevant. Scores are never logged. See
  `Docs/API.md` §18/§20.
- **Ask prompt gotchas (verified live):** `$output_format_instructions$` must stay in the custom template or
  citations come back with **zero** references; `$search_results$` is required; `$query$` isn't needed for
  Nova. The LLM never sees filenames, so "do I have X?" is answered from metadata (`FindIntent`).
- **Scanned PDFs** are indexed as page *images* (chunk `content.type=IMAGE`, empty `text`); their searchable text
  lives in the `x-amz-bedrock-kb-description` metadata.
- **Live verification harness:** `LiveRelevanceVerificationTest` (skipped unless `LIVE_KB=1`; needs
  `KNOWLEDGE_BASE_ID`, `ASK_MODEL_ARN`, `LIVE_USER_A/B`, `LIVE_DOCS_JSON`). It calls the real KB read-only.
- **API Lambda timeout is 28s** (ApiStack), below the 30s HTTP API integration ceiling (explicit `TimeoutInMillis`).
  Ask can do a preflight `Retrieve` + one or two generations; latency is ~2-5s normally, up to ~12s for a retrying
  turn. Don't lower it below ~20s without re-measuring. The first context-only turn asks the anchored wording once
  (no failed attempt first).
- **File discovery (Ask)** (live since Lambda `live` alias v18): `FindIntent` (explicit find/show/locate/do-I-have, determiner optional) +
  `DocumentNameMatcher` (filename tokens: camelCase/`_-.`/digits, plural stemming, one-edit typo on 6+ char tokens,
  all query tokens must match; bare topics exact-only and not questions). Candidates = filename matches, then
  gate-passing semantic docs (max 3), resolved *before* the relevance check; nothing → deterministic no-answer.
  Reads the caller's own READY docs via `DocumentRepository.listReadyByUser` (GSI1, own partition). Root cause it
  fixed: "find numerical method assignment" (no determiner) skipped the find path and got a model refusal even
  though the file scored 0.75 on Retrieve. 0.62 is unchanged.
- **Ask conversational context:** `AskSession.contextDocumentIds` (server-owned; set by the find path; bedrockSessionId
  nullable) scopes deictic follow-ups to the found file. Details: `Docs/DATA_MODEL.md` §15.1, `Docs/API.md` §20.
- **`RetrieveAndGenerate` gotchas (verified live):** its own retrieval can return **zero references** for short
  vague messages even when a scoped `Retrieve` with the same filter finds the file (wording-dependent, some
  deterministic); failures cluster deep inside long Bedrock sessions; Bedrock can return the fixed string "Sorry, I
  am unable to assist you with this request." instead of an answer. The backend handles these with one structural
  anchored retry in a fresh session (`AskService.needsAnchoredRetry`). Don't 'fix' this with filename-prefixing
  or punctuation normalisation — both were measured to not help. A fully deterministic alternative would be our own
  scoped `Retrieve` + a direct model call (an architecture change: `/ask` is specified as `RetrieveAndGenerate`).
- **Deferred:** the `/app/document/:id` detail page (still a stub). Demo data: `Docs/demo-samples/`.

---

## Engineering showcase (`/engineering`)

Public, unauthenticated route (outside `ProtectedRoute`) explaining the deployed architecture; linked from the
landing header/footer ("Engineering"). Frontend-only — no backend/infra/API change. Copy lives in
`Frontend/src/pages/EngineeringPage/content.ts`; components in `Frontend/src/components/engineering/`.

- The three diagrams are **generated**: `python Docs/diagrams/generate.py` (uses `diagramkit.py`) writes
  `Docs/diagrams/*.svg`. The page inlines them via Vite `?raw` (theme-adaptive CSS variables); `vite.config.ts` sets
  `server.fs.allow: ['..']` so dev can read `../Docs`. Change the diagrams by editing `generate.py`, not the SVGs.
- Only the high-level diagram uses AWS icons: official AWS Architecture Icons (July 2026 pack), unmodified, stored in
  `Docs/diagrams/icons/` and inlined by `Diagram.icon()`. The Upload and Search/Ask diagrams stay logical (no icons).
- Layout: hero (3 chips, 3 facts, no region/IDs) -> sticky contained `SectionNav` (scroll-spy, horizontal scroll on mobile)
  directly below it. `ap-south-1 (Mumbai)` lives in the Architecture lead. Landing has an `EngineeringShowcase`
  teaser (secondary to the main CTA) between the product visual and "How it works".
- Must never show account IDs, bucket names, ARNs, Cognito IDs, emails, real user/document IDs — enforced by a scan in
  `EngineeringPage.test.tsx`. The 0.62 threshold is described as corpus-calibrated and tunable.
- When Ask/Search/ingestion behavior changes, update `generate.py` + `content.ts` + `ARCHITECTURE.md` together.

---

## Verification Checklist

After backend changes: `cd Backend && mvn test`
After infra changes: `cd Infra && mvn test && cdk synth` (with
`GOOGLE_OAUTH_CLIENT_ID`/`ALARM_EMAIL` set)
After frontend changes: `cd Frontend && npx tsc --noEmit && npx vitest run && npm run build`
After any deploy: `./Infra/smoke-test.ps1` (add `SMOKE_ACCESS_TOKEN` for
authenticated-route coverage), then check the relevant CloudWatch log group.

Do not report a task complete without having actually run the relevant one(s)
of these (`AGENTS.md` "Testing Rules").

---

## Commands

```powershell
# Backend
cd Backend; mvn clean package        # rebuild the jar (also runs tests)
cd Backend; mvn test                 # tests only

# Infra
cd Infra; mvn test                   # Infra-level CDK unit tests
cd Infra; npx cdk synth              # requires GOOGLE_OAUTH_CLIENT_ID + ALARM_EMAIL
cd Infra; npx cdk diff <Stacks> --exclusively
cd Infra; ./deploy.ps1 -Stacks <Stacks>   # the only sanctioned deploy path

# Frontend
cd Frontend; npm run build
cd Frontend; npx vitest run
cd Frontend; npx tsc --noEmit

# Smoke test
cd Infra; ./smoke-test.ps1
$env:SMOKE_ACCESS_TOKEN = "<token>"; ./Infra/smoke-test.ps1   # full coverage
```

---

## Handoff Protocol

**Before doing any work in this repository:**

1. Read `AGENTS.md`.
2. Read `CLAUDE.md`.
3. Read this file (`Docs/KNOWLEDGE_TRANSFER.md`).
4. Read `Docs/TASKS.md` for current phase status.
5. Run `git status` and `git log -1` — don't assume this file's "Current
   Project State" section is still accurate; verify.
6. Inspect the relevant existing code directly before changing it — don't
   assume any doc (including this one) is fully up to date.

**After any work that changes** deployed architecture, required environment
variables, deployment commands, discovered AWS behavior, known
incidents/gotchas, current phase status, or important operational
procedures — **update this file before ending the task.** Keep it short:
if a change needs paragraphs to explain, put the detail in
`Docs/ARCHITECTURE.md`/`Docs/API.md`/`Docs/DATA_MODEL.md`/`Docs/OPERATIONS.md`
and link to it from here in one line.
