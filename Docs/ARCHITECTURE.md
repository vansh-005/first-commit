# Architecture

## 1. Purpose

This project is a **personal multimodal memory layer**. Users upload cluttered digital content such as PDFs, documents, screenshots, images, audio, and video, then later retrieve the right information using natural-language search without remembering filenames or folder locations.

The architecture is optimized for a 4-day AWS hackathon:

- ship a reliable working vertical slice quickly;
- use managed AWS services where they remove undifferentiated infrastructure work;
- keep idle cost close to zero;
- make tenant isolation explicit;
- keep the design simple enough that the team can explain every component in the demo.

Primary AWS Region: **`ap-south-1` (Mumbai)**.

---

## 2. Architecture diagrams

### High-level AWS architecture

Lucid view:
https://lucid.app/lucidchart/cde6a55f-5063-4c6d-8634-0bd727c79624/view

Lucid edit:
https://lucid.app/lucidchart/cde6a55f-5063-4c6d-8634-0bd727c79624/edit

### Upload and ingestion sequence — final

Lucid view:
https://lucid.app/lucidchart/2f805ab4-74f8-43ec-91b6-5c8340b5b2b5/view

Lucid edit:
https://lucid.app/lucidchart/2f805ab4-74f8-43ec-91b6-5c8340b5b2b5/edit

### Semantic search and ask sequence

Lucid view:
https://lucid.app/lucidchart/f6e50938-afee-4377-b7af-7368dc1a9c6e/view

Lucid edit:
https://lucid.app/lucidchart/f6e50938-afee-4377-b7af-7368dc1a9c6e/edit

---

## 3. High-level architecture

```text
User / Browser
      |
      v
AWS Amplify Hosting (React)
      |
      +------> Amazon Cognito
      |          authentication
      |
      v
Amazon API Gateway - HTTP API
      |
      v
Java API Lambda + SnapStart
      |
      +------> DynamoDB        document/application metadata
      |
      +------> Amazon S3       original user files + KB metadata sidecars
      |
      +------> Bedrock KB      semantic search / grounded Q&A

Async ingestion:

Amazon S3
   |
   v
Amazon SQS
   |
   v
Ingestion Coordinator Lambda
   |
   v
Bedrock Knowledge Base ingestion
   |
   +--> Bedrock Data Automation parser
   |
   +--> text chunking + embeddings
   |
   v
Amazon S3 Vectors

Status reconciliation:

EventBridge scheduled rule
   |
   v
Status Reconciler Lambda
   |
   +--> GetIngestionJob
   +--> DynamoDB status updates
```

CloudWatch is used for Lambda logs, API/ingestion diagnostics, and basic operational visibility.

---

## 4. Core service choices

| Concern | AWS service | Decision |
|---|---|---|
| Frontend | AWS Amplify Hosting | Host React/Vite app and produce a public URL quickly. |
| Authentication | Amazon Cognito | Managed signup/login and JWTs. |
| API edge | API Gateway HTTP API | Lower-complexity request routing and JWT authorization. |
| User-facing backend | AWS Lambda, Java 21, SnapStart | Scales to zero while reducing Java cold-start latency. |
| Metadata | DynamoDB on-demand | Serverless application state with no capacity planning. |
| File storage | Amazon S3 | Durable object storage; backend never proxies file bytes. |
| Async buffer | Amazon SQS + DLQ | Absorb bursty single/bulk uploads and retry ingestion safely. |
| Multimodal understanding | Bedrock Knowledge Bases + Bedrock Data Automation parser | Managed parsing of PDFs/images/audio/video into searchable representations. |
| Embeddings | Titan Text Embeddings V2 initially | Unified text-semantic retrieval after BDA conversion. |
| Vector store | Amazon S3 Vectors | Serverless vector storage; avoids an always-running vector database. |
| Answer generation | Bedrock model, configurable | Used only by `/ask`; default should be a cost-efficient model available from Mumbai/APAC. |
| Monitoring | Amazon CloudWatch | Logs and basic metrics. |
| Ingestion status | EventBridge + small status Lambda | Avoid waiting/polling inside the ingestion Lambda. |
| Infrastructure | AWS CDK | All project infrastructure is defined as code. |

---

## 5. Authentication and tenant boundary

Amazon Cognito is the source of user identity.

