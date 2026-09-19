import {
  Activity,
  BellRing,
  Boxes,
  Database,
  FileSearch,
  Fingerprint,
  Gauge,
  HardDrive,
  KeyRound,
  Layers,
  Lock,
  MessagesSquare,
  RefreshCw,
  Rocket,
  ScrollText,
  ShieldCheck,
  Timer,
  Trash2,
  Waypoints,
  type LucideIcon,
} from 'lucide-react'

/*
 * Copy for the public /engineering page. Concise on purpose; the detailed contract lives in Docs/.
 * Only service names and structural key prefixes appear here — never account ids, bucket names, ARNs,
 * Cognito ids, emails, real user ids or other production identifiers.
 */

export interface Card {
  icon: LucideIcon
  title: string
  body: string
}

export const HERO = {
  eyebrow: 'Under the hood',
  title: 'Engineering Recollect',
  lead:
    'A serverless personal memory layer on AWS. Upload anything, find it by describing it, and ask questions answered only from your own files — with tenant isolation designed into every layer.',
  chips: ['Serverless compute', 'No always-on servers', 'Tenant-isolated by design', 'Async ingestion', 'Grounded answers'],
  facts: [
    { label: 'Region', value: 'ap-south-1 (Mumbai)' },
    { label: 'Compute', value: 'Java 21 Lambda + SnapStart' },
    { label: 'Retrieval', value: 'Bedrock Knowledge Base + S3 Vectors' },
    { label: 'Infrastructure', value: 'AWS CDK, end to end' },
  ],
}

export const ARCHITECTURE_LEGEND = [
  'Amplify serves the React app; deployments follow Git pushes.',
  'Cognito handles Google and email sign-in and issues JWTs.',
  'The browser calls API Gateway with a bearer token; a JWT authorizer validates it.',
  'A Java 21 Lambda on SnapStart serves the API. Its identity comes only from the validated token.',
  'DynamoDB holds application state: documents, ingestion jobs and Ask sessions.',
  'The API signs presigned URLs; file bytes bypass the API Lambda.',
  'The browser uploads straight to S3 with that presigned URL.',
  'S3 events land in SQS, which buffers upload bursts.',
  'Background Lambdas consume the queue, reconcile status and clean up stale documents.',
  'The coordinator starts Knowledge Base ingestion; the reconciler polls its status.',
  'The Knowledge Base parses, embeds with Titan V2 and indexes into S3 Vectors.',
  'Search and Ask read the same index; only Ask invokes Nova Lite.',
]

export const INGESTION_LEGEND = [
  'The browser asks for upload URLs for a batch of files.',
  'The API records each document as UPLOAD_PENDING.',
  'It returns presigned PUT URLs for backend-generated keys under the caller’s prefix.',
  'The browser uploads bytes straight to S3 with that URL, bypassing the API Lambda.',
  'S3 emits ObjectCreated for the users/ prefix only; staged files never re-enter the pipeline.',
  'SQS buffers and retries the events in batches.',
  'The coordinator marks documents UPLOADED.',
  'It stages each file into kb/multimodal/ or kb/text/ with a metadata sidecar.',
  'It marks INDEXING and saves an ingestion-job record.',
  'It starts a Knowledge Base ingestion job.',
  'A scheduled reconciler polls the job every minute.',
  'Documents become READY, or FAILED with a safe, user-facing reason.',
]

export const UPLOAD_CALLOUTS: Card[] = [
  {
    icon: HardDrive,
    title: 'Direct-to-S3 uploads',
    body: 'The API issues a presigned PUT for a backend-generated key and records UPLOAD_PENDING. Bytes travel browser → S3, so the backend scales with metadata, not file size.',
  },
  {
    icon: Layers,
    title: 'Two parsing paths',
    body: 'Files are staged into kb/multimodal/ (Bedrock Data Automation in multimodal mode: PDFs, images, audio, video) or kb/text/ (the default text parser), each with a sidecar carrying userId and documentId.',
  },
  {
    icon: Waypoints,
    title: 'Backpressure, not failure',
    body: 'A Knowledge Base runs one ingestion job at a time. A ConflictException leaves documents at UPLOADED and SQS retries later; the DLQ and an alarm catch what truly fails.',
  },
]

