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

- [x] Create `MemoryLayer` table — deployed, `ACTIVE`
- [x] PAY_PER_REQUEST billing — verified via `describe-table`
- [x] PK / SK schema — verified via `describe-table`
- [x] Add chronological document-listing GSI

```text
GSI1PK = USER#<userId>
GSI1SK = <createdAt>#<documentId>
```

GSI1 verified `ACTIVE` with the correct key schema via `describe-table`.

- [x] Backend repository/data-access layer (`DocumentRepository`, DynamoDB Enhanced Client)
- [x] Document status enum

## S3

- [x] Create private uploads bucket — deployed
- [x] Block public access — verified (`BlockPublicAcls`/`IgnorePublicAcls`/`BlockPublicPolicy`/`RestrictPublicBuckets` all `true`; unauthenticated GET returns `403`)
- [x] Configure encryption — verified (SSE-S3/AES256)
- [x] Configure browser upload CORS — verified: `PUT`/`GET`/`HEAD` from the Amplify origin + `localhost:5173`, matching the amendment exactly
- [x] Define user/document object-key convention

```text
users/<sub>/documents/<documentId>/original/<fileName>
```

Verified via a real upload: object landed at exactly this path under the authenticated test user's own sub.

## API

- [x] Implement `POST /api/v1/uploads` — verified with a real authenticated request (real Cognito access token, obtained by scripting the actual hosted-UI login + code exchange for a throwaway test user — no browser available in this environment)
- [x] Generate backend UUID document IDs
- [x] Generate server-owned S3 keys
- [x] Write `UPLOAD_PENDING` records — verified: DynamoDB item status stayed `UPLOAD_PENDING` after the S3 PUT completed, exactly per the amendment (no completion endpoint, no auto-transition)
- [x] Return presigned PUT URLs — verified: performed an actual presigned `PUT`, got `200`
- [x] Support multiple files in one request (bulk) — covered by `UploadControllerTest`; not re-verified live this pass (single-file live test was sufficient to prove the deployed path)

- [x] Implement `GET /api/v1/documents` — verified, returns the uploaded item
- [x] Newest-first ordering — implemented via GSI1 `ScanIndexForward=false`; not separately re-verified live with multiple items this pass
- [x] Pagination cursor — implemented (opaque base64), covered by `DocumentCursorTest`

- [x] Implement `GET /api/v1/documents/{id}` — verified, `200` with correct data
- [x] Implement `GET /api/v1/documents/{id}/access-url` — verified, `200`; downloaded the file through the returned presigned URL and confirmed the content matched exactly what was uploaded
- [x] Ownership checks before signing GET URLs — implemented (S3-key-prefix check, `DocumentsControllerTest`)

## Frontend

- [x] File picker
- [x] Drag-and-drop area
- [x] Multiple-file selection
- [x] Per-file upload progress
- [x] Client-side upload concurrency cap (4 concurrent)
- [x] File library view
- [x] Category tabs
- [x] Processing status UI
- [x] Open/download file via access URL

Frontend is implemented and build/lint-clean, but not yet deployed this pass (only `MemoryLayerDataStack` and `MemoryLayerApiStack` were deployed) — not exercised in a real browser yet.

## Verification

- [x] Upload bytes bypass backend — confirmed: the presigned `PUT` went straight to S3; the API never saw the file bytes
- [x] File lands in correct user S3 prefix — confirmed via `head-object` on the real key
- [x] DynamoDB record belongs to authenticated user — confirmed (`PK = USER#<real test-user sub>`)
- [x] User A cannot access User B document — verified the ownership *mechanism*: an unknown documentId returns `404 DOCUMENT_NOT_FOUND`, and the lookup is structurally scoped to the authenticated user's own partition, so a foreign documentId is indistinguishable from a nonexistent one. Not verified with a second real user's genuine document (only one throwaway test user was created), since the code path is identical either way.
- [x] Bulk upload works with several files — covered by `UploadControllerTest`; not re-verified live this pass

### Phase 3 deployment notes

