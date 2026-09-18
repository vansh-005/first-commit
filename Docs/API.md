# API.md

## 1. Purpose

This document defines the public application API for the hackathon MVP.

The API is intentionally small. It supports four product capabilities:

1. initialize direct-to-S3 uploads,
2. browse and open previously uploaded files,
3. semantically search the user's memory,
4. ask questions over the user's memory with grounded citations.

The API does **not** transport file bytes through the Java backend. Uploads go directly from the browser to Amazon S3 using short-lived presigned URLs.

---

# 2. API architecture

```text
React / Amplify
      |
      | HTTPS
      | Authorization: Bearer <Cognito access token>
      v
API Gateway HTTP API
      |
      | JWT validated by Cognito authorizer
      v
Java Lambda API + SnapStart
      |
      +--> DynamoDB
      +--> S3 presigning
      +--> Bedrock Knowledge Base
      +--> CloudWatch
```

Amazon API Gateway performs JWT validation before protected requests reach the backend.

Authentication may originate from:

```text
Google Sign-In
or
Email / Password
        |
        v
Amazon Cognito
        |
        v
Cognito access token
```

The backend treats the Cognito JWT `sub` as the canonical `userId`.

---

# 3. Base URL and versioning

All application routes use:

```text
/api/v1
```

Example deployed URL:

```text
https://<api-id>.execute-api.ap-south-1.amazonaws.com/api/v1
```

The frontend must receive the API origin through environment configuration. It must not hardcode the deployed API Gateway hostname.

---

# 4. Authentication

All endpoints except health checks require:

```http
Authorization: Bearer <Cognito access token>
```

The API Gateway JWT authorizer validates the token before invoking the Java Lambda.

The backend derives:

```text
userId = JWT.sub
```

The following are never accepted from the frontend as trusted authorization inputs:

```text
userId
ownerId
tenantId
S3 object key
S3 bucket
Knowledge Base metadata filter
```

Those values are derived server-side.

---

# 5. Standard headers

## Request

```http
Authorization: Bearer <token>
Content-Type: application/json
```

`Authorization` is omitted only for public health routes.

## Response

```http
Content-Type: application/json
```

API responses should also expose a request identifier for debugging, either in the JSON error object or a response header.

---

# 6. Standard error format

All application-generated errors use:

```json
{
  "error": {
    "code": "DOCUMENT_NOT_FOUND",
    "message": "The requested document does not exist.",
    "requestId": "request-id",
    "retryable": false
  }
}
```

`code` is machine-readable.

`message` is safe for display to the user.

Do not expose:

- stack traces,
- AWS ARNs unnecessarily,
- internal bucket names,
- raw DynamoDB keys,
- credentials,
- Bedrock internal exceptions.

---

# 7. HTTP status conventions

| Status | Meaning |
|---|---|
| `200` | Successful read/action |
| `201` | Resource initialized successfully |
| `204` | Successful action with no body |
| `400` | Invalid request |
| `401` | Missing/invalid authentication |
| `403` | Authenticated but not authorized |
| `404` | Resource not found |
| `409` | Resource state conflict |
| `413` | Upload exceeds configured limit |
| `415` | Unsupported media type |
| `422` | Valid JSON but unsupported operation/content |
| `429` | Rate/quota limit |
| `500` | Internal application failure |
| `502` | Upstream AWS/Bedrock failure |
| `503` | Temporarily unavailable |

---

# 8. Public health endpoint

## `GET /api/v1/health`

Used by:

- smoke tests,
- deployment verification,
- demo readiness checks.

Authentication:

```text
Public
```

Response:

```json
{
  "status": "ok",
  "service": "memory-layer-api",
  "version": "1"
}
```

This route must not query Bedrock or other expensive downstream services.

---

# 8a. Internal diagnostic route (Phase 2, temporary)

## `GET /api/v1/me`

**This is not a permanent product endpoint.** It exists solely to prove the Cognito JWT
authorizer, audience/issuer validation, and the `memory-api/access` custom scope requirement
work end to end, since Phase 2 ships before any real protected business endpoint exists.
Remove or repurpose once Phase 3+ endpoints make it redundant for that purpose.

Authentication:

```text
Required — Bearer access token
```

API Gateway JWT authorizer requirements:

```text
issuer   = the Cognito user pool
audience = the SPA app client ID
scope    = memory-api/access   (enforced by API Gateway via RouteAuthorizationScopes,
                                 not application code — a request without this scope in
                                 its access token's `scope` claim gets 403 before the
                                 Lambda is invoked)
```

Response:

```json
{
  "userId": "<cognito-sub>"
}
```

`userId` is the authenticated user's Cognito `sub`, read from the already-validated JWT
claims API Gateway attaches to the request context.

---

# 9. Upload model

Single-file and bulk uploads use the **same endpoint**.

A single upload is simply:

```text
files.length == 1
```

A bulk upload is:

```text
files.length > 1
```

The backend creates an independent `Document` record and presigned S3 URL for every file.

The API never receives file bytes.

---

# 10. Initialize upload(s)

## `POST /api/v1/uploads`

Authentication:

```text
Required
```

Purpose:

- validate requested files,
- create document records,
- generate server-controlled S3 object keys,
- return short-lived presigned S3 PUT URLs.

### Request

```json
{
  "files": [
    {
      "clientFileId": "browser-local-id-1",
      "fileName": "internship-offer.pdf",
      "contentType": "application/pdf",
      "sizeBytes": 2481934
    }
  ]
}
```

### Fields

#### `clientFileId`

Frontend-generated temporary identifier.

It exists only so the frontend can map an API result back to the corresponding browser file during bulk upload.

It is **not** a persistent application identifier.

#### `fileName`

Original display filename.

The backend sanitizes it before including it in an S3 object key.

#### `contentType`

Browser-reported MIME type.

The backend validates the extension/MIME combination against supported types.

#### `sizeBytes`

Used for pre-upload validation.

Do not trust it as proof of the final uploaded object size; server-side processing can inspect S3 object metadata after upload.

---

## Response

```json
{
  "uploads": [
    {
      "clientFileId": "browser-local-id-1",
      "documentId": "550e8400-e29b-41d4-a716-446655440000",
      "status": "UPLOAD_PENDING",
      "upload": {
        "method": "PUT",
        "url": "https://presigned-s3-url",
        "headers": {
          "Content-Type": "application/pdf"
        },
        "expiresAt": "2026-09-18T04:00:00Z"
      }
    }
  ]
}
```

Recommended presigned URL lifetime:

```text
15 minutes
```

The backend does not return:

```text
bucket
s3Key
userId
```

unless there is a concrete frontend need.

---

# 11. Direct upload to S3

The browser uploads each file directly:

```http
PUT <presigned-url>
Content-Type: application/pdf

<raw file bytes>
```

This request goes to S3, **not** to the application API.

For bulk upload, the frontend may upload several independent files concurrently with a modest client-side concurrency cap.

Recommended initial browser concurrency:

```text
3-5 simultaneous uploads
```

This avoids saturating the browser/network while still providing good UX.

---

# 12. Upload completion

There is intentionally no mandatory:

```text
POST /uploads/{id}/complete
```

endpoint.

S3 `ObjectCreated` events are the authoritative trigger for asynchronous ingestion:

```text
Browser PUT
   |
   v
S3
   |
   v
SQS
   |
   v
Ingestion Coordinator
```

This avoids trusting the client to tell the backend that an upload succeeded.

The document status transitions asynchronously.

---

# 13. File-type routing

Routing is an internal implementation detail and is not exposed in the public API.

The application supports two Knowledge Base ingestion paths within the same logical product:

### Multimodal / BDA path

Used for content such as:

```text
PDF
JPEG / JPG
PNG
MP3
WAV
M4A
FLAC
OGG
AMR
MP4
MOV
```

This path uses Bedrock Data Automation parsing so speech, images, and video can become searchable text representations.

### Text-document path

Used for text-oriented formats such as:

```text
DOC / DOCX
TXT
MD
HTML
CSV
XLS / XLSX
```

This path uses the appropriate Knowledge Base text-document parsing configuration.

The frontend should not need to know which internal parser handles a file.

---

# 14. Multipart uploads

Multipart upload is **not required for the first vertical slice**.

Initial MVP:

```text
presigned PutObject
```

Later, large audio/video files can use:

```text
CreateMultipartUpload
        |
        v
presigned part URLs
        |
        v
parallel UploadPart
        |
        v
CompleteMultipartUpload
```

If/when implemented, multipart routes should live under:

```text
/api/v1/uploads/multipart/*
```

Do not implement these endpoints until the basic upload -> index -> search flow is working.

---

# 15. List documents

## `GET /api/v1/documents`

Authentication:

```text
Required
```

Purpose:

- render the user's file library,
- show upload/processing state,
- power All / Documents / Photos / Video / Audio views.

### Query parameters

```text
limit
cursor
category
status
```

Example:

```http
GET /api/v1/documents?limit=30&category=IMAGE
```

Supported categories:

```text
IMAGE
VIDEO
AUDIO
DOCUMENT
OTHER
```

Supported statuses:

```text
UPLOAD_PENDING
UPLOADED
INDEXING
READY
FAILED
```

`cursor` is an opaque application cursor.

The frontend must not depend on its internal representation.

### Response

```json
{
  "items": [
    {
      "documentId": "550e8400-e29b-41d4-a716-446655440000",
      "fileName": "internship-offer.pdf",
      "mediaCategory": "DOCUMENT",
      "mimeType": "application/pdf",
      "sizeBytes": 2481934,
      "status": "READY",
      "createdAt": "2026-09-18T03:00:00Z",
      "uploadedAt": "2026-09-18T03:00:10Z",
      "updatedAt": "2026-09-18T03:02:30Z",
      "failureReason": null
    }
  ],
  "nextCursor": null
}
```

Raw S3 object keys are not returned.

---

# 16. Get document

## `GET /api/v1/documents/{documentId}`

Authentication:

```text
Required
```

The lookup is always scoped to:

```text
USER#<authenticated JWT sub>
```

Therefore knowing another user's `documentId` is insufficient to access it.

### Response

```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "fileName": "internship-offer.pdf",
  "mediaCategory": "DOCUMENT",
  "mimeType": "application/pdf",
  "sizeBytes": 2481934,
  "status": "READY",
  "createdAt": "2026-09-18T03:00:00Z",
  "uploadedAt": "2026-09-18T03:00:10Z",
  "updatedAt": "2026-09-18T03:02:30Z",
  "failureReason": null
}
```

Use this endpoint for per-file processing-status polling when needed.

---

# 17. Get temporary file URL

## `GET /api/v1/documents/{documentId}/access-url`

Authentication:

```text
Required
```

Purpose:

- open a search result,
- open a citation,
- preview/download a library item.

Flow:

```text
JWT.sub
   |
   v
load document from authenticated user's DynamoDB partition
   |
   v
verify ownership
   |
   v
verify expected S3 prefix
   |
   v
generate short-lived presigned GET URL
```

### Response

```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "url": "https://temporary-presigned-s3-url",
  "expiresAt": "2026-09-18T03:30:00Z"
}
```

Recommended lifetime:

```text
5-15 minutes
```

Never return a permanent public S3 URL.

---

# 18. Semantic search

## `POST /api/v1/search`

Authentication:

```text
Required
```

Purpose:

Find files/content based on what the user remembers.

Examples:

```text
"that screenshot about AWS hackathon credits"

"electricity bill from around August"

"lecture where the professor explained fading"

"internship document mentioning relocation"
```

This endpoint uses Bedrock Knowledge Base `Retrieve`.

It does **not** invoke a generative model.

That keeps ordinary search:

- faster,
- cheaper,
- closer to the core product behavior.

### Request

```json
{
  "query": "that screenshot about AWS hackathon credits",
  "limit": 10,
  "filters": {
    "mediaCategories": ["IMAGE", "DOCUMENT"]
  }
}
```

All user-provided filters are optional.

The API always injects the security filter:

```text
userId == authenticated JWT.sub
```

The frontend cannot override or remove this filter.

Conceptually the final Bedrock filter is:

```text
AND(
    userId == authenticatedUserId,
    optional product filters...
)
```

### Response

The API converts raw Bedrock chunks into **document-centric results**.

Multiple matching chunks from one file should not appear as duplicate files unless there is a deliberate UX reason.

```json
{
  "query": "that screenshot about AWS hackathon credits",
  "results": [
    {
      "document": {
        "documentId": "doc-123",
        "fileName": "Screenshot_20260903.png",
        "mediaCategory": "IMAGE",
        "mimeType": "image/png"
      },
      "match": {
        "score": 0.87,
        "snippet": "AWS promotional credits available...",
        "mediaTimestamp": null
      }
    }
  ]
}
```