After API Gateway validates the Cognito JWT, the backend extracts the Cognito **`sub`** claim and uses it as the canonical `userId`.

The client must never be trusted to provide an authoritative `userId`.

This affects every path:

- DynamoDB queries are scoped by authenticated `userId`.
- S3 object keys are created by the backend under the authenticated user's prefix.
- Knowledge Base metadata includes `userId`.
- Every Bedrock retrieval call receives a **server-created metadata filter** for that authenticated `userId`.
- Citation S3 URIs are validated again before the backend creates a presigned download URL.

Authentication uses Amazon Cognito User Pools with Google as a federated social identity provider and native email/password as a fallback. The frontend redirects users through Cognito managed login. Regardless of authentication method, Cognito issues the application JWT, and API Gateway validates it using a JWT authorizer. The backend derives the canonical userId exclusively from the Cognito sub claim.

Recommended S3 key layout:

```text
users/{userId}/{documentId}/{originalFileName}
users/{userId}/{documentId}/{originalFileName}.metadata.json
```

The second file is the Bedrock Knowledge Base metadata sidecar. It is written **before** the client uploads the source file so tenant metadata is present whenever the next ingestion job sees the object.

Example sidecar conceptually:

```json
{
  "metadataAttributes": {
    "userId": "<cognito-sub>",
    "documentId": "<document-id>",
    "mediaType": "application/pdf"
  }
}
```

`userId` is intended for filtering, not for semantic meaning, so it should not be deliberately included in the embedding text.

---

## 6. Upload path

### 6.1 Single and bulk upload use the same architecture

There is no separate backend architecture for bulk uploads. A bulk upload is simply multiple files going through the same presigned-upload flow.

Suggested API:

```text
POST /uploads/presign
```

The request may contain one file or an array of files. The API returns one `documentId` and one presigned S3 upload URL per file.

### 6.2 Upload sequence

1. User selects one or more files.
2. React calls `POST /uploads/presign` with filename, MIME type, and declared size.
3. Backend creates DynamoDB document records with `UPLOAD_PENDING`.
4. Backend allocates S3 keys under the authenticated user's prefix.
5. Backend writes the Knowledge Base `.metadata.json` sidecar for each source file.
6. Backend returns short-lived presigned S3 PUT URLs.
7. Browser uploads bytes **directly to S3**.
8. The Java API never buffers or proxies the uploaded file.

This keeps Lambda/API Gateway out of the large-file data path.

### 6.3 Multipart upload

Multipart upload is **not required for the first vertical slice**.

MVP:

```text
ordinary file -> single presigned S3 PUT
```

Extension:

```text
large media (roughly >100 MB) -> S3 multipart upload
```

The eventual multipart flow will be:

1. initiate multipart upload;
2. return presigned URLs for parts;
3. upload parts in parallel from the browser;
4. retry failed parts independently;
5. complete the multipart upload.

Do not block MVP delivery on multipart support.

---

## 7. Async ingestion pipeline

### 7.1 Why SQS is used here

SQS is used **only where the workload is asynchronous and bursty**.

Users may upload one file or dozens at once. S3 events can therefore arrive as a burst while Bedrock ingestion is comparatively heavyweight. SQS provides:

- buffering;
- retries;
- backpressure;
- a DLQ for poison/failing messages;
- the same pipeline for both single and bulk uploads.

SQS is intentionally **not** inserted into synchronous search/API calls.

### 7.2 S3 to SQS

S3 `ObjectCreated` notifications are sent to the ingestion queue, **filtered to the `users/` prefix only**. Knowledge Base staging objects and their `.metadata.json` sidecars live under `kb/` (see §7.3.1) and never produce their own S3 events into this queue, so there is no need to filter out `.metadata.json` keys at the consumer.

### 7.3 Ingestion Coordinator Lambda

The SQS event source mapping should use a small batching window so several uploads can be coalesced.

Initial tuning:

- small SQS batch size (10);
- short batch window (10s);
- reserved concurrency `1` initially;
- a **generous** retry budget before DLQ (see §7.3.2) — not a small one.

The coordinator:

1. validates source-object events, parsing `userId`/`documentId` back out of the original upload key;
2. marks affected documents `UPLOADED`;
3. routes/copies each source object into its Knowledge Base staging prefix and writes its metadata sidecar there (§7.3.1);
4. marks affected documents `INDEXING`;
5. starts an incremental Knowledge Base ingestion job for the affected data source;
6. stores the returned `ingestionJobId` and associated document IDs.

#### 7.3.1 Knowledge Base staging prefixes

Two Knowledge Base data sources exist, but they never scan the same `users/` prefix a client uploads into. Instead, the coordinator copies each source object into one of two staging prefixes within the same uploads bucket, and writes its `.metadata.json` sidecar beside the staged copy — not beside the original:

```text
users/<userId>/documents/<documentId>/original/<fileName>      <- original upload (untouched)
kb/multimodal/<documentId>/<fileName>                          <- BDA data source watches this
kb/multimodal/<documentId>/<fileName>.metadata.json
kb/text/<documentId>/<fileName>                                <- text data source watches this
kb/text/<documentId>/<fileName>.metadata.json
```

Routing is by file extension, per §13 of `Docs/API.md`'s multimodal/text lists. This avoids two data sources double-scanning (or ambiguously splitting) the same `users/` prefix, and keeps the S3 event notification's `users/`-only filter sufficient to prevent staging writes from re-entering the pipeline.

#### 7.3.2 Bedrock's single-concurrent-ingestion-job constraint

**A Bedrock Knowledge Base allows only one ingestion job running at a time, across every data source it has** — not one-per-data-source. `StartIngestionJob` returns `ConflictException` ("You have reached the maximum number of concurrent ingestion jobs per knowledge base: 1") whenever a job is already running, whether that job belongs to the multimodal or the text data source. This was confirmed directly against a real Knowledge Base during the Phase 4 compatibility spike, not assumed from documentation.

This means a burst of uploads — even uploads of the same type — routinely queues up behind a single active job. That is **expected backpressure, not a poison message**, and the ingestion queue's retry configuration is sized around it rather than around a small fixed number of "real" failure retries:

```text
visibilityTimeout: 5 minutes
maxReceiveCount:   20
-> roughly 100 minutes of retry budget before a healthy, merely-waiting upload could reach the DLQ
```

When the coordinator receives `ConflictException`, it leaves the affected documents at `UPLOADED` (not `FAILED`) and reports those SQS messages as batch item failures so they retry later via the queue's own visibility timeout. If an ingestion job is already running, the Lambda **must not acknowledge and lose new work** — the messages remain/retry through SQS after the active job finishes, and a subsequent incremental sync then picks up the newly staged objects.