export const SEARCH_POINTS = [
  'Nearest-neighbour search always returns something — even for a topic you never uploaded.',
  'Recollect applies a minimum similarity per chunk, before de-duplicating to documents.',
  'A query with nothing relevant returns an empty list, never arbitrary files.',
  'Scores decide; they are never shown to users or written to logs.',
]

export const ASK_OUTCOMES: Card[] = [
  {
    icon: FileSearch,
    title: 'Nothing relevant → no-answer',
    body: 'A preflight retrieval uses the same tenant filter and relevance gate. If nothing survives, Ask replies deterministically with zero citations and never calls the model.',
  },
  {
    icon: Fingerprint,
    title: 'File lookup from metadata',
    body: '“Find / show / do I have …” (or a bare topic that strongly matches a filename) is answered from real filenames plus semantic candidates — the model never sees filenames. The resolved files become the conversation’s server-owned context.',
  },
  {
    icon: MessagesSquare,
    title: 'Grounded answer',
    body: 'RetrieveAndGenerate with Nova Lite, scoped to the relevant documents — or, for follow-ups like “explain this”, to the files already in the conversation’s context — with a prompt that answers only from the user’s own files. Citations come only from those documents.',
  },
  {
    icon: RefreshCw,
    title: 'Bounded recovery',
    body: 'If generation refuses or retrieves nothing although relevant documents are known, it retries once in a fresh session. The successful session id replaces the failed one.',
  },
]

export const SECURITY_CARDS: Card[] = [
  {
    icon: KeyRound,
    title: 'One identity',
    body: 'userId is the validated JWT sub — never an email, never a client-supplied field.',
  },
  {
    icon: ShieldCheck,
    title: 'Server-side tenant filter',
    body: 'Every retrieval injects userId == sub in the backend. Clients cannot set, widen or replace it, and there is no field to try.',
  },
  {
    icon: Boxes,
    title: 'Backend-owned S3 keys',
    body: 'Keys are generated server-side under the caller’s prefix. The client never chooses a bucket key.',
  },
  {
    icon: Lock,
    title: 'Ownership before every URL',
    body: 'A presigned GET is issued only after the document is loaded from the caller’s own partition and its key is verified. Citations resolve the same way.',
  },
  {
    icon: Database,
    title: 'Sessions the client can’t forge',
    body: 'Ask sessions resolve under the caller’s partition: a foreign or expired id returns 409. Context document ids are server-written and re-checked against the caller’s documents each turn.',
  },
  {
    icon: ScrollText,
    title: 'Nothing sensitive in logs',
    body: 'Structured logs carry request ids and a hashed user id — never file content, questions, answers, JWTs or presigned URLs. Storage is private and encrypted at rest.',
  },
]

export const DATA_MODEL_ROWS = [
  {
    entity: 'Document',
    pk: 'USER#<sub>',
    sk: 'DOC#<documentId>',
    note: 'status, S3 key, metadata. A GSI lists a user’s documents newest-first.',
  },
  {
    entity: 'IngestionJob',
    pk: 'SYSTEM#INGESTION',
    sk: 'JOB#<jobId>',
    note: 'covered documents and Bedrock job status. TTL 7 days after completion.',
  },
  {
    entity: 'AskSession',
    pk: 'USER#<sub>',
    sk: 'ASK_SESSION#<sessionId>',
    note: 'server-owned contextDocumentIds and an optional (nullable) Bedrock sessionId. 24 h TTL.',
  },
]

export const ACCESS_PATTERNS = [
  'List a user’s documents newest-first (GSI, opaque cursor).',
  'Fetch one document — the key lookup is itself the ownership check.',
  'Resolve an Ask session under the caller’s partition.',
  'Find ingestion jobs that cover a document (system partition).',
]

