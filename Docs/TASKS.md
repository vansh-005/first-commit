# TASKS.md — Delivered scope

Recollect is a **submission-ready MVP**. The application is feature-frozen; this file records what was delivered,
how it was verified, and the deliberate trade-offs. Design rationale lives in `ARCHITECTURE.md`; runbooks and
incident lessons live in `OPERATIONS.md`.

The core success path works end to end on AWS:

```text
Sign in → upload files → asynchronous processing → browse the library
        → semantic search → ask questions → open the cited source
```

---

## Delivered scope

### Foundation and infrastructure
- [x] Java 21 backend (Spring Boot on Lambda, SnapStart), React + TypeScript frontend, Java AWS CDK app
- [x] All infrastructure as code (CDK stacks: auth, data, ingestion, API, frontend, alarms); one sanctioned deploy
      wrapper (`Infra/deploy.ps1`) that rebuilds the backend jar, runs tests and shows the diff before deploying
- [x] Deployed `GET /api/v1/health`, HTTP API + JWT authorizer, Amplify-hosted frontend
- [x] `Infra/smoke-test.ps1` for post-deploy checks

### Authentication and tenancy
- [x] Amazon Cognito: Google federated sign-in and email/password fallback
- [x] Canonical identity is the validated JWT `sub`; the backend never stores passwords
- [x] Tenant isolation enforced server-side: DynamoDB partitioning by user, retrieval filters injected by the
      server, ownership checks before any signed access URL

### Upload and ingestion
- [x] Single and bulk upload share one pipeline; browser uploads straight to S3 via presigned URLs
- [x] S3 → SQS (with DLQ) → Ingestion Coordinator → Bedrock Knowledge Base ingestion
- [x] Two parsing paths: Bedrock Data Automation (PDF, image, audio, video) and the default text parser;
      Titan Text Embeddings V2 vectors stored in S3 Vectors
- [x] Document status lifecycle (`UPLOAD_PENDING → UPLOADED → INDEXING → READY | FAILED`) with a scheduled
      reconciler and stale-document cleanup
- [x] Handling of the one-ingestion-job-at-a-time Knowledge Base limit (retry via the queue, no lost uploads)

### Search and Ask
- [x] `/search` on Bedrock `Retrieve` with a corpus-calibrated relevance gate (0.62), so absent topics return no
      results instead of arbitrary nearest neighbours
- [x] `/ask` on `RetrieveAndGenerate` (Amazon Nova Lite) behind a preflight relevance check, with a deterministic
      no-answer and citations that open the source file
- [x] File discovery by filename metadata ("find / show / do I have …"), server-owned conversation context for
      follow-ups ("explain this assignment"), one bounded recovery retry, 28 s Lambda / 30 s API timeouts

### Frontend
- [x] Landing, sign-in, Home, Library (status, filters, download), Search (highlighted snippets, media timestamps),
      Ask (citations, follow-ups), global upload dialog, light/dark themes, responsive layout, accessibility pass
- [x] Public `/engineering` page with canonical architecture diagrams (`Docs/diagrams/`)

### Reliability and operations
- [x] Structured JSON logs with hashed user identifiers and no file or question content
- [x] Eight CloudWatch alarms → SNS email notification; DLQ occupancy alarm
- [x] Runbook and deploy procedure in `OPERATIONS.md`

---

## Verification

- Backend, frontend and infrastructure test suites pass (`mvn test` in `Backend/` and `Infra/`;
  `npm test`, `npx tsc --noEmit`, `npm run lint`, `npm run build` in `Frontend/`).
- Search and Ask relevance behavior verified against the deployed API and a labelled query set (~75 queries on the
  current test corpus); regression suites cover isolation between users, no-answer, file discovery, follow-ups
  and citations.
- `cdk synth` is clean; deployments go through `Infra/deploy.ps1` and are checked with the health endpoint and
  CloudWatch logs.

---

## Deliberate trade-offs and future work

These are product-evolution items, not gaps in the MVP. Each was consciously deferred to keep the delivered system
small, correct and low-cost.

| Area | Why deferred | Direction |
|---|---|---|
| Document deletion | Correct deletion must coordinate the original S3 object, the Knowledge Base staging copy and metadata sidecar, the DynamoDB record and the vector state, or deleted content stays retrievable | `DELETE /documents/{id}` with a coordinated Knowledge Base re-sync |
| Multipart upload for very large media | Presigned single-part upload covers the supported file sizes | S3 multipart with resumable parts |
| Streaming answers | `RetrieveAndGenerate` is request/response; the 28 s budget covers typical turns | Streaming `/ask` |
| Proactive memory (dates, renewals, reminders) | Reactive retrieval is the core value | Extracted actionable dates + EventBridge scheduling |
| Duplicate detection, related memories, auto-tags | Do not change the core demo path | Byte-hash then near-duplicate detection |
| Per-document ingestion failure attribution | A Knowledge Base ingestion job reports aggregate status | Finer-grained reporting when the service exposes it |
| Higher ingestion concurrency | One Knowledge Base ingestion job runs at a time | Sharded Knowledge Bases if the corpus outgrows it |
| Relevance threshold | 0.62 is calibrated to the current corpus | Re-calibrate against a larger labelled set |

---

## Submission checklist

- [x] Live frontend and API, Google and email sign-in
- [x] Architecture diagrams and public engineering page
- [x] Cost story: on-demand / serverless, no always-on infrastructure (see `ARCHITECTURE.md` §15)
- [x] README
- [ ] Demo video link (added to the README when published)
- [ ] CI/CD workflow (final repository change)
- [ ] Final cleanup of coding-agent files (`AGENTS.md`, `CLAUDE.md`, `Docs/KNOWLEDGE_TRANSFER.md`, `Docs/AWS.md`) after CI/CD