Use an idempotency token when starting jobs (a fresh UUID per attempt is sufficient — retried `StartIngestionJob` calls are naturally idempotent in effect, since each incremental sync re-scans the data source's current state).

### 7.4 Managed multimodal parsing

The Knowledge Base S3 data source is configured to use **Bedrock Data Automation as its multimodal parser**.

Therefore the application does **not** manually run:

```text
custom BDA Lambda -> BDA output bucket -> processing Lambda -> staging bucket
```

Instead:

```text
S3 source object
    -> Knowledge Base ingestion
    -> BDA parsing
    -> searchable text representation
    -> chunking
    -> embedding
    -> S3 Vectors
```

BDA is responsible for extracting/search-enabling content from supported PDFs, images, audio, and video.

The architecture deliberately chooses the **text-conversion retrieval path** rather than direct native multimodal similarity for MVP. The product's primary need is semantic memory retrieval across modalities, not image-to-image or audio-to-audio similarity.

#### 7.4.1 Multimodal storage destination (supplemental data storage)

BDA/multimodal parsing requires the Knowledge Base to declare a **multimodal storage destination** (`supplementalDataStorageConfiguration`), confirmed the hard way during the Phase 4 compatibility spike:

- It is **required** the moment any data source uses `BEDROCK_DATA_AUTOMATION` parsing — creating a data source with that parser against a KB that lacks this field fails.
- It is **immutable once the Knowledge Base is created** — there is no update path; changing it requires destroying and recreating the KB (losing its ID and all indexed content).
- Its S3 URI **must be a bucket root** (`s3://bucket-name`), not a sub-prefix of an existing bucket — a prefix is rejected at KB-creation time.
- Consequently it lives in its **own dedicated bucket**, separate from the uploads bucket, created and wired up front in the same CDK deploy that creates the Knowledge Base.
- The KB's IAM role needs `s3:GetObject`, `s3:PutObject`, **and `s3:DeleteObject`** on that bucket — omitting `DeleteObject` fails KB creation with a misleading "write access" validation error rather than a clear permissions error.

#### 7.4.2 BDA parsing modality and cross-region invocation (confirmed at real deployment)

Two more BDA constraints only surfaced once real (non-degenerate) test files were run through a deployed pipeline, not the spike's manual CLI testing:

- The data source's `parsingConfiguration.bedrockDataAutomationConfiguration.parsingModality` field **must be explicitly set to `MULTIMODAL`**. Left unset, BDA silently runs in a text/document-only mode: PDFs still work, but every JPEG, PNG, WAV, and MP4 is rejected as "file format was not supported" — indistinguishable from a genuinely unsupported format unless you know to check this field.
- BDA's actual invocation profile for a Knowledge Base in `ap-south-1` is **cross-region** — it invokes through an APAC profile hosted in `ap-northeast-1` under the same account (`arn:aws:bedrock:ap-northeast-1:<account-id>:data-automation-profile/apac.data-automation-v1`), not the same-region AWS-owned profile shown in AWS's own documented example policy. The KB role's `bedrock:InvokeDataAutomationAsync`/`bedrock:GetDataAutomationStatus` permissions must cover this region, not just the KB's own region.

### 7.5 Ingestion status

Do not keep a Lambda running while a potentially long ingestion job completes.

The ingestion coordinator stores active `ingestionJobId`s in DynamoDB and returns.

A lightweight EventBridge schedule invokes a Status Reconciler Lambda approximately once per minute:

1. read active ingestion jobs;
2. call `GetIngestionJob`;
3. if still running, do nothing;
4. on success, mark associated documents `READY`;
5. on failure, mark affected documents `FAILED` and keep diagnostics.

This avoids paying for an idle Lambda polling loop.

For a later hardening pass, Bedrock Knowledge Base resource-level CloudWatch ingestion logs can be used for more precise per-document progress.

---

## 8. Query architecture

The product exposes two logically different capabilities because they have different cost profiles.

### 8.1 Semantic search — `/search`

Use Bedrock Knowledge Base `Retrieve`.

```text
POST /search
```

Flow:

1. API Gateway validates Cognito JWT.
2. Lambda extracts `sub` as `userId`.
3. Lambda constructs metadata filter: `userId == authenticatedSub`.
4. Lambda calls `Retrieve`.
5. Knowledge Base performs vector search in S3 Vectors.
6. Backend returns ranked files/chunks and source metadata.

No generation model is invoked.

This is the default path for requests such as:

> "Find the screenshot where I saved the AWS credits information."

### 8.2 Ask your memory — `/ask`

Use Bedrock Knowledge Base `RetrieveAndGenerate`.

```text
POST /ask
```

Flow:

1. same Cognito-derived tenant filter as `/search`;
2. retrieve user-owned grounding chunks;
3. invoke the configured Bedrock generation model;
4. return answer + citations;
5. reuse the Bedrock-generated `sessionId` for follow-up turns in the same conversation.

Do not assume a fixed session lifetime in application logic. If a session is no longer accepted, begin a new conversation.

Generation is deliberately separate from semantic search so ordinary file retrieval does not incur unnecessary LLM token cost.

---

## 9. Citation and source handling

Bedrock can return S3 source references. Those URIs must never be returned to the browser as raw privileged access.

For each citation:

1. backend reads the returned S3 object key;
2. verify that the key belongs to the authenticated user's prefix and/or matches a DynamoDB record owned by that user;
3. generate a short-lived presigned GET URL;
4. return the URL and display metadata to the frontend.

This creates defense-in-depth even if an upstream metadata filter is ever misconfigured.

For audio and video retrieval, preserve Bedrock's returned chunk start/end timestamps so the frontend can eventually jump directly to the relevant moment in the original media file.

---

## 10. Browse and rendering path

The file library does **not** query Bedrock just to show uploads.

```text
GET /documents
```

reads DynamoDB and returns application metadata such as:

- `documentId`;
- original filename;
- MIME/media type;
- upload time;
- file size;
- processing status;
- optional thumbnail/preview information later.

The React frontend groups items into views such as:

- All;
- Photos;
- Documents;
- Audio;
- Videos.

Grouping is based on MIME/media type, not an LLM call.

When the UI needs to open an original object, the backend verifies ownership and returns a short-lived S3 presigned GET URL.

---

## 11. DynamoDB responsibility

DynamoDB is the **application state database**. The Bedrock Knowledge Base is not used as the source of truth for the user's upload library.

Minimum document lifecycle:

```text
UPLOAD_PENDING
      |
      v
INDEXING
  |       |
  v       v
READY   FAILED
```

Minimum document metadata:

```text
documentId
userId
originalFileName
s3Key
mimeType
sizeBytes
uploadedAt
status
activeIngestionJobId?   // when applicable
```

The exact table/index schema belongs in `Docs/DATA_MODEL.md`.

---

## 12. Backend compute decision: Lambda vs ECS

### Selected: API Gateway + Java Lambda + SnapStart

The main user-facing backend uses Java Lambda with SnapStart rather than an always-running ECS/Fargate service.

Reasons:

- expected hackathon/demo traffic is intermittent;
- Lambda has no always-on application compute;
- Java SnapStart specifically targets JVM initialization latency;
- API Gateway + Lambda is easy to reproduce with CDK;
- no ALB is required;
- the architecture remains almost entirely pay-per-use.

Use a published Lambda version/alias because SnapStart works on published versions rather than `$LATEST`.

### Fallback: ECS Express Mode

ECS Express Mode remains a documented fallback if measured latency is unacceptable after deployment.

Do **not** move to ECS preemptively. First deploy and benchmark the Lambda API.

If Lambda + SnapStart is still too slow for the user-facing flow, the Java API can be moved behind ECS Express Mode while S3, DynamoDB, Cognito, SQS, Bedrock, and the ingestion architecture remain unchanged.

---

## 13. Security decisions

MVP security requirements:

- S3 Block Public Access enabled.
- Files accessed only through authenticated API flows and presigned URLs.
- Short presigned URL lifetimes.
- API Gateway Cognito JWT authorization.
- Cognito `sub` is the canonical user ID.
- No client-controlled tenant identifier is trusted.
- Knowledge Base retrieval always receives a server-created `userId` filter.
- Citation ownership is revalidated before presigning.
- Lambda roles use least privilege.
- Secrets/credentials are never committed to Git.
- Deployed workloads use IAM roles, not the local `hackit` developer access key.
- CORS restricted to the deployed frontend origin once the Amplify URL is known.

No VPC is required for the MVP because all selected dependencies expose managed AWS APIs. Avoiding a VPC also avoids unnecessary NAT Gateway complexity and baseline cost.

---

## 14. Reliability decisions

### Uploads

- Browser uploads directly to S3.
- Large payloads never pass through Lambda/API Gateway.
- SQS absorbs bursts and retries ingestion work.
- DLQ captures repeatedly failing ingestion events.

### Ingestion

- SQS consumer uses partial batch failure/retry behavior rather than silently dropping failed messages.
- Do not start overlapping Knowledge Base ingestion jobs — Bedrock enforces this KB-wide (one job at a time across every data source), not per data source; see §7.3.2.
- New upload events arriving during an active ingestion job remain retryable and cause a later incremental sync.
- Status polling is out-of-band through EventBridge rather than blocking a Lambda.

### Queries

- User requests fail cleanly if Bedrock is temporarily unavailable.
- `/search` and `/ask` have separate timeout/error handling because `/ask` includes model generation.

### Operational safety (Phase 7)

- CloudWatch alarms exist for API/ingestion Lambda errors, API throttles, EventBridge failed
  invocations, the reconciler failing to run at all (tolerant ~10-minute window), the ingestion
  queue's oldest-message age, and any DLQ occupancy — all notifying one SNS topic. See
  `Docs/OPERATIONS.md` §2 for exact thresholds and rationale.
- A document stuck outside Bedrock's own tracking (`UPLOAD_PENDING`/`UPLOADED` with no
  progress) is caught by a dedicated, separately-scheduled stale-document cleanup job — never
  by the Status Reconciler, which remains the sole authority for `INDEXING` documents. See
  `Docs/OPERATIONS.md` §8.