export const RELIABILITY_CARDS: Card[] = [
  {
    icon: Waypoints,
    title: 'Buffered, retried ingestion',
    body: 'SQS absorbs bursts with a retry budget sized around the Knowledge Base’s one-job limit. Any message reaching the dead-letter queue raises an alarm.',
  },
  {
    icon: RefreshCw,
    title: 'Status reconciliation',
    body: 'EventBridge runs the reconciler every minute. It polls Bedrock and moves documents to READY or FAILED — the ingestion Lambda never waits.',
  },
  {
    icon: Trash2,
    title: 'Stale-document cleanup',
    body: 'Every 15 minutes: UPLOAD_PENDING > 30 min with no object → FAILED; UPLOADED > 2 h with no active job → FAILED. INDEXING > 1 h only warns — Bedrock stays the authority.',
  },
  {
    icon: BellRing,
    title: '8 CloudWatch alarms → SNS',
    body: 'API errors and throttles, coordinator and reconciler errors, scheduler failures, reconciler-not-running, queue age and DLQ occupancy — one topic, one email.',
  },
  {
    icon: Activity,
    title: 'Structured logging',
    body: 'One JSON event per request and pipeline step, with request ids and latency. Content is never logged.',
  },
  {
    icon: Timer,
    title: 'Bounded time',
    body: 'The API Lambda times out at 28 s, just under the 30 s HTTP API limit; Ask’s recovery retry is capped at one.',
  },
  {
    icon: Rocket,
    title: 'Safe deploys',
    body: 'One script rebuilds, tests, shows the diff, asks for confirmation and deploys a single stack. Missing configuration fails synthesis instead of falling back.',
  },
  {
    icon: Gauge,
    title: 'Careful clients',
    body: 'Safe reads retry on 429/503 with backoff. Uploads and Ask are never blindly retried.',
  },
]

export const DECISIONS = [
  { title: 'Lambda + SnapStart, not containers', why: 'Scales to zero with acceptable Java cold starts. ECS stays a fallback only if measured latency demands it.' },
  { title: 'Presigned direct uploads', why: 'File bytes bypass the API Lambda, so upload size and burstiness don’t shape the API tier.' },
  { title: 'SQS only where work is async', why: 'It buffers ingestion. It is deliberately absent from synchronous search and Ask calls.' },
  { title: 'Bedrock Knowledge Bases, not a hand-rolled RAG', why: 'Managed parsing (including audio and video), chunking, embeddings and retrieval instead of glue code we would have to own.' },
  { title: 'S3 Vectors, not an always-on vector database', why: 'Serverless storage and query with no baseline cost and per-user metadata filtering.' },
  { title: 'Search and Ask kept separate', why: 'Search uses Retrieve and no language model. Only Ask pays for generation.' },
  { title: 'Server-owned conversation state', why: 'Ask sessions and their document context live in DynamoDB under the caller’s key, so nothing authoritative is trusted from the client.' },
  { title: 'Everything in CDK', why: 'Reproducible, reviewable infrastructure with a diff before every deploy.' },
]

export const COST_ROWS = [
  { service: 'Lambda', idle: 'Nothing', scales: 'Per request' },
  { service: 'API Gateway (HTTP API)', idle: 'Nothing', scales: 'Per request' },
  { service: 'DynamoDB', idle: 'Storage only', scales: 'On-demand reads/writes' },
  { service: 'S3, SQS, EventBridge', idle: 'Storage only', scales: 'Per request' },
  { service: 'S3 Vectors', idle: 'Vector storage only', scales: 'Per query' },
  { service: 'Bedrock', idle: 'Nothing', scales: 'Per file ingested, per Ask' },
]

export const COST_NOTES = [
  'No NAT gateway, no RDS or Aurora, no OpenSearch, no Redis, no always-on EC2 or containers.',
  'The costly work is per file (parsing and embeddings) and per Ask (generation). Plain search calls no language model.',
]

