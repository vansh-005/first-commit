# TASKS.md

## Goal

Ship a working, deployed MVP for the multimodal personal memory layer.

The core success path is:

```text
Sign in
  ↓
Upload files
  ↓
Files are processed
  ↓
Browse uploads
  ↓
Semantic search
  ↓
Ask questions
  ↓
Open cited source
```

Do not start stretch features until this path works end-to-end.

---

# Phase 0 — Project foundation

## Repository

- [x] Define product
- [x] Define high-level architecture
- [x] Define data model
- [x] Define API contract
- [x] Add `AGENTS.md`
- [x] Add `CLAUDE.md`
- [x] Add `TASKS.md`

## Local / AWS setup

- [x] AWS CLI configured
- [x] IntelliJ AWS Toolkit connected
- [x] AWS CDK installed
- [x] CDK environment bootstrapped
- [x] AWS Agent Toolkit configured for Claude Code
- [x] Claude Code can inspect AWS account

---

# Phase 1 — Skeleton + first deployment

## Backend

- [ ] Initialize Java backend project
- [ ] Add minimal Lambda handler structure
- [ ] Implement `GET /api/v1/health`
- [ ] Add structured logging
- [ ] Add basic error response model
- [ ] Add backend tests

## Frontend

- [ ] Initialize React/Vite frontend
- [ ] Add environment-based API URL
- [ ] Create minimal app shell
- [ ] Add health-check integration for smoke testing

## Infrastructure

- [ ] Initialize Java CDK project under `Infra/`
- [ ] Define API Gateway HTTP API
- [ ] Define Java Lambda
- [ ] Enable Java SnapStart where supported/configured
- [ ] Wire `/api/v1/health`
- [ ] Add CloudWatch logs
- [ ] `cdk synth` succeeds
- [ ] Deploy first stack

## Verification

- [ ] Public deployed frontend opens
- [ ] Deployed `/api/v1/health` returns `200`
- [ ] Frontend can reach deployed API
- [ ] Commit baseline

### Phase 1 exit condition

```text
browser -> deployed frontend -> deployed API -> {"status":"ok"}
```

Do not move on until this works.

---

# Phase 2 — Authentication

## Cognito

- [ ] Create Cognito User Pool
- [ ] Configure app client
- [ ] Configure callback/logout URLs
- [ ] Configure email/password authentication
- [ ] Configure Cognito managed login / hosted auth flow
- [ ] Configure Google as federated identity provider

## Google OAuth

- [ ] Create/configure Google OAuth client
- [ ] Configure Cognito redirect URI in Google
- [ ] Store client secret securely
- [ ] Verify Google login end-to-end

## API authorization

- [ ] Configure API Gateway JWT authorizer
- [ ] Protect all endpoints except `/health`
- [ ] Backend reads validated JWT claims
- [ ] Backend derives canonical `userId` from `sub`

## Frontend

- [ ] Login page
- [ ] `Continue with Google`
- [ ] Email/password fallback
- [ ] Logout
- [ ] Authenticated route handling
- [ ] Token attachment to API calls

## Verification

- [ ] Unauthenticated protected request returns `401`
- [ ] Email/password login works
- [ ] Google login works
- [ ] Authenticated API can read `sub`

### Phase 2 exit condition

```text
Google login -> Cognito -> JWT -> protected API
```

---

# Phase 3 — File library and direct upload

## DynamoDB

- [ ] Create `MemoryLayer` table
- [ ] PAY_PER_REQUEST billing
- [ ] PK / SK schema
- [ ] Add chronological document-listing GSI

Recommended document listing index:

```text
GSI1PK = USER#<userId>
GSI1SK = <createdAt>#<documentId>
```

- [ ] Backend repository/data-access layer
- [ ] Document status enum

## S3

- [ ] Create private uploads bucket
- [ ] Block public access
- [ ] Configure encryption
- [ ] Configure browser upload CORS
- [ ] Define user/document object-key convention

```text
users/<sub>/documents/<documentId>/original/<fileName>
```

## API