- Deployment safeguards (a canonical `Infra/deploy.ps1` wrapper, and a CDK-level fail-fast
  check with no silent placeholder fallback for required configuration) exist specifically
  because two real incidents happened without them — a stale deployed jar, and a placeholder
  Google OAuth client ID silently reaching a real deploy. See `Docs/OPERATIONS.md` §4.
- The frontend transparently retries `GET /documents`, `GET /documents/{id}`,
  `GET /documents/{id}/access-url`, and `POST /search` with exponential backoff on a 429/503
  response — all idempotent reads. `POST /uploads` and `POST /ask` are never retried
  client-side, since replaying either could create a duplicate side effect (a second Bedrock
  session for `/ask`).

---

## 15. Cost strategy

The architecture intentionally avoids services with an idle infrastructure floor unless they become necessary.

### Low/usage-based components

- Amplify Hosting;
- Cognito;
- API Gateway HTTP API;
- Lambda;
- DynamoDB on-demand;
- S3;
- SQS;
- EventBridge;
- CloudWatch;
- S3 Vectors.

### Main variable-cost components

1. **Bedrock Data Automation** during ingestion.
2. **Embedding generation** during Knowledge Base ingestion.
3. **Bedrock generation model** for `/ask`.

Cost controls:

- `/search` uses `Retrieve` only and avoids generation tokens.
- `/ask` uses a cost-efficient configurable model.
- BDA runs during ingestion rather than on every query.
- S3 Vectors is chosen instead of an always-running OpenSearch/Aurora vector cluster.
- DynamoDB uses on-demand billing.
- No ECS/ALB in the initial design.
- No NAT Gateway.
- No Redis/ElastiCache.
- No RDS.
- No CloudFront until traffic/performance justifies it.