`score` is useful for ranking/debugging but should not be presented to users as an absolute confidence percentage.

---

# 19. Audio/video search timestamps

When Bedrock retrieval metadata includes media timing information, the API may expose:

```json
{
  "mediaTimestamp": {
    "startMs": 125000,
    "endMs": 141000
  }
}
```

This allows a later UX such as:

```text
Search result
   |
   v
Open video at 02:05
```

The field is optional.

Clients must work when it is absent.

---

# 20. Ask your memory

## `POST /api/v1/ask`

Authentication:

```text
Required
```

Purpose:

Generate an answer grounded in the user's indexed memories.

This endpoint uses Bedrock Knowledge Base `RetrieveAndGenerate`.

Example:

```text
"What did my internship offer say about relocation?"
```

### First request

```json
{
  "question": "What did my internship offer say about relocation?"
}
```

### Follow-up request

```json
{
  "question": "Was there a repayment condition?",
  "sessionId": "bedrock-session-id-returned-earlier"
}
```

The frontend may only send a `sessionId` that the backend previously returned from Bedrock.

The application does not invent its own Bedrock session IDs.

### Server-side retrieval filter

Every `/ask` request injects:

```text
userId == authenticated JWT.sub
```

before invoking Bedrock.

### Response

```json
{
  "answer": "The document states that ...",
  "sessionId": "bedrock-session-id",
  "citations": [
    {
      "citationId": "c1",
      "documentId": "550e8400-e29b-41d4-a716-446655440000",
      "fileName": "internship-offer.pdf",
      "snippet": "Relevant source excerpt...",
      "accessUrl": "https://temporary-presigned-s3-url",
      "accessUrlExpiresAt": "2026-09-18T03:30:00Z",
      "mediaTimestamp": null
    }
  ]
}
```

Citations are mapped from Bedrock source references to application documents.

Before generating every citation URL, the backend performs the same ownership checks as `/documents/{id}/access-url`.

---

# 21. Search vs Ask

These are deliberately separate APIs.

```text
/search
   |
   v
Bedrock Retrieve
   |
   v
ranked files/chunks
```

Use when the user wants to **find something**.

```text
/ask
   |
   v
Bedrock RetrieveAndGenerate
   |
   v
generated answer + citations
```

Use when the user wants the system to **answer something**.

This separation prevents unnecessary LLM-generation cost for ordinary file retrieval.

---

# 22. No client-controlled model configuration

The frontend does not send:

```text
knowledgeBaseId
modelArn
embeddingModel
temperature
AWS region
S3 bucket
retrieval userId filter
```

These are backend/infrastructure configuration.

This prevents:

- accidental architecture coupling,
- users selecting expensive models,
- tenant-filter bypass,
- exposing implementation details.

---

# 23. Deletion

Deletion is useful but is **not on the critical first vertical slice**.

Target API:

## `DELETE /api/v1/documents/{documentId}`

Authentication:

```text
Required
```

Target behavior:

1. verify ownership,
2. delete original S3 object,
3. delete Knowledge Base metadata sidecar,
4. remove/mark DynamoDB record,
5. ensure the next Knowledge Base sync removes indexed content.

Response:

```http
204 No Content
```

The implementation must not report deletion complete while indexed content remains retrievable indefinitely.

---

# 24. Retry semantics

### Safe client retries

The frontend may retry:

```text
GET /documents
GET /documents/{id}
GET /documents/{id}/access-url
POST /search
```

`POST /ask` should be retried cautiously because a repeated call can incur another generation request.

Upload initialization should not be blindly retried many times because each successful initialization creates a new document record.

A future version may add an `Idempotency-Key` header if unreliable networks make duplicate initialization a real problem.

---

# 25. Rate limiting

The backend should translate downstream quota failures into:

```http
429 Too Many Requests
```

Example:

```json
{
  "error": {
    "code": "RATE_LIMITED",
    "message": "The service is temporarily busy. Please retry shortly.",
    "requestId": "request-id",
    "retryable": true
  }
}
```

The frontend should use exponential backoff rather than aggressive retry loops.

This is especially important for:

- Bedrock Knowledge Base retrieval,
- ingestion orchestration,
- bulk uploads.

---

# 26. CORS

Allowed production origin:

```text
Amplify application origin
```