- **Bug found and fixed before this could pass**: `POST /uploads` initially returned `500`.
  CloudWatch showed `DynamoDbException: Missing the key PK in the item`. Root cause:
  `TableSchema.fromBean()` derives each DynamoDB attribute name from the Java property name
  (`getPk()` → `"pk"`) unless overridden — the table's actual key schema uses the literal
  uppercase `PK`/`SK`/`GSI1PK`/`GSI1SK`. Fixed by adding `@DynamoDbAttribute("PK")` etc. to
  `Document.java`, and added `DocumentTableSchemaTest` (asserts on the real attribute map
  `TableSchema` produces) so this class of mistake fails in `mvn test`, not in production.
  Backend rebuilt, `MemoryLayerApiStack` redeployed, then full verification re-run from
  scratch — all passed.
- Two of the `cdk deploy` attempts hit transient S3 asset-upload socket timeouts (once
  "not read from or written to within the timeout period", once a 10s connect timeout).
  Confirmed DNS/connectivity to the CDK asset bucket were fine in between; both were
  transient and resolved on retry, same category as a similar Phase 1 blip.
- A throwaway Cognito test user (`phase3-test@example.com`) and one test document/S3 object
  exist from this verification pass. Not deleted — will clean up on request.

### Phase 3 exit condition

```text
login -> upload files -> S3 -> browse files in library
```

Backend/data layer confirmed end to end via direct API calls. The browser leg (frontend
deployed + used live) is not done yet — frontend code is implemented and validated locally
but `MemoryLayerFrontendStack` wasn't part of this deploy.

---

# Phase 4 — Asynchronous ingestion

Implemented and validated via `mvn test`, `mvn package`, and `cdk synth` (all pass) plus a
pre-implementation AWS compatibility spike (see below). **Not yet deployed** — stopped for
review per plan, so the live-verification checklist below is intentionally still unchecked.

## Compatibility spike (before full implementation)

- [x] Upgrade `aws-cdk-lib` (2.199.0 → 2.270.0, for `AWS::S3Vectors::*` support) — `mvn test`/`cdk synth`/`cdk diff` all clean
- [x] Create S3 Vectors + KB + BDA data source in `ap-south-1` — done manually via CLI, then torn down
- [x] Verify BDA parsing end-to-end — a real PDF ingested and became retrievable; a hand-generated 1×1 PNG failed BDA with "file format was not supported" (root cause not fully isolated — flagged for early real-file testing, see Verification below)
- [x] Establish exact KB/BDA IAM permissions — confirmed against AWS's documented service-role policy, including the undocumented-until-tested requirement that the supplemental-storage policy needs `s3:DeleteObject`, not just `Get`/`Put`
- [x] Verify Titan Text Embeddings V2 + S3 Vectors — `Retrieve` returned correctly ranked, correctly metadata-tagged results for both the text and BDA/PDF paths
- [x] Test one multimodal (PDF) and one text-document path via the separated staging prefixes — both ingested and retrieved successfully
- [x] Spike resources (KB, data sources, IAM role, S3 Vectors bucket/index, supplemental bucket, test objects) fully deleted after the spike — confirmed via `get-knowledge-base`/`get-vector-bucket`/`get-role`/`head-bucket` all returning not-found

Two design amendments came out of this spike and are reflected in the implementation below:
Knowledge Base staging prefixes (`kb/multimodal/`, `kb/text/`) instead of two data sources
scanning `users/`, and a generous SQS retry budget sized around Bedrock's real
one-job-per-Knowledge-Base concurrency limit rather than a small fixed retry count. See
`Docs/ARCHITECTURE.md` §7.3.1–7.3.2 and §7.4.1 for the full detail and the exact errors that
drove each decision.

## Event pipeline