export const CHALLENGES = [
  {
    title: 'Search that never says “nothing”',
    problem: 'Vector search always returns its nearest neighbours, so an absent topic still produced results.',
    fix: 'A minimum similarity applied per chunk before de-duplication, calibrated on ~75 labelled queries. Absent topics returned empty; Ask uses the same gate as a preflight and refuses deterministically.',
  },
  {
    title: 'One ingestion job at a time',
    problem: 'A Knowledge Base rejects a second concurrent job across all its data sources.',
    fix: 'Treat the conflict as backpressure: documents stay UPLOADED and SQS retries within a budget sized for it. The DLQ stayed empty under real bursts.',
  },
  {
    title: 'Images silently rejected',
    problem: 'Data Automation ran text-only unless multimodal mode was set explicitly, so every image looked “unsupported”.',
    fix: 'Configured multimodal parsing at creation (it is immutable) and verified with real images, audio and video.',
  },
  {
    title: 'Models can’t see filenames',
    problem: '“Do I have my assignment?” failed even with the right chunks retrieved, and “explain this assignment” had nothing to refer to.',
    fix: 'File lookups answer from metadata, and resolved files become server-owned conversation context that scopes follow-ups.',
  },
  {
    title: 'Flaky retrieval on vague follow-ups',
    problem: 'RetrieveAndGenerate returned zero references for some short messages, though a scoped Retrieve with the same filter found the file (5 of 12 wordings failed).',
    fix: 'Measured before fixing: prefixing, normalisation and an orchestration prompt did not help. One bounded, file-anchored retry recovered every wording (16/16).',
  },
  {
    title: 'A timeout found only live',
    problem: 'A retrying Ask turn ran past the API Lambda’s 10 s limit — invisible to unit tests.',
    fix: 'Raised to 28 s under the gateway’s 30 s ceiling, enforced by an infrastructure test, and re-verified through the deployed alias.',
  },
  {
    title: 'Deploy accidents',
    problem: 'A stale build once shipped, and a placeholder identity-provider setting nearly reached a real deploy.',
    fix: 'A single deploy script that always rebuilds and diffs, plus configuration that fails fast instead of defaulting.',
  },
  {
    title: 'What only production reveals',
    problem: 'Wrong IAM action names, cross-region inference-profile permissions and decimal timestamps passed every unit test.',
    fix: 'An opt-in verification harness that runs the real service code against the real Knowledge Base, read-only.',
  },
]

export const TRADEOFFS = [
  'One region, one Knowledge Base ingestion job at a time — fine for a personal corpus, a limit at scale.',
  'The relevance threshold is corpus-calibrated and tunable, not universal; very vague queries can fall below it.',
  'Ask latency is roughly 2–5 s normally and up to ~12 s when a recovery retry runs.',
  'A conversation that began with a file lookup keeps follow-ups on that file until a new lookup or conversation.',
]

export const FUTURE = [
  'Deletion, coordinated across the original object, staged copy and sidecar, DynamoDB and the vector index.',
  'A fully deterministic Ask path: scoped retrieval plus a direct model call, replacing the retry.',
  'Multipart upload for very large media; streaming answers.',
  'A contextual-grounding check via Bedrock Guardrails; near-duplicate detection.',
  'A CDN in front of private source files; proactive date and expiry reminders.',
]

export const API_ROWS = [
  { method: 'GET', path: '/api/v1/health', purpose: 'Public liveness check' },
  { method: 'POST', path: '/api/v1/uploads', purpose: 'Register files, get presigned upload URLs (single or bulk)' },
  { method: 'GET', path: '/api/v1/documents', purpose: 'List the caller’s documents, newest first' },
  { method: 'GET', path: '/api/v1/documents/{id}', purpose: 'One document and its processing state' },
  { method: 'GET', path: '/api/v1/documents/{id}/access-url', purpose: 'Short-lived URL to open or download, after an ownership check' },
  { method: 'POST', path: '/api/v1/search', purpose: 'Gated semantic search — no language model' },
  { method: 'POST', path: '/api/v1/ask', purpose: 'Grounded answers with citations and an opaque session id' },
]

export const API_NOTES = [
  'Every route except health requires a Cognito JWT with the API scope.',
  'Cursors and session ids are opaque. Errors share one envelope: code, message, request id, retryable.',
]