Allowed development origin:

```text
http://localhost:<frontend-port>
```

Do not use:

```text
Access-Control-Allow-Origin: *
```

for authenticated production APIs unless there is a deliberate reason.

Allowed methods initially:

```text
GET
POST
DELETE
OPTIONS
```

Allowed request headers:

```text
Authorization
Content-Type
```

S3 bucket CORS must separately allow direct browser PUT operations from the frontend origin.

---

# 27. Logging

For every backend request, structured logs should include:

```text
requestId
route
method
statusCode
latencyMs
userIdHash or safe internal user identifier
documentId when relevant
downstreamService
downstreamLatencyMs
```

Do not log:

```text
JWTs
presigned URLs
raw uploaded content
OAuth tokens
AWS credentials
full user questions if privacy-sensitive logging is not required
```

For Bedrock requests, prefer logging metadata such as:

```text
operation=Retrieve
latencyMs=...
resultCount=...
```

rather than full private user content.

---

# 28. API timeout expectations

### Fast control-plane operations

These should normally return quickly:

```text
POST /uploads
GET /documents
GET /documents/{id}
GET /documents/{id}/access-url
```

### Search

`POST /search` is synchronous and waits for Bedrock retrieval.

### Ask

`POST /ask` is synchronous for MVP and waits for retrieval + generation.

File ingestion is **never** performed synchronously inside an HTTP request.

---

# 29. Processing-status UX

After S3 upload completes, the frontend should show:

```text
Uploaded
   |
   v
Processing...
   |
   +--> Ready
   |
   +--> Failed
```

The browser can periodically refresh:

```text
GET /api/v1/documents
```

or:

```text
GET /api/v1/documents/{documentId}
```

Polling should stop when each relevant document reaches:

```text
READY
or
FAILED
```

A WebSocket/SSE status channel is unnecessary for the hackathon MVP.

---

# 30. Security invariants

The following must always be true.

## Authentication

Protected endpoints require a valid Cognito JWT.

## User identity

```text
userId = JWT.sub
```

## Document lookup

Documents are loaded from the authenticated user's partition.

## S3 upload

The backend chooses the exact S3 key before signing.

## S3 download

The backend verifies document ownership before issuing a presigned GET URL.

## Knowledge Base retrieval

The backend injects the authenticated `userId` metadata filter.

## Citations

A raw Bedrock S3 URI is never automatically trusted as authorization.

## Client request bodies

No client-provided identifier may override the authenticated user's identity.

---

# 31. MVP endpoint summary

| Method | Endpoint | Auth | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/health` | No | Health check |
| `POST` | `/api/v1/uploads` | Yes | Initialize single/bulk uploads |
| `GET` | `/api/v1/documents` | Yes | Browse user's library |
| `GET` | `/api/v1/documents/{id}` | Yes | Document metadata/status |
| `GET` | `/api/v1/documents/{id}/access-url` | Yes | Temporary file access |
| `POST` | `/api/v1/search` | Yes | Semantic retrieval |
| `POST` | `/api/v1/ask` | Yes | Grounded Q&A + citations |

Post-MVP:

| Method | Endpoint | Purpose |
|---|---|---|
| `DELETE` | `/api/v1/documents/{id}` | Delete memory |
| multipart routes | `/api/v1/uploads/multipart/*` | Large-media upload |
| streaming ask | TBD | Token-streamed answers |

---

# 32. Implementation order

Agents should implement the API vertically in this order:

```text
1. GET /health

2. Cognito JWT authorization

3. POST /uploads
   + direct S3 PUT

4. GET /documents
   + GET /documents/{id}
   + access URL

5. asynchronous ingestion
   + processing statuses

6. POST /search

7. POST /ask
   + citations

8. delete / multipart / polish
```

Do not start with multipart uploads, deletion, streaming responses, or complex retry infrastructure before the core upload -> index -> retrieve flow works.

---

# 33. Important current platform constraint

The Knowledge Base implementation must be tested against the currently selected parser and vector-store quotas before productionizing the product.

In particular, Bedrock's BDA-parser Knowledge Base path has service quotas that make this architecture suitable for the hackathon MVP but not automatically sufficient for an unlimited production-scale personal drive.

Treat these as infrastructure constraints, not API contract assumptions.

The public API should remain stable if the ingestion implementation is later replaced or sharded.
