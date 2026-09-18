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

- [x] Initialize Java backend project
- [x] Add minimal Lambda handler structure
- [x] Implement `GET /api/v1/health`
- [x] Add structured logging
- [x] Add basic error response model
- [x] Add backend tests

## Frontend

- [x] Initialize React/Vite frontend
- [x] Add environment-based API URL
- [x] Create minimal app shell
- [x] Add health-check integration for smoke testing (dev-only indicator, not part of product UI)

## Infrastructure

- [x] Initialize Java CDK project under `Infra/`
- [x] Define API Gateway HTTP API
- [x] Define Java Lambda
- [x] Enable Java SnapStart where supported/configured
- [x] Wire `/api/v1/health`
- [x] Add CloudWatch logs
- [x] `cdk synth` succeeds
- [x] Deploy first stack — `MemoryLayerApiStack` deployed to `ap-south-1`
      (`https://0sby0h3d1a.execute-api.ap-south-1.amazonaws.com`); `MemoryLayerFrontendStack`
      also deployed (Amplify app `d28nd6lc9fjyiv`,
      `https://main.d28nd6lc9fjyiv.amplifyapp.com`) after the GitHub App/token prerequisite
      was completed.

## Verification

- [x] Public deployed frontend opens — verified via curl (`200`, correct HTML/assets);
      direct navigation to a client-side route also returns `200`, confirming the Amplify
      SPA rewrite rule works
- [x] Deployed `/api/v1/health` returns `200` — verified via curl (cold start ~3.2s with
      SnapStart restore, warm ~0.26s); CloudWatch logs show clean SnapStart RESTORE_REPORT
      and structured `RequestLog` lines with no errors
- [x] Frontend can reach deployed API — verified via CORS: simulated the browser's
      preflight + GET from the Amplify origin against `/api/v1/health`, both succeed. Note:
      the production bundle itself has no code path that calls the API yet (the Phase 1
      health-check UI is intentionally dev-only and tree-shaken from production builds), so
      this was verified at the network/CORS level rather than observed on the live site.
- [ ] Commit baseline — awaiting user approval to commit/push

### Phase 1 notes

- Amplify GitHub App was installed with access limited to `vansh-005/first-commit`, and the
  PAT is stored in Secrets Manager (`ap-south-1`) as `memory-layer/amplify-github-token`.
- Creating `CfnBranch` did not itself trigger a build (`enableAutoBuild` only applies to
  future pushes); the first build was started manually via
  `aws amplify start-job --job-type RELEASE`.

### Phase 1 exit condition

```text
browser -> deployed frontend -> deployed API -> {"status":"ok"}
```

Do not move on until this works.

---

# Phase 2 — Authentication

## Cognito

- [x] Create Cognito User Pool (`MemoryLayerAuthStack`, CDK)
- [x] Configure app client (public/no-secret, PKCE, `AuthorizationCodeGrant`)
- [x] Configure callback/logout URLs (Amplify origin `/login` and `/`, plus
      `http://localhost:5173` for dev)
- [x] Configure email/password authentication (native Cognito sign-in, self-signup enabled)
- [x] Configure Cognito managed login / hosted auth flow (Cognito-prefix domain
      `memory-layer-auth-<account-suffix>`)
- [x] Configure Google as federated identity provider (attribute mapping: `email`→`email`,
      `email_verified`→`email_verified`, `name`→`name`, `picture`→`picture`)
- [x] Custom resource-server scope `memory-api/access`, requested by the SPA client and
      required by `/api/v1/me` via native HTTP API `AuthorizationScopes` (amendment beyond
      the original plan)

## Google OAuth

- [x] Create/configure Google OAuth client (`326049175774-...apps.googleusercontent.com`)
- [x] Configure Cognito redirect URI in Google
- [x] Store client secret securely — verified: `memory-layer/google-oauth-client-secret`
      exists in Secrets Manager and is wired via a CloudFormation dynamic reference
      (confirmed in the synthesized template: never plaintext)
- [x] Verify Google login end-to-end (server-side wiring) — confirmed by calling Cognito's
      real `/oauth2/authorize?...&identity_provider=Google` endpoint directly: it 302s to
      `accounts.google.com/o/oauth2/v2/auth` with the correct `client_id` and a
      `redirect_uri` of Cognito's own `/oauth2/idpresponse`, proving the User Pool client,
      callback URL registration, and Google IdP config are all correctly linked. Actually
      completing a Google account login/consent in a browser is a manual step only you can
      do (see below).