- [x] S3 `ObjectCreated` notification — `DataStack`, filtered to `users/` prefix only
- [x] SQS ingestion queue — `DataStack`
- [x] SQS DLQ — `DataStack`
- [x] Configure retry/redrive policy — `visibilityTimeout=5min`, `maxReceiveCount=20` (~100 min budget, sized around Bedrock's single-concurrent-job constraint, not a small fixed count)
- [x] Ingestion coordinator Lambda — `IngestionCoordinatorHandler` (`IngestionStack`)
- [x] Controlled Lambda concurrency — `reservedConcurrentExecutions(1)`

## Knowledge Base

- [x] Create/configure Bedrock Knowledge Base — `IngestionStack` (`CfnKnowledgeBase`, customer-managed, S3 Vectors storage)
- [x] Configure S3 Vectors — `CfnVectorBucket` + `CfnIndex` (dimension 1024, float32, cosine)
- [x] Configure embedding model — Titan Text Embeddings V2
- [x] Configure multimodal/BDA data source — scoped to `kb/multimodal/`
- [x] Configure text-document data source — scoped to `kb/text/`
- [x] Verify supported file types — PDF and plain text confirmed via the spike; **image/audio/video not yet confirmed with real files** (see Verification)
- [x] Create metadata sidecars — `KbMetadataSidecar`/`KbStagingService`, written beside the staged copy, not the original upload

Required metadata (implemented exactly):

```text
userId
documentId
mediaCategory
fileName
```

## Processing state

- [x] S3 event sets document `UPLOADED` — `IngestionCoordinatorHandler.markUploaded` (idempotent: only UPLOAD_PENDING -> UPLOADED)
- [x] Ingestion coordinator starts incremental ingestion job — per data source, per batch
- [x] Store ingestion job state — `IngestionJob` / `IngestionJobRepository`
- [x] Set affected documents to `INDEXING` — idempotent: only UPLOADED -> INDEXING

## Ingestion job tracking

- [x] Create ingestion-job entity

```text
PK = SYSTEM#INGESTION
SK = JOB#<jobId>
```

- [x] Store affected document IDs — as `"userId#documentId"` pairs (no documentId-only index exists; see `Docs/DATA_MODEL.md` §14)
- [x] Add TTL for completed job records — `expiresAt`, table's `timeToLiveAttribute`, 7-day retention after completion
- [x] EventBridge scheduled reconciler — `StatusReconcilerHandler`, `Schedule.rate(1 minute)`
- [x] Call `GetIngestionJob` — per in-progress job, each run
- [x] Mark documents `READY` on success — idempotent: only INDEXING -> READY
- [x] Mark documents `FAILED` on failure — idempotent: only INDEXING -> FAILED
- [x] Store safe failure reason — generic user-facing message; real Bedrock failure reasons go to CloudWatch only, never the document record

## Verification — deployed and confirmed end-to-end (2026-09-18/19)

Real files uploaded through the actual deployed API (not synthetic/degenerate test data), full pipeline exercised end to end: `UPLOAD_PENDING` -> `UPLOADED` -> `INDEXING` -> `READY`, each confirmed via DynamoDB, then confirmed retrievable via a direct `Retrieve` call with correct `userId`/`documentId`/`mediaCategory`/`fileName` metadata.

- [x] PDF becomes searchable — real PDF, `READY`, retrieved with correct text content
- [x] Image becomes searchable — **real JPEG and PNG both confirmed**, `READY`, retrieved with an accurate BDA-generated image description. The earlier spike's synthetic-image failures turned out to be a real, now-fixed bug: `parsingModality` must be explicitly set to `MULTIMODAL` on the data source's BDA config, or BDA silently runs in a text/document-only mode and rejects every image as "file format was not supported" — see "Deployment-time fixes" below. Full trace re-confirmed independently for one real JPEG (`photo_6106897192711820494_y.jpg`, documentId `de56b210-562e-434c-9992-87ef850a9a08`, ingestion job `WTYEPVGEML`): job `COMPLETE` (1 new document indexed, 0 failed) → DynamoDB `READY` → `Retrieve` by `documentId` returns `content.type=IMAGE` (not text — confirms the SDK's typed image-content field is real, not assumed) with a correct, non-trivial `x-amz-bedrock-kb-description` (an OCR-quality read of handwritten notes) and exact `userId`/`documentId`/`fileName`/`mediaCategory` metadata → a real semantic query with no `documentId` filter ranks it first (score 0.82) well above unrelated documents (~0.56) → filtering the same query to a *different* user's `userId` returns zero occurrences of this document, confirming tenant isolation holds at the KB retrieval layer itself, not just in the API layer
- [x] Audio and video both become searchable — a synthetic pure-tone WAV was correctly *rejected* by BDA ("no text content found in the files" — expected, not a bug, since there's no speech to transcribe); a **real speech recording** (Windows TTS) was correctly transcribed and retrieved, matching the spoken content exactly. A real MP4 (H.264 test pattern) was retrieved with an accurate BDA-generated video summary.
- [x] Text document path works — real Markdown file, `READY`, retrieved with correct content
- [ ] Failed processing is visible in UI — `FileCard` already renders a `FAILED` status label (Phase 3); not exercised against a real failed document in the browser this pass (only verified via DynamoDB/API)
- [x] Burst of multiple uploads does not create uncontrolled ingestion calls — 6 files uploaded simultaneously (repeated across several rounds during fix verification); confirmed via full ingestion-job history that **no two jobs ever ran concurrently** on the Knowledge Base, and `ConflictException` was repeatedly observed and correctly handled as retryable backpressure (see coordinator logs: "Ingestion job busy for data source ...; N document(s) will retry")
- [x] DLQ remained empty throughout all testing, including through several real bugs that caused repeated retries before their fixes deployed
- [x] EventBridge reconciler confirmed running exactly once per minute on schedule throughout the test window

### Deployment-time fixes found and applied (not yet committed — pending review)

1. **Missing explicit CDK dependency** — `CfnKnowledgeBase` only depended on the IAM Role resource (via `roleArn`), not the separate `AWS::IAM::Policy` resource holding its permissions, so CloudFormation could (and did, deterministically, twice) create the KB before its permissions existed. Fixed by building one explicit `Policy` construct and adding `knowledgeBase.getNode().addDependency(kbPolicy)`.
2. **Account Lambda concurrency limit** — this account's total concurrent-execution limit is 10 (`aws lambda get-account-settings`), and AWS requires ≥10 unreserved at all times, so `reservedConcurrentExecutions(1)` on the coordinator could never be satisfied. Removed; correctness still holds via the KB's single-job limit + `ConflictException` handling, not Lambda-level serialization.
3. **`StartIngestionJob`/`GetIngestionJob` wrong IAM resource ARN** — assumed a `.../data-source/*` sub-resource; the real `AccessDeniedException` named the bare Knowledge Base ARN as the checked resource. Fixed.
4. **BDA's actual invocation profile is cross-region** — for a KB in `ap-south-1`, BDA invokes through an APAC profile hosted in `ap-northeast-1` under this account, not the same-region AWS-owned profile the official docs example shows. Fixed by wildcarding the region segment in the `BDAInvoke`/`BDAGetStatus` policy resources.
5. **`parsingModality` never set** — without explicitly setting it to `MULTIMODAL` on the BDA data source's `bedrockDataAutomationConfiguration`, BDA silently ran in a text/document-only mode; every real JPEG/PNG/WAV/MP4 was rejected as an unsupported format and only PDF worked. This is the root cause behind the earlier spike's unresolved synthetic-image failures.
6. **Job tracking ordering bug (found via real concurrent traffic)** — `IngestionCoordinatorHandler` used to mark documents `INDEXING` *before* saving the `IngestionJob` DynamoDB record. With Lambda concurrency no longer reserved to 1 (fix #2), two invocations can race to update the same `Document`; the loser's write throws before the job record is ever saved, orphaning a real, already-started Bedrock job with no tracking record — the reconciler can never find it, and it silently self-heals only once the orphaned job finishes on its own. Fixed by saving the job record immediately after `StartIngestionJob` succeeds, and making the per-document status update retry once on a version conflict by re-reading the current record.
7. Two DataSource-recreation naming collisions during iteration (`AWS::Bedrock::DataSource` names must be unique per KB, and CloudFormation's replace-then-delete ordering collided with that) were resolved by deleting the stale data source via CLI before retrying `cdk deploy` — not a code bug, just an operational note for anyone changing a data source's parsing configuration later.

### Known residual behavior (not a correctness bug, but worth documenting)

When a burst of files across the two data sources contends for the Knowledge Base's single job slot, whichever coordinator invocation happens to win the race only tracks its *own* local batch in the `IngestionJob` record — even though Bedrock's incremental sync scans and indexes the *entire* pending file set in that data source, including files queued by other (losing/retrying) invocations. Those other files get correctly retried and eventually get their own (now largely redundant, since already indexed) job, so nothing is lost or shown incorrectly — but a few extra no-op ingestion jobs can run during a heavy burst. Acceptable for MVP; a future hardening pass could have a winning invocation claim all currently-`UPLOADED` documents for its data source rather than just its own batch.

### Phase 4 exit condition

```text
upload -> async processing -> READY
```

**Met.** Verified end to end against the real deployed pipeline for all six required formats (JPEG, PNG, PDF, MD, audio, video), including citation-quality metadata on retrieval.

---

# Phase 5 — Semantic search

Implemented, deployed, and verified end-to-end against the real Knowledge Base
(2026-09-18/19). `mvn test` (Backend + Infra), `npm test`/`npm run build`/`npm run lint`
(Frontend), and `cdk synth` all pass.

## Deployment

`MemoryLayerIngestionStack` (additive `Outputs` only, exporting `KnowledgeBaseArn`/`Id` for
`ApiStack` to consume) and `MemoryLayerApiStack` (new `/search` route, IAM statement, env var,
new Lambda version) were deployed. `MemoryLayerDataStack` and `MemoryLayerFrontendStack` showed
no `cdk diff` and were not deployed — Amplify redeploys the frontend from the `main` branch push,
not from `cdk deploy`.

**Deployment incident (caused and fixed in this pass):** `cdk deploy MemoryLayerIngestionStack
MemoryLayerApiStack` auto-included `MemoryLayerAuthStack` as a dependency stack. `GOOGLE_OAUTH_CLIENT_ID`
was not set in the deploying shell, so `InfraApp` fell back to its `placeholder-google-client-id`
default, which got deployed to the real Cognito Google identity provider — breaking Google
Sign-In for a few minutes. Caught immediately via `describe-identity-provider`, and fixed by
redeploying `MemoryLayerAuthStack` alone with the correct env var (the real client ID had been
captured in an earlier `cdk diff` in this same session). Confirmed restored. **Lesson for future
deploys of this stack:** `GOOGLE_OAUTH_CLIENT_ID` must be set in the environment before *any*
`cdk deploy`/`cdk diff` that could touch `AuthStack`, including indirectly via dependency
inclusion — verify with `cdk diff MemoryLayerAuthStack` shows no changes before deploying.

**Two real bugs found and fixed via live verification** (not caught by unit tests):
1. **Stale deployed jar** — the first `ApiStack` deploy packaged whatever was already sitting at
   `Backend/target/backend.jar`, built before Phase 5's backend code existed (`SearchController`
   confirmed absent via `unzip -l`). `POST /api/v1/search` 404'd (fell through to Spring's static
   resource handler) despite the API Gateway route and IAM being wired correctly. Fixed by
   `mvn clean package` before every `ApiStack` deploy going forward — CDK's `Code.fromAsset`
   does not rebuild the jar itself.
2. **Wrong IAM action namespace** — `ApiStack` granted `bedrock-agent-runtime:Retrieve` (the
   SDK/client name), but the real required IAM action — confirmed via the live
   `AccessDeniedException`'s exact wording — is `bedrock:Retrieve`. Same category of gotcha as
   Phase 4's `StartIngestionJob`/`GetIngestionJob` IAM fix. Fixed in `ApiStack.java` and
   `ApiStackTest.java`.
3. **Media timestamps silently always null** — `SearchResultMapper.asMillis` used
   `Document.unwrap() instanceof Number`, but verified live (and via `javap` against the real
   SDK jar) that `NumberDocument.unwrap()` always returns the number's `String` form via
   `SdkNumber.stringValue()`, never a `Number`. The code fell through to its `String` branch,
   where `Long.parseLong("6360.0")` throws on the decimal point and was silently swallowed. Real
   Bedrock chunk timestamps are JSON floats (`0.0`, `6360.0`), so every real audio/video result's
   `mediaTimestamp` was dropped. Fixed by switching to `Document`'s typed accessors
   (`isNumber()`/`asNumber().longValue()`, which correctly parses decimal strings via
   `BigDecimal` internally). Added a regression test
   (`resolvesMediaTimestampWhenTheRealKbSendsDecimalFormattedMillis`) using
   `Document.fromNumber(0.0)` — the existing tests only used integer values and never exercised
   this path.

As part of this phase, also built the shared authenticated `AppShell` (resizable/collapsible
sidebar, top bar with global search, Home/Library/Search/Ask navigation) per the approved plan's
first amendment, migrating `/app`, `/app/library`, and `/app/search` into it; `Ask` remains a
placeholder route.

## Backend

- [x] Implement `POST /api/v1/search` — `SearchController`/`SearchService`
- [x] Call Bedrock Knowledge Base `Retrieve` — never `RetrieveAndGenerate`
- [x] Inject `userId = JWT.sub` metadata filter — server-side only, via `AuthenticatedUserResolver`; client-supplied identifiers are ignored, not just unused
- [x] Support optional media-category filter — additive `in`/`equalsValue` clause `AND`ed with the tenant filter, never replacing it
- [x] Convert chunk-level results to document-centric results — oversample (`limit * 3`, capped 50), dedupe to the first (highest-scoring) chunk per `documentId`, `SearchResultMapper.dedupeAndRank`
- [x] Resolve document metadata from DynamoDB — via existing `DocumentRepository`, skipping any result whose document can't be resolved (e.g. deleted after indexing)
- [x] Return snippets — priority order per approved amendment #2: `content.text()` → BDA `audio().transcription()`/`video().summary()` → `x-amz-bedrock-kb-description` metadata → safe generic/file-type fallback (no undocumented SDK fields assumed)
- [x] Return media timestamps when available — defensively checks both `x-amz-bedrock-kb-chunk-start/end-time-in-millis` and `_media_start_time_ms`/`_media_end_time_ms` per approved amendment #3; only set when both start and end resolve
- [x] Map KB staging references back to the original document — primary: `documentId` from Bedrock's returned chunk metadata (written by the Phase 4 ingestion sidecar); fallback: parse the staging S3 key (`kb/multimodal|text/<documentId>/...`) via `DocumentKeys.parseStagingDocumentId`. Live-verified: no `s3://` URI or bucket name appears anywhere in real `/api/v1/search` responses

## Frontend

- [x] Search input — `TopBar`'s global search box, navigates to `/app/search?q=...` only (no duplicated search logic per page)
- [x] Search loading state — `SearchPage`
- [x] Search result cards — `SearchResultCard`
- [x] File type / source display — media category + filename shown on each card
- [x] Relevant snippet — shown per Docs/FRONTEND.md §15
- [x] Open source file — via `/documents/{id}/access-url`, same freshly-signed-URL pattern as `FileCard`. Live-verified: two calls return distinct signatures/expiry, and the returned URL was downloaded directly — 200 OK, correct `image/jpeg` content-type, correct byte size (235574, matching DynamoDB), valid JPEG magic bytes
- [x] Seek to timestamp for media if available — timestamp range rendered as text on the card when present (no scoped media player yet — deferred, no timestamp-linked playback exists in the app currently). Live-verified after the `asMillis` fix: real audio/video results return correct `{startMs, endMs}`
- [x] Raw vector scores never shown to the user (Docs/FRONTEND.md §15) — covered by tests on both `SearchPage` and `SearchResultCard`

## Security

- [x] Confirm client cannot alter tenant filter — `SearchControllerTest` asserts a client-supplied `userId`/`tenantId` in the request body is ignored. **Live-verified**: authenticated as user `61d34d4a...`, sent a request body with `userId`/`tenantId` set to a *different* real user (`d1d3cdda...`) — results stayed scoped to the authenticated user's own 2 documents only, none of the other user's 10 documents leaked
- [x] Confirm User A searches cannot retrieve User B content — **live-verified twice**: (1) directly against the Knowledge Base, filtering the exact query that surfaces user 61d34d4a's JPEG by user d1d3cdda's `userId` instead returns zero occurrences of that document; (2) through the deployed API (see injection test above)

## Cost

- [x] Search path uses `Retrieve`, not `RetrieveAndGenerate`

## Tests

- [x] `SearchResultMapperTest` (15 tests) — dedup/limit/snippet-priority/timestamp-forms/documentId-extraction, incl. a live-bug regression test for decimal-formatted millis
- [x] `SearchControllerTest` (4 tests) — tenant-filter injection, media-category filter, error mapping
- [x] `ApiStackTest` — new `POST /api/v1/search` route and `bedrock:Retrieve` IAM statement
- [x] `SearchPage.test.tsx` (6 tests) — `q`-param-driven search, empty/error states, category-filter re-query, no raw score rendered
- [x] `SearchResultCard.test.tsx` (3 tests) — open-file action, timestamp formatting, no raw score rendered

## Live verification against the real deployed API/Knowledge Base (2026-09-18/19)

All calls made directly against the deployed `memory-layer-api:live` Lambda alias with a
synthetic-but-faithful API Gateway v2.0 JWT-authorizer event (no browser automation tool is
available in this environment — see the browser-refresh note below for what that means for
frontend-only checks).

- [x] JPEG search — query `"Tata Motors corporate entrepreneurship strategy notes"` as the JPEG's owning user: the JPEG (`photo_6106897192711820494_y.jpg`) ranks #1 at score 0.82 with its real BDA-generated snippet ("...Tata Motars can nenew innovat...")
- [x] PDF/Markdown/Audio/Video content search — query `"giraffe umbrella cactus"` (the shared Phase 4 test keyword) correctly surfaces `test-speech.wav` (AUDIO, transcription snippet + `{startMs:0, endMs:6360}`), `test-notes.md` (DOCUMENT, exact text), `test-doc.pdf` (DOCUMENT, exact text), and `test-image.*` (IMAGE, BDA description) — and a `"television test pattern color bars broadcast"` query correctly surfaces `test-video.mp4` (VIDEO, summary snippet + timestamp) at the top 3 results
- [x] Category filters alter results — the same query filtered to `mediaCategories: ["AUDIO"]` returns only the 2 audio documents; filtered to `["DOCUMENT"]` returns only the 5 PDF/MD documents, both excluding the otherwise-present image/video/audio results
- [x] Unrelated query produces a sensible low-relevance result — `"quantum chromodynamics lagrangian renormalization"` returns results scored ~0.50, visibly lower than any on-topic query (~0.53–0.90); there is no hard relevance cutoff configured (a pure-KNN system always returns its k nearest vectors), which is consistent with the approved plan — a minimum-score threshold was never specified as a requirement
- [x] Clicking Open — see the frontend checklist entry above; verified via direct download of a freshly-issued presigned URL
- [x] No staging S3 URI ever exposed — grepped every captured live response for `s3://`/bucket names; none found (one false-positive substring match was the test markdown file's own literal text content mentioning "kb/text/", not an actual leaked path)
- [x] userId/tenantId injection cannot affect isolation — see the Security checklist above
- [~] Search survives a browser refresh at `/app/search?q=...` — verified structurally, not via a driven browser (no browser-automation tool available in this environment): `FrontendStack`'s Amplify `CustomRuleProperty` rewrites any non-asset path to `/index.html` with a 200 (so a hard refresh doesn't 404), and `SearchPage.test.tsx` mounts fresh at that exact URL shape and correctly re-runs the search from the `q` param. Recommend a manual one-time browser check after the next Amplify deploy.
- [x] CloudWatch checked for errors — all `ERROR`-level log lines in `memory-layer-api` during the verification window are attributable to the two bugs above, fixed before final verification; zero errors in either Phase 4 Lambda (`memory-layer-ingestion-coordinator`, `memory-layer-status-reconciler`) during the same window
- [x] No Phase 4 regression — `memory-layer-status-reconciler` confirmed still running once/minute with no errors; the JPEG's DynamoDB `status`/`updatedAt` unchanged since its original Phase 4 ingestion

**Known test-data note (not a Phase 5 defect):** one stray `UPLOAD_PENDING` document
(`2b709b0e-92d2-410f-9e90-c5530ef598e6`, user `d1d3cdda...`) was created while validating the
direct-Lambda-invoke verification technique against the already-working `/api/v1/uploads` route.
It will never progress (no file was actually uploaded to S3 for it) and is harmless, but the
agent's shell session did not have permission to delete it — left for manual cleanup if desired.

### Phase 5 exit condition

Example query:

```text
"that screenshot about AWS hackathon credits"
```

returns the correct uploaded memory.

- [x] Verified against the real deployed API/Knowledge Base — see live verification section above

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