At hackathon scale, ingestion and model inference should dominate the application cost, which makes the cost story easy to explain to judges.

---

## 16. Deliberately deferred features

The following are explicitly **not MVP blockers**:

### Duplicate detection

Deferred. Byte-hash deduplication does not materially improve the core 3-minute demo.

### Multipart upload

Architecture supports adding it later for large audio/video, but MVP uses ordinary presigned PUT uploads.

### Near-duplicate detection

Deferred.

### Proactive date/expiry reminders

Stretch feature only. It can later turn the memory layer from reactive retrieval into proactive resurfacing.

### Bedrock Guardrails contextual grounding

Potential hardening step after the core retrieval/citation flow works. Do not block the MVP on it.

### Native multimodal similarity

MVP uses BDA -> searchable text -> text embeddings. Direct image/audio similarity can be evaluated later if it improves the product.

### CloudFront/CDN for private source files

Not needed for hackathon traffic. Use presigned S3 GET URLs first.

---

## 17. Infrastructure as Code

All infrastructure is defined in `Infra/` using AWS CDK.

Rules:

- Do not manually create project infrastructure in the AWS Console when CDK can own it.
- `cdk synth` must succeed before deployment.
- Review the diff before destructive changes.
- Primary deployment Region is `ap-south-1`.
- Resource names/tags should use a project-specific prefix.
- Destructive resources should use safe removal policies during development where practical.

The already-created account-level CDK bootstrap resources are not application resources and should remain separate from this stack.

---

## 18. MVP deployment order

Implement vertically in this order:

1. Amplify frontend URL.
2. Cognito signup/login.
3. API Gateway -> Java Lambda `/health`.
4. DynamoDB document metadata.
5. Presigned S3 upload for one file.
6. Upload library (`GET /documents`).
7. SQS ingestion pipeline.
8. Knowledge Base + BDA parser + S3 Vectors.
9. `/search` using `Retrieve` + tenant metadata filter.
10. clickable source citations.
11. `/ask` using `RetrieveAndGenerate`.
12. bulk upload UX using the same upload pipeline.
13. multipart upload only if time remains and large-media demo requires it.

The system should remain deployable and demonstrable after every major step.

---

## 19. Architectural summary

The central boundary is intentionally simple:

> **Our application owns identity, upload state, object ownership, UI, and orchestration. Bedrock owns multimodal parsing, chunking, embeddings, retrieval, and grounded generation.**

The design therefore emphasizes managed AWS capabilities where they provide the largest leverage while keeping the application's security-critical responsibilities — especially tenant isolation and source authorization — explicit in our own backend.