## API authorization

- [x] Configure API Gateway JWT authorizer (`CfnAuthorizer`, L1 — see `ApiStack` Javadoc for
      why not the L2)
- [x] Protect `/api/v1/me` (diagnostic route) with the authorizer + required scope;
      `/api/v1/health` remains public. No other routes exist yet to protect.
- [x] Backend reads validated JWT claims (`AuthenticatedUserResolver`, reads the API
      Gateway authorizer context aws-serverless-java-container attaches to the request)
- [x] Backend derives canonical `userId` from `sub`

## Frontend

- [x] Login page (`react-oidc-context`, not Amplify Auth)
- [x] `Continue with Google` (hosted-page redirect with `identity_provider=Google` hint)
- [x] Email/password fallback (redirects to the same hosted page without the hint, showing
      Cognito's native form — see Phase 2 plan for the FRONTEND.md deviation this implies)
- [x] Logout (manual redirect to Cognito's non-standard `/logout` endpoint)
- [x] Authenticated route handling (`ProtectedRoute`, guards all `/app/*` routes)
- [x] Token attachment to API calls (`Authorization: Bearer <access_token>`, access token
      read via a shared `oidc-client-ts` `UserManager`)

## Verification

- [x] Unauthenticated protected request returns `401` — verified: `GET /api/v1/me` with no
      token and with a garbage token both return `401 {"message":"Unauthorized"}`
- [x] Email/password path is reachable — verified: Cognito's hosted login page (no
      `identity_provider` hint) renders `200` with "Sign in", "Sign up", "Password", and
      "Continue with Google" all present, no client/redirect errors
- [x] Google login path is reachable — verified: the Google-hinted authorize request 302s
      correctly to Google's consent screen (see above)
- [x] Deployed frontend serves the real Phase 2 build — verified: production JS bundle
      contains the real Cognito issuer, hosted domain, client ID, and `memory-api/access`
      scope (confirmed via direct string search in the fetched bundle)
- [x] Authenticated API can read `sub` **in the live deployed app** — confirmed manually in
      the browser: Google sign-in completes, the Home page shows the signed-in email, and
      the authenticated `GET /api/v1/me` call returns `200` with the real `sub`. Full round
      trip (real login → real token → `/me` → displayed `sub`) verified end to end.

### Phase 2 notes

- Amplify env vars alone don't trigger a rebuild; the same lesson as Phase 1's GitHub
  connection applied here too — a manual `start-job` was used once before the code was
  pushed, then the real push correctly auto-triggered the build that shipped the actual
  Phase 2 frontend code (`enableAutoBuild` on `CfnBranch`).
- Deployed resources: User Pool `ap-south-1_EcoZ4MroP`, SPA client
  `2pa5i5dfkq23gok012n92t5okl`, hosted domain `memory-layer-auth-907297`.
- **Post-deploy bugfix**: the first browser login surfaced a `500` on `GET /api/v1/me`.
  CloudWatch showed `ClassCastException: LinkedHashMap cannot be cast to
  HttpApiV2JwtAuthorizer`. Root cause: `LambdaHandler` used
  `RequestHandler<HttpApiV2ProxyRequest, ...>`, which relies on AWS Lambda's own default
  event deserialization — that path does not honor aws-serverless-java-container's
  `@JsonDeserialize` annotation on `HttpApiV2AuthorizerMap`, so the JWT authorizer's claims
  landed as a raw `LinkedHashMap` instead of the typed model. Fixed by switching to
  `RequestStreamHandler` + `proxyStream(...)`, which parses the raw event with the
  container library's own configured `ObjectMapper`. No JWT decoding/revalidation logic was
  touched — API Gateway still does all of that. Covered by a new regression test
  (`LambdaHandlerTest`) that invokes the real Lambda entrypoint with a raw HTTP API v2 event
  JSON, which a MockMvc-based test (injecting an already-typed context) could not have
  caught. Redeployed `MemoryLayerApiStack` only; `MemoryLayerAuthStack` had no changes.

### Phase 2 exit condition

```text
Google login -> Cognito -> JWT -> protected API
```

**Met.** Verified end to end in the browser (see above).

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