- [ ] Implement `POST /api/v1/uploads`
- [ ] Generate backend UUID document IDs
- [ ] Generate server-owned S3 keys
- [ ] Write `UPLOAD_PENDING` records
- [ ] Return presigned PUT URLs
- [ ] Support multiple files in one request

- [ ] Implement `GET /api/v1/documents`
- [ ] Newest-first ordering
- [ ] Pagination cursor

- [ ] Implement `GET /api/v1/documents/{id}`
- [ ] Implement `GET /api/v1/documents/{id}/access-url`
- [ ] Ownership checks before signing GET URLs

## Frontend

- [ ] File picker
- [ ] Drag-and-drop area
- [ ] Multiple-file selection
- [ ] Per-file upload progress
- [ ] Client-side upload concurrency cap
- [ ] File library view
- [ ] Category tabs
- [ ] Processing status UI
- [ ] Open/download file via access URL

## Verification

- [ ] Upload bytes bypass backend
- [ ] File lands in correct user S3 prefix
- [ ] DynamoDB record belongs to authenticated user
- [ ] User A cannot access User B document
- [ ] Bulk upload works with several files

### Phase 3 exit condition

```text
login -> upload files -> S3 -> browse files in library
```

---

# Phase 4 — Asynchronous ingestion

## Event pipeline

- [ ] S3 `ObjectCreated` notification
- [ ] SQS ingestion queue
- [ ] SQS DLQ
- [ ] Configure retry/redrive policy
- [ ] Ingestion coordinator Lambda
- [ ] Controlled Lambda concurrency

## Knowledge Base

- [ ] Create/configure Bedrock Knowledge Base
- [ ] Configure S3 Vectors
- [ ] Configure embedding model
- [ ] Configure multimodal/BDA data source
- [ ] Configure text-document data source
- [ ] Verify supported file types
- [ ] Create metadata sidecars

Required metadata:

```text
userId
documentId
mediaCategory
fileName
```

## Processing state

- [ ] S3 event sets document `UPLOADED`
- [ ] Ingestion coordinator starts incremental ingestion job
- [ ] Store ingestion job state
- [ ] Set affected documents to `INDEXING`

## Ingestion job tracking

- [ ] Create ingestion-job entity

```text
PK = SYSTEM#INGESTION
SK = JOB#<jobId>
```

- [ ] Store affected document IDs
- [ ] Add TTL for completed job records
- [ ] EventBridge scheduled reconciler
- [ ] Call `GetIngestionJob`
- [ ] Mark documents `READY` on success
- [ ] Mark documents `FAILED` on failure
- [ ] Store safe failure reason

## Verification

- [ ] PDF becomes searchable
- [ ] Image becomes searchable
- [ ] At least one audio/video input becomes searchable
- [ ] At least one text document path works
- [ ] Failed processing is visible in UI
- [ ] Burst of multiple uploads does not create uncontrolled ingestion calls

### Phase 4 exit condition

```text
upload -> async processing -> READY
```

---

# Phase 5 — Semantic search

## Backend

- [ ] Implement `POST /api/v1/search`
- [ ] Call Bedrock Knowledge Base `Retrieve`
- [ ] Inject `userId = JWT.sub` metadata filter
- [ ] Support optional media-category filter
- [ ] Convert chunk-level results to document-centric results
- [ ] Resolve document metadata from DynamoDB
- [ ] Return snippets
- [ ] Return media timestamps when available

## Frontend

- [ ] Search input
- [ ] Search loading state
- [ ] Search result cards
- [ ] File type / source display
- [ ] Relevant snippet
- [ ] Open source file
- [ ] Seek to timestamp for media if available

## Security

- [ ] Confirm client cannot alter tenant filter
- [ ] Confirm User A searches cannot retrieve User B content

## Cost

- [ ] Search path uses `Retrieve`, not `RetrieveAndGenerate`

### Phase 5 exit condition

Example query:

```text
"that screenshot about AWS hackathon credits"
```

returns the correct uploaded memory.

---

