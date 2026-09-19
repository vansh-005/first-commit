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

Implemented, deployed, and verified end-to-end against the real Knowledge Base and the real
`RetrieveAndGenerate` model (2026-09-19). `mvn test` (Backend + Infra), `npm test`/`npm run
build`/`npm run lint` (Frontend), and `cdk synth` all pass.

**Post-approval correction:** `AskService` now only treats a Bedrock `ValidationException` as
an expired session when its message matches the exact invalid/expired-session wording verified
during the spike (`"Session with Id ... is not valid"`, matched via a compiled regex). Any
other `ValidationException` on an existing session (e.g. malformed input) surfaces as a normal
`400` and leaves the session mapping untouched. Two regression tests added
(`bedrockRejectingAnExistingSessionAsInvalidExpiresTheMappingRatherThanRetryingTransparently`,
`anUnrelatedValidationExceptionOnAnExistingSessionDoesNotExpireOrDeleteTheSession`).

**Real bug found and fixed via live deployment (not caught by unit tests, IAM-permission
category exactly like Phase 5's `bedrock:Retrieve` namespace fix):** the first live `/ask` call
failed with `AccessDeniedException: Not authorized to call GetInferenceProfile for
arn:aws:bedrock:ap-south-1:<account>:inference-profile/apac.amazon.nova-lite-v1:0`.
`bedrock:RetrieveAndGenerate` alone does not cover resolving/invoking a cross-region inference
profile — `bedrock:GetInferenceProfile`, `bedrock:InvokeModel`, and
`bedrock:InvokeModelWithResponseStream` were added to the same `Resource: "*"` statement (kept
separate from the KB-ARN-scoped `bedrock:Retrieve` statement, per the mandatory amendment).
Fixed, redeployed, re-verified — see live verification results below.

Before implementation, planning-time empirical checks against the already-deployed Knowledge
Base (`MESMX1P9DN`) confirmed: `RetrieveAndGenerateConfiguration`'s retrieval filter is the
identical shape `Retrieve` uses; Anthropic models on this account require a separate, manual
"model use case details" form (observed to fail intermittently even after being filled out),
while Amazon Nova Lite via the APAC cross-region inference profile has no such gate and produced
accurate grounded answers; citation structure (`citations[].generatedResponsePart` +
`retrievedReferences[]`) shares the same `content()`/`location()`/`metadata()` shape as
`Retrieve`'s chunks; default graceful non-hallucination and tenant isolation both hold without
any custom prompt template; an invalid/unknown `sessionId` throws `ValidationException` with a
distinguishable message.

## Backend

- [x] Implement `POST /api/v1/ask` — `AskController`/`AskService`
- [x] Call `RetrieveAndGenerate` — `KnowledgeBaseRetrieveAndGenerateConfiguration`, fixed 8 grounding chunks (not client-configurable, matching the documented request shape)
- [x] Inject authenticated user metadata filter — `RetrievalFilters.forUser`, extracted as the single shared source of truth for the tenant filter used by both `/search` and `/ask` (Phase 6 amendment: do not duplicate this security-critical logic)
- [x] Return generated answer — `response.output().text()`
- [x] Return an application-issued session identifier, never Bedrock's raw one — Phase 6 mandatory amendment. See "Session handling" below
- [x] Accept sessionId for follow-up turns — resolved to the real Bedrock session under the authenticated user's own DynamoDB partition before ever calling Bedrock
- [x] Parse citations — flattens `citations[].retrievedReferences[]`
- [x] Deduplicate citations by `(documentId, startMs, endMs)`, not `documentId` alone — `AskCitationMapper`, preserves separate cited moments of the same audio/video file (Phase 6 amendment)
- [x] Map citations to application documents — reuses `RetrievalContentMapper` (extracted from `SearchResultMapper` during this phase; both `/search` and `/ask` chunk-mapping logic now share one implementation)
- [x] Verify document ownership — `DocumentRepository.findByUserAndDocumentId`, same as `/search`; unresolvable citations are silently skipped, never surfaced as a raw staging reference
- [x] Resolve citation sources through the existing `/documents/{id}/access-url` on click — no presigned URL embedded in the `/ask` response itself (one of the three pre-approved open decisions)

### Session handling (Phase 6 mandatory amendment)

- [x] New ephemeral `AskSession` DynamoDB entity — `PK=USER#<sub>, SK=ASK_SESSION#<applicationSessionId>`, storing only `bedrockSessionId` + TTL, never conversation text (`Docs/DATA_MODEL.md` §15)
- [x] Raw Bedrock session IDs are never exposed to or accepted from the frontend
- [x] A `sessionId` that doesn't resolve under the authenticated user's own partition (foreign, forged, expired, or never existed) throws `AskSessionExpiredException` **before any Bedrock call is made**
- [x] Bedrock rejecting an existing session (`ValidationException`) deletes the mapping and throws the same exception — no transparent contextual retry without history
- [x] `AskSessionExpiredException` → `409 ASK_SESSION_EXPIRED` (`ApiExceptionHandler`)
- [x] Renamed `SearchUnavailableException` → `RetrievalUnavailableException`, shared by `/search` and `/ask` (third pre-approved open decision)

## IAM / Infra

- [x] `bedrock:RetrieveAndGenerate` granted as its **own** statement with `Resource: "*"` — kept separate from the KB-ARN-scoped `bedrock:Retrieve` statement, per current AWS Knowledge Bases IAM documentation (Phase 6 mandatory amendment; the call also invokes the configured model/inference profile, a separate resource from the Knowledge Base itself). Same statement also grants `bedrock:GetInferenceProfile`/`bedrock:InvokeModel`/`bedrock:InvokeModelWithResponseStream` — added after a live `AccessDeniedException` on the first real `/ask` call showed `RetrieveAndGenerate` alone doesn't cover resolving/invoking the cross-region inference profile
- [x] `ASK_MODEL_ARN` env var — Amazon Nova Lite via the APAC cross-region inference profile (`apac.amazon.nova-lite-v1:0`), built from `this.getRegion()`/`this.getAccount()` rather than hardcoded
- [x] New route `POST /api/v1/ask`, same JWT authorizer + `memory-api/access` scope as every other protected route
- [x] No `DataStack` changes — `AskSession` reuses the existing table and its already-configured `expiresAt` TTL attribute

## Frontend

- [x] Ask UI — real `AskPage` inside the existing `AppShell` (nav entry already existed from Phase 5)
- [x] Multi-turn session handling — holds the opaque `sessionId` in component state only; a page refresh starts a new conversation (no persistence), matching `Docs/ARCHITECTURE.md` §8.2
- [x] Answer rendering — Question → Answer → Sources thread (`Docs/FRONTEND.md` §16)
- [x] Citation cards — new `CitationCard` (kept separate from `SearchResultCard` — Search and Ask stay UI-decoupled)
- [x] Click citation -> original source — fetches `/access-url` fresh on click, same pattern as `SearchResultCard`
- [x] Show source snippet
- [x] Show media timestamp when available
- [x] Graceful `ASK_SESSION_EXPIRED` handling — new `ApiError` class (carries HTTP status + `Docs/API.md` §6 error code) lets the frontend distinguish this from other failures; on it, the conversation thread is cleared and a "starting a new one" notice is shown, the typed question is preserved, and the app does **not** silently resend it as if history still existed

## Tests

- [x] `AskCitationMapperTest` (6 tests) — dedup by `(documentId, startMs, endMs)`, preserving separate audio/video moments; flattening multiple references per citation; skipping unresolvable references
- [x] `AskServiceTest` (10 tests, plain Mockito) — first-turn vs. follow-up session flow, foreign/unresolvable `sessionId` never calls Bedrock, the verified invalid/expired-session `ValidationException` message expires the session without retrying, an *unrelated* `ValidationException` on a healthy session does **not** expire or delete it (post-approval regression test), throttling vs. other upstream failures, tenant-filter injection, unresolvable-citation skipping
- [x] `AskControllerTest` (5 tests, MockMvc) — end-to-end happy path incl. asserting no `accessUrl` field is present, validation error, client-supplied `userId`/`tenantId` ignored, `409 ASK_SESSION_EXPIRED`, follow-up session resolution
- [x] `ApiStackTest` — new `POST /api/v1/ask` route and the separate `bedrock:RetrieveAndGenerate`/`Resource: "*"` IAM statement
- [x] `AskPage.test.tsx` (5 tests) — empty state, first-turn call shape, follow-up reuses the returned `sessionId`, session-expiry clears the thread without a transparent retry, generic-error inline message
- [x] `CitationCard.test.tsx` (3 tests) — open-file action, timestamp formatting, no timestamp text when absent
- [x] All existing Phase 5 `SearchResultMapperTest`/`SearchControllerTest` tests still pass unchanged against the refactored shared mapper (signature-preserving delegation, not a behavior change)

## Documentation

- [x] `Docs/DATA_MODEL.md` §15 rewritten for the `AskSession` entity, its access pattern, and the session lifecycle (replacing the superseded raw-Bedrock-passthrough description)
- [x] `Docs/API.md` §20 rewritten: opaque application `sessionId` semantics, citation shape without `accessUrl`, `mediaCategory`/`mimeType` fields added to match the shipped DTO, `409 ASK_SESSION_EXPIRED` documented, fixed 8-chunk retrieval noted as not client-configurable

## Live verification (2026-09-19, against the real deployed API + Knowledge Base)

All calls made directly against the deployed `memory-layer-api:live` Lambda alias with a
synthetic-but-faithful API Gateway v2.0 JWT-authorizer event (same technique Phase 5 used — no
browser-automation tool is available in this environment).

- [x] A real answerable question ("What does the note say Tata Motors can do to renew innovation?") against the JPEG's owning user → grounded answer, single citation, correct `documentId` (`de56b210-...`), matching the exact BDA-derived text
- [x] The same question as a different real user → correctly declined ("the search results do not contain any information related to Tata Motors or innovation"), citations only from that user's own 8 unrelated documents — no leak
- [x] An off-topic question ("boiling point of mercury on Jupiter") against a user with real content → graceful non-hallucinating decline, still cites the (irrelevant) retrieved chunks rather than fabricating an answer
- [x] A follow-up using the returned application `sessionId` ("Who resists these entrepreneurial teams?", no antecedent without context) → correctly resolved from the prior turn, same `sessionId` returned
- [x] A follow-up with a forged/nonexistent `sessionId` → `409 ASK_SESSION_EXPIRED`
- [x] **Cross-user session theft attempt** — a second real user supplied the first user's genuine, currently-valid `sessionId` → `409 ASK_SESSION_EXPIRED`, identical response to the forged case (does not leak whether the session exists for someone else)
- [x] Attempted `userId`/`tenantId`/raw-`bedrockSessionId` injection in the request body → all silently ignored (not fields on `AskRequest`), results stayed correctly scoped to the authenticated user
- [x] No staging S3 URI, bucket name, or `accessUrl` field in any captured response body
- [x] Click a citation → fresh `/access-url` for the JPEG → 200, `image/jpeg`, 235574 bytes (matches DynamoDB) — real file confirmed
- [x] Citation dedup verified live: a query returning 8 grounding chunks across 3 duplicate `test-video.mp4`/`test-speech.wav` uploads produced 8 distinct, correctly-deduped citations, none incorrectly merged (the specific same-document-different-moment case is proven directly by `AskCitationMapperTest`, since the live test corpus has no natural example of one document cited at two different timestamps in one answer)
- [x] `AskSession` DynamoDB items inspected directly: 7 rows created during this verification pass, each scoped to the correct `PK=USER#<sub>`, each with a distinct opaque `applicationSessionId`, its own `bedrockSessionId` (never returned to any client), and a valid ~24h `expiresAt` TTL; the continued session's `updatedAt` correctly refreshed on reuse; no conversation text stored anywhere
- [x] CloudWatch checked for errors: exactly one `ERROR`-level line in the entire verification window, the `GetInferenceProfile` `AccessDeniedException` from the first call before the IAM fix — zero errors after; zero errors in either Phase 4 Lambda (`memory-layer-ingestion-coordinator`, `memory-layer-status-reconciler`) throughout
- [x] No Phase 4/5 regression — the JPEG's DynamoDB `status`/`updatedAt` unchanged since Phase 4/5; `GET /api/v1/health` returns 200; `MemoryLayerAuthStack`'s Google client ID confirmed unchanged post-deploy

### Phase 6 exit condition

```text
ask -> grounded answer -> citation -> source file
```

**Met and live-verified.**

---

# Phase 7 — Reliability and observability

Scope was narrowed by explicit approval to operational resilience and safety — not new product
features. See `Docs/OPERATIONS.md` for the full operational reference this phase produced.

- [x] CloudWatch structured logs for API — `RequestLoggingInterceptor` emits one `api_request`
  JSON line per request (`requestId`, `method`, `path`, `status`, `durationMs`, best-effort
  `userIdHash`); `ApiExceptionHandler` emits `api_error` for every handled exception type, using
  the same `requestId` already returned to the client in the error envelope
- [x] CloudWatch structured logs for ingestion — `IngestionCoordinatorHandler` and
  `StatusReconcilerHandler` emit structured events for staging, job start, job completion,
  job failure, and conflict/error cases (`document_staged`, `ingestion_job_started`,
  `ingestion_job_complete`, `ingestion_job_failed`, `ingestion_job_conflict`,
  `coordinator_message_failed`, `ingestion_job_start_failed`)
- [x] API latency visibility — `durationMs` on every `api_request` structured log line
- [x] Bedrock latency logging — `latencyMs` on `ingestion_job_complete`/`ingestion_job_failed`
  (job-level, computed from the job's `startedAt`); no separate per-Bedrock-call latency metric
  was added beyond this, since `/search` and `/ask` latency is already covered by API latency
  visibility above
- [x] SQS DLQ visibility — `IngestionDlqVisible` CloudWatch alarm (any visible message is
  anomalous), plus `Docs/OPERATIONS.md` §3 inspection/redrive procedure
- [x] Clear frontend retry UX — `withRetry` in `Frontend/src/api/client.ts` transparently
  retries `listDocuments`/`getDocument`/`getAccessUrl`/`searchDocuments` on `429`/`503` with
  exponential backoff (3 attempts, 500ms/1000ms/2000ms), exactly matching `Docs/API.md` §24's
  safe-retry list; `initUploads`/`askQuestion` are deliberately never retried client-side
- [x] Handle `429` — covered by the frontend retry above; a `429` that survives all retries (or
  from `/ask`, which is never retried) still surfaces the existing `RATE_LIMITED` error message
  through the pre-existing generic error display
- [x] Handle Bedrock upstream errors — pre-existing `RetrievalUnavailableException` handling
  (Phases 5/6) covers this; Phase 7 added `503` to the frontend's retryable-status list
  alongside `429` so a transient Bedrock throttle also gets a transparent retry
- [ ] Handle expired presigned URLs — **not addressed this phase.** Out of scope: the approved
  plan focused on server-side operational safety and did not call out presigned URL expiry
  handling as an amendment; still open for a later phase if it proves to matter
- [ ] Handle unsupported file types — **not addressed this phase**, same reasoning as above
- [x] Handle processing failures — new stale-document cleanup job (see below) catches documents
  that fail *silently* (no error, just stuck); documents that fail loudly already transition to
  `FAILED` via the existing Phase 4 error handling
- [x] Demo smoke-test checklist — `Infra/smoke-test.ps1` plus `Docs/OPERATIONS.md` §6

### New in Phase 7 (beyond the original stub above)

- [x] CloudWatch alarms — `Infra/src/main/java/com/memorylayer/infra/AlarmsStack.java`, one new
  CDK stack, one SNS topic (`memory-layer-alarms`) with a single email subscription, 8 alarms
  covering API errors/throttles, coordinator/reconciler errors, EventBridge failed invocations,
  reconciler-not-running (tolerant ~10-minute window), SQS oldest-message age, and DLQ
  occupancy. Method names for every CDK metric/alarm/action call were verified via `javap`
  against the real `aws-cdk-lib` jar before use, including the non-obvious finding that `IRule`
  exposes no metric convenience methods at all. Covered by `AlarmsStackTest` (8 tests, all
  passing) — including a fixed `Match.arrayWith`/`Match.anyValue()` nesting bug found only by
  running the test
- [x] Stale-document handling — `StaleDocumentPolicy` (pure threshold logic, 12 unit tests) +
  `StaleDocumentCleanupHandler` (new Lambda, `rate(15 minutes)` schedule, deliberately separate
  from the Status Reconciler): `UPLOAD_PENDING` past 30 minutes with no S3 object → `FAILED`;
  `UPLOADED` past 2 hours → `FAILED` (auto-fail, per explicit user decision — see
  `Docs/OPERATIONS.md` §8 rationale on the ~100-minute normal SQS backpressure budget);
  `INDEXING` past 1 hour → structured warning only, Bedrock job status remains sole authority.
  Uses a full `DynamoDB` table scan filtered by status (`DocumentRepository.scanByStatus`) —
  documented in `Docs/OPERATIONS.md` §8 "Scale note" as an MVP-scale tradeoff; a new GSI was
  deliberately not added for this low-frequency job per the approved plan
- [x] **Review fix**: stale-`UPLOADED` cleanup now checks the document is not covered by a
  still-running (`STARTING`/`IN_PROGRESS`) ingestion job before auto-failing it —
  `StaleDocumentPolicy.activeJobDocumentRefs`, guarded in `StaleDocumentCleanupHandler.checkUploaded`.
  Without this, a document whose own `INDEXING` status write failed (a real, if rare, race —
  see `IngestionCoordinatorHandler.startJobFor`'s Javadoc) but whose content Bedrock was still
  actively indexing would have been wrongly marked `FAILED`. Regression-tested in the new
  `StaleDocumentCleanupHandlerTest` (4 tests: active-job skip, no-job auto-fail, terminal-job
  auto-fail — a documented separate limitation, see `Docs/OPERATIONS.md` §8 "Known limitation" —
  and the pre-existing `UPLOAD_PENDING` behavior unchanged) plus 3 new
  `StaleDocumentPolicyTest` cases for the set-builder itself
- [x] Deployment safeguards (both requested layers) — `Infra/deploy.ps1` (rebuilds the backend
  jar, runs Backend+Infra tests, shows `cdk diff`, requires typed confirmation, always uses
  `--exclusively`) and a CDK-level fail-fast in `InfraApp.requireEnv` (hard `IllegalStateException`
  for `GOOGLE_OAUTH_CLIENT_ID` or `ALARM_EMAIL` unset — no silent placeholder fallback, live
  -verified to fail synth for each). Both target the exact two real Phase 5 incidents (stale
  jar; placeholder Google client ID silently reaching a real deploy via an undeclared
  dependency-stack inclusion)
- [x] `Docs/OPERATIONS.md` — new document covering alarms, DLQ inspection/redrive, deployment
  procedure, structured logging fields, smoke testing, frontend retry behavior, and
  stale-document handling

### Explicitly out of scope this phase (per approval)

CloudWatch Synthetics, X-Ray, and any new product feature. Presigned-URL-expiry and
unsupported-file-type handling were left open (see above) rather than silently implemented
beyond what the approved plan called for.

### Validation performed

- Backend: `mvn test` — all tests green, including 12 `StaleDocumentPolicyTest` cases, 4 new
  `StaleDocumentCleanupHandlerTest` cases (the review-requested active-job regression coverage),
  5 `StructuredLogTest` cases, and the `DocumentTableSchemaTest` status-attribute-name case
- Infra: `mvn test` — all tests green, including 8 new `AlarmsStackTest` cases
- Infra: `cdk synth` — succeeds with `GOOGLE_OAUTH_CLIENT_ID`/`ALARM_EMAIL` set; confirmed it
  fails fast (before any AWS call) with a clear message when either is unset
- Frontend: `tsc --noEmit`, `vite build`, and `vitest run` (31 tests across 9 files, including 3
  new `client.test.ts` cases for the retry helper) — all green
- `Infra/smoke-test.ps1` — live-tested three times against the real deployed API: happy path
  (200 on `/health`), bad base URL (404, confirms failure path), and a garbage bearer token
  (genuine `401`s from the real API Gateway JWT authorizer on all three authenticated routes)
- `Infra/deploy.ps1` — reviewed only, **not executed** (it performs a real deploy)

### Not yet done (explicit — awaiting review before deployment)

- [ ] Live deployment of `MemoryLayerAlarmsStack`, and the `MemoryLayerIngestionStack`/
  `MemoryLayerApiStack` changes from this phase (new stale-cleanup Lambda; structured
  logging/exception-handler changes) — **intentionally not deployed**, per explicit instruction
  to stop before deployment for review
- [ ] SNS email subscription confirmation (`vansharcade324@gmail.com`) — happens automatically
  on first deploy of `MemoryLayerAlarmsStack`; requires manually clicking the confirmation link
- [ ] Post-deploy live verification: confirm each of the 8 alarms shows `OK` state, confirm the
  stale-cleanup schedule actually fires every 15 minutes, re-run `Infra/smoke-test.ps1` against
  the redeployed API

### Phase 7 exit condition

All code implemented and validated locally (backend, infra, frontend). **Not yet deployed** —
stopping here for review per explicit instruction before any `cdk deploy`.

---

# Phase 8 — UX polish

Frontend/product polish only — no backend, infra, API-contract or AWS changes. The product is
now branded **Recollect** ("Your digital life, remembered."). Implemented and validated locally
(`tsc --noEmit`, `vitest run` — 75 tests / 18 files, `oxlint` — no errors, `vite build`).
Landing/Login visual direction **approved**. Pushed to `main` for Amplify deploy; **Phase 8 is NOT closed** —
awaiting a real-Cognito-login review of Home/Library/Search/Ask with actual data, then one final polish pass.

- [x] Better landing page — nav, hero, product visual built from the real UI, 3 value props,
      privacy statement, final CTA/footer (`pages/LandingPage`, `components/brand/ProductPreview`)
- [x] Clear product tagline — "Your digital life, remembered."
- [x] Login — split-screen on desktop (memory-card illustration pinned to the dark theme + focused
      auth panel), redirect/loading/error feedback, collapses to the auth panel on narrow screens
- [x] AppShell — Recollect wordmark, refined nav states, **global upload dialog** (one upload queue for
      the whole app, works from any page), Cmd/Ctrl+K search focus, avatar user menu with
      Dark/Light/System theme control, accessible mobile drawer (focus trap, Escape, focus return),
      skip link, keyboard-resizable sidebar
- [x] Ask — centered ~860px column, 3 clickable suggested prompts, distinct user/assistant turns,
      sticky composer, answering skeleton, numbered source cards with thumbnails/timestamps/Open,
      autoscroll, Retry, session-expired notice (question preserved, never auto-resent),
      `?q=` prefill from Search's "Ask your memory" bridge
- [x] Home — personalized greeting, large search + example searches, Upload/Ask quick actions,
      Processing section (only when relevant), Recent memories
- [x] Smooth upload progress — real browser->S3 progress bars only; friendly per-file errors;
      a failed `POST /uploads` now fails every file visibly (previously left them "Queued" forever)
- [x] Processing skeletons / Processing -> Ready polling — `useDocuments`: ~5s (15s after 2 min)
      while any document is non-terminal, paused while the tab is hidden, zero calls when settled
- [x] Empty-state UX — shared `EmptyState` (Home, Library incl. per-filter, Search, Ask)
- [x] Search — clickable examples, skeleton results, snippet highlighting (React nodes, never
      innerHTML), segmented category control, result count, no-results -> Ask bridge, no score shown
- [x] File previews where easy — real image thumbnails; designed document / audio / video
      placeholders (`FileThumb`), expired thumbnail URL refetched once before falling back
- [x] Library — slim dropzone + page-wide drop overlay, Upload button, Ready/Processing/Failed
      badges, Load more (opaque cursor), skeletons, retry
- [x] Mobile-friendly layout — responsive grids, drawer nav, sticky composer, sheet-style dialog
- [x] Good error messages — `friendlyError`: never surfaces service names/raw statuses; every
      load/search/ask failure has a Retry
- [x] Unsupported file types — soft, non-blocking "may not be searchable" hint while
      uploading (`isLikelySearchable`); backend behaviour unchanged
- [x] Expired presigned URLs — every open fetches a fresh access URL; failure shows an inline
      message instead of silently doing nothing
- [x] Accessibility — visible focus, labelled inputs, `aria-live` status regions, `aria-pressed`
      filters, modal semantics, `prefers-reduced-motion`, AA-safe muted/accent text tokens,
      per-page document titles
- [x] Demo sample files — `Docs/demo-samples/` (5 realistic Markdown memories + demo script)
- [x] Route-level code splitting — landing no longer pays for the authenticated app
      (main bundle 333 kB; the >500 kB build warning is gone)
### Phase 8 review round 1 (first real-data feedback) — implemented locally, **not committed**

- [x] Search/Ask snippets sanitized for display only (`lib/snippet.ts`): Markdown syntax and BDA
      `<figure>`-style markup stripped, whitespace collapsed, clamped (~280 chars, 3 lines). Stored/indexed
      content and the API response are unchanged.
- [x] Ask decline state: a "couldn't find it" answer still arrives with every retrieved chunk attached,
      so those are shown collapsed as neutral **"Context checked"** instead of numbered **Sources**.
      Verified live against the KB that there is **no structural signal** (same shape as a normal answer:
      no guardrail action, one citation spanning the whole text; only the reference count differs), so this
      is a narrow text heuristic (`lib/askAnswer.ts`) that only *relabels* — never hides — so a wrong guess
      costs a label, not a real source. A deterministic fix (prompt-template marker -> backend `grounded`
      flag) is a Phase 9 backend item.
- [x] Home balance: Processing is a compact capped list (+N more); Recent memories use full-width rows for
      up to 3 items and a card grid beyond that (no lone tile floating in whitespace).
- [x] **Download** added beside Open on Library/Home cards, Search results and Ask sources — same fresh,
      ownership-checked `/access-url` flow, fetched to a Blob and saved under the real filename (S3 CORS
      allows GET from the app; no API change). Files >200 MB fall back to opening in a tab.
- [x] Fixed a class typo from the contrast pass (`text-accent-hover` missing its `hover:` on Home).
- Delete is **explicitly not part of Phase 8** — see Phase 9 (needs coordinated original S3 + KB staging/
  sidecar + DynamoDB + Knowledge Base/vector state).

### Phase 8 correctness round (live-browser findings) — **backend deployed and live-verified (Lambda `live` alias = version 15)**

- [x] UI: double focus ring on search inputs fixed (root cause: the global `*:focus-visible` rule was
      unlayered so it beat Tailwind's `focus:outline-none`; now in `@layer base`, one container
      border+faint-ring treatment); Recollect wordmark links to `/app` (in-app) or `/` (public);
      `FileThumb` is the single type-specific preview (PDF page / document / spreadsheet / audio
      waveform / video frame, size-aware) across Home, Library, Search and Ask.
- [x] **Search relevance measured, not guessed.** Labelled Retrieve queries against the real KB: 45+ clearly
      absent queries topped out at **0.5955**; clear positives 0.74-0.85, natural paraphrases 0.64-0.73,
      vague topical queries ~0.60-0.62. `MIN_RELEVANCE_SCORE = 0.62` (env-overridable), applied per chunk
      **before** dedup. An absent query returns `results: []`.
- [x] **Ask correctness:** preflight `Retrieve` with the same tenant filter + gate; nothing relevant ->
      deterministic "I couldn't find anything in your memories that answers that.", 0 citations, and
      `RetrieveAndGenerate` is not called; otherwise custom generation prompt (private-memories framing,
      answer only from context, no outside advice, exact refusal sentence) with generation restricted to
      the relevant documents via `documentId in [...]` AND-ed after the tenant clause; citations limited to
      gate-passing documents; refusals drop citations.
- [x] AWS placeholder requirements verified (docs + live): `$search_results$` required; `$query$` only for
      Claude v2-and-earlier; `$output_format_instructions$` required for citation references (omitting it
      returned a citation with **zero** references live).
- [x] **Find path:** the model cannot see filenames (verified: with all 8 chunks from
      `NumericalMethods_Assignment1.pdf` it still said "couldn't find" to "do I have numerical methods
      assignment?"), so existence/locate questions are answered from relevant documents' metadata.
- [x] Tests: backend 121 (gate/threshold regression from measured scores, search positive+negative,
      Ask preflight/no-answer/restricted-generation/citation-filter/find/follow-up/isolation); frontend 99.
- [x] **Live verification against the real KB** with the real service classes (opt-in
      `LiveRelevanceVerificationTest`, DynamoDB stubbed, read-only Bedrock): all 7 pass — absent Search -> `[]`
      (both accounts), `numerical methods assignment` -> `NumericalMethods_Assignment1.pdf`, AWS-credit Ask ->
      no-answer/0 citations with generation never invoked, existence question -> the actual file, two known
      content questions -> grounded answers citing only their own document, cross-user Ask/Search -> nothing,
      follow-up without standalone match still answers.
- [x] Deployed `MemoryLayerApiStack` only via `Infra/deploy.ps1` (diff was Lambda code + SnapStart version/alias
      rollover only; no IAM/env/route changes). Version 15 `Active`, SnapStart `On`, `/health` 200.
- [x] **Live-verified against the deployed API** (16/16): absent Search -> `[]` (both accounts); known Search ->
      the expected file; AWS-credit Ask -> deterministic no-answer, 0 citations, `sessionId: null` and no
      AskSession row written (i.e. no generation); existence Ask -> the actual file + citation; known content
      Asks -> grounded, citing only their own document; cross-user Search/Ask -> nothing, and replaying another
      user's real sessionId -> 409; contextual follow-up -> same session, grounded. CloudWatch for the window:
      15 requests, only the deliberate 409, no other errors.
- [x] Frontend pushed to `main` (Amplify build) after the backend passed — **stopped for browser review**
- [x] **Conversational document context (follow-up to browser review):** find -> "explain this assignment" used to
      return the no-answer because the find path stored no conversation. `AskSession` now carries a server-owned
      `contextDocumentIds` (bedrockSessionId nullable); a find creates/updates the session and returns its id; the
      next turn retrieves scoped to `userId AND documentId IN context` (no global gate); the Bedrock session id
      is saved into the same AskSession. Context is re-verified against the user's own documents every turn.
      Global 0.62 threshold unchanged. Backend tests: 155 (find->explain, find->summarize, question-number,
      cross-user/foreign context ignored, fresh conversation still gated, find replaces context, retry rules).
- [x] **Live finding while verifying that:** `RetrieveAndGenerate`'s own retrieval fails for some short deictic
      wordings (0 references) although a scoped `Retrieve` with the identical filter returns the file's chunks;
      5 of 12 wordings failed, one flakily. Measured fixes: relaxed refusal rule in the prompt (10/20 -> 16/20), and
      a structural, generic anchored retry ("Describe the contents of the file. Then: <question>", 16/16); filename
      prefixing and capitalisation/punctuation normalisation did NOT help; a custom orchestration prompt did not
      help. Also handled: Bedrock's canned "Sorry, I am unable to assist you with this request." (treated as a
      refusal), and context-scoped answers with no reference objects (cite the context file).
- [x] Live-verified against the real KB (8/8, real service classes): find -> explain / summarize / question 2 /
      tell me about / please explain all answer from the found file with it cited (repeated runs); a new
      conversation for an account with no assignment is still gated to the no-answer; all earlier cases unchanged.
      **Deployed as Lambda `live` = version 17** (see the next item).
- [x] **API Lambda timeout 10s -> 28s** (ApiStack; HTTP API integration `TimeoutInMillis` made explicit at 30000, a
      test enforces Lambda < gateway). Found while verifying v16: a retrying Ask turn made two generations and hit
      the 10s ceiling (3 of 4 attempts timed out). Also: the FIRST context-only turn (contextDocumentIds non-empty,
      bedrockSessionId null) now sends the anchored wording first, ONCE (state-based, no failed attempt beforehand);
      the recovery retry remains for later turns only.
- [x] **v17 live-verified through the deployed alias:** exact browser sequence (find -> explain -> question 2 ->
      summarize) passes, plus the full regression suite (16/16). Exact per-invocation latency (warmed, log-tail
      REPORT): absent Ask ~0.2s, find ~0.7-1.0s, known-content Ask ~2s (Tata) / ~4-7s (image-page PDF), first
      post-find explain 5.9-8.9s (one generation, always grounded+cited), later turns 4-11s, actual recovery
      retries 6.6-11.4s (5 observed). 51 invocations on v17: 0 timeouts, 0 5xx, max 12.5s.
- [ ] Known limits: a 0.62 gate drops vague topical queries (e.g. "Newton Raphson method", ~0.61); Ask now makes
      one extra `Retrieve` per question (added latency not yet measured in Lambda)

- [ ] File-detail page (`/app/document/:id`) — **deliberately deferred**; still a stub, nothing links to it
- [x] Landing hero verified at 1280x600, 1366x650 (mockup search + answer visible above the fold); Login collage re-laid out with no overlaps
- [x] Pushed to `main` (Amplify auto-build)
- [ ] Real-browser review with real data (Home, Library, Search, Ask), both themes, fresh + incognito, mobile widths
- [ ] Final polish pass from that review

## Phase 8 notes

- UI primitives are hand-written Tailwind (Skeleton/Badge/EmptyState/ErrorState/dialog/menu) —
  no shadcn/Radix dependency was added; no new runtime dependencies at all.
- The Google Fonts stylesheet for Inter is loaded from `index.html` (the only new external request).
- Landing has no dev-only API health badge (removed); public pages make no API calls.
- Local theme preference key: `recollect-theme`. "Memory Layer" remains only in backend/infra
  resource names (unchanged on purpose — renaming AWS resources is out of scope).

---

# Phase 9 — Stretch features

Only start after the MVP works end-to-end.

## Large media

- [ ] Multipart S3 upload
- [ ] Multipart API endpoints
- [ ] Resume/retry failed parts

## Deletion

Deferred from Phase 8 on purpose: correct deletion must coordinate the original S3 object, the KB staging
copy + `.metadata.json` sidecar, the DynamoDB record, and the Knowledge Base/vector state (re-sync), or
deleted content stays retrievable.

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