# Phase 6 — Ask your memory

## Backend

- [ ] Implement `POST /api/v1/ask`
- [ ] Call `RetrieveAndGenerate`
- [ ] Inject authenticated user metadata filter
- [ ] Return generated answer
- [ ] Return Bedrock `sessionId`
- [ ] Accept sessionId for follow-up turns
- [ ] Parse citations
- [ ] Map citations to application documents
- [ ] Verify document ownership
- [ ] Generate short-lived citation access URLs

## Frontend

- [ ] Ask UI
- [ ] Multi-turn session handling
- [ ] Answer rendering
- [ ] Citation chips/cards
- [ ] Click citation -> original source
- [ ] Show source snippet
- [ ] Show media timestamp when available

## Verification

Example:

```text
"What did my internship document say about relocation?"
```

returns:

```text
grounded answer + clickable citation
```

### Phase 6 exit condition

```text
ask -> grounded answer -> citation -> source file
```

---

# Phase 7 — Reliability and observability

- [ ] CloudWatch structured logs for API
- [ ] CloudWatch structured logs for ingestion
- [ ] API latency visibility
- [ ] Bedrock latency logging
- [ ] SQS DLQ visibility
- [ ] Clear frontend retry UX
- [ ] Handle `429`
- [ ] Handle Bedrock upstream errors
- [ ] Handle expired presigned URLs
- [ ] Handle unsupported file types
- [ ] Handle processing failures
- [ ] Demo smoke-test checklist

---

# Phase 8 — UX polish

- [ ] Better landing page
- [ ] Clear product tagline
- [ ] Smooth upload progress
- [ ] Processing skeletons
- [ ] Empty-state UX
- [ ] Search result highlighting
- [ ] File previews where easy
- [ ] Mobile-friendly layout
- [ ] Good error messages
- [ ] Demo sample files

---

# Phase 9 — Stretch features

Only start after the MVP works end-to-end.

## Large media

- [ ] Multipart S3 upload
- [ ] Multipart API endpoints
- [ ] Resume/retry failed parts

## Deletion

- [ ] `DELETE /documents/{id}`
- [ ] Remove source
- [ ] Remove metadata sidecar
- [ ] Re-sync Knowledge Base
- [ ] Ensure deleted content is no longer retrievable

## Proactive memory

- [ ] Extract meaningful dates
- [ ] Distinguish expiry/renewal/deadline/payment dates
- [ ] Store actionable dates
- [ ] EventBridge scheduling
- [ ] Notifications

## Other possible stretch

- [ ] Duplicate detection
- [ ] Related-memory recommendations
- [ ] Auto-categories/tags
- [ ] Conversation persistence
- [ ] Streaming `/ask`
- [ ] richer video/audio preview

---

# Final hackathon checklist

## Product

- [ ] Core user flow works
- [ ] Semantic search demo is convincing
- [ ] Q&A citations open the source
- [ ] Google sign-in works

## Architecture

- [ ] Architecture diagrams finalized
- [ ] AWS services match documentation
- [ ] No unexplained always-on cost
- [ ] Cost story is ready

## Deployment

- [ ] Stable live frontend URL
- [ ] Stable API URL
- [ ] Fresh-browser test completed
- [ ] Incognito login test completed
- [ ] Demo account / sample data ready

## Submission

- [ ] README
- [ ] Architecture diagram
- [ ] Live URL
- [ ] Demo video
- [ ] Deployment instructions
- [ ] Cost / design decisions summarized
- [ ] Known limitations documented

---

# Current next action

Start **Phase 1 only**.

First Claude Code task:

```text
Read AGENTS.md, CLAUDE.md, and all files in Docs/.

We are starting Phase 1 only.

Inspect the repository and propose a concise implementation plan for:
- the Java backend skeleton,
- the React frontend skeleton,
- the Java CDK infrastructure,
- a deployed GET /api/v1/health endpoint.

Do not implement later phases.
Do not create AWS resources yet.
Return the proposed file structure, AWS resources required, and exact implementation steps for review.
```
