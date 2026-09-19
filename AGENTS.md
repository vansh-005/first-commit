# AGENTS.md

## Purpose

This file defines repository-wide rules for any coding agent working on this project.

The project is a multimodal personal memory layer. Users upload files such as PDFs, documents, images, audio, and video, then later browse, semantically search, and ask questions over their personal corpus.

Before making non-trivial changes, read:

- `Docs/PRODUCT.md`
- `Docs/ARCHITECTURE.md`
- `Docs/DATA_MODEL.md`
- `Docs/API.md`
- `Docs/TASKS.md`
- `Docs/OPERATIONS.md` (alarms, deployment procedure, smoke testing, stale-document handling — read before any infra deployment or operational change)

These documents are the current source of truth.

---

## Repository Structure

```text
Amazon/
├── Backend/        # Java backend / Lambda code
├── Frontend/       # React frontend
├── Infra/          # AWS CDK infrastructure
├── Docs/           # Product and engineering specifications
├── AGENTS.md
└── CLAUDE.md
```

Do not introduce a new top-level directory unless there is a clear reason.

---

## Product Rules

The core MVP is:

```text
Authenticate
   ↓
Upload
   ↓
Process / index
   ↓
Browse uploads
   ↓
Semantic search
   ↓
Ask with citations
```

Do not prioritize stretch features before the core flow works.

Stretch features include:

- multipart upload for large files,
- proactive date/expiry reminders,
- duplicate detection,
- deletion,
- advanced previews,
- streaming answers,
- complex analytics,
- collaboration/sharing.

---

## Architecture Rules

Follow `Docs/ARCHITECTURE.md`.

Key decisions currently locked:

- AWS region: `ap-south-1`
- frontend hosting: AWS Amplify Hosting
- authentication: Amazon Cognito
- social login: Google through Cognito federation
- public API: API Gateway HTTP API
- main backend: Java (via Spring Boot) Lambda with SnapStart
- application metadata: DynamoDB on-demand
- user files: Amazon S3
- async ingestion buffer: Amazon SQS + DLQ
- ingestion workers: Lambda
- multimodal parsing: Bedrock Data Automation through Knowledge Bases where supported
- text document parsing: Knowledge Base text document path
- vector store: S3 Vectors
- semantic search: Bedrock Knowledge Base `Retrieve`
- grounded Q&A: `RetrieveAndGenerate`
- observability: CloudWatch
- infrastructure: AWS CDK

Do not add:

- RDS/Aurora,
- Redis/ElastiCache,
- OpenSearch,
- NAT Gateway,
- always-on EC2,
- ECS/Fargate,

unless a measured requirement justifies it and the architecture docs are updated first.

---

## Infrastructure Rules

All persistent AWS infrastructure must be defined in `Infra/` using AWS CDK.

Do not manually create project resources in the AWS Console when CDK can manage them.

Allowed manual actions:

- inspecting resources,
- viewing logs,
- checking quotas,
- validating deployments,
- one-time external OAuth setup where required.

Before creating a resource:

1. inspect existing infrastructure,
2. confirm it does not already exist,
3. keep naming deterministic,
4. keep costs minimal.

After infrastructure changes:

```text
cdk synth
```

must succeed.

Before deployment, inspect the proposed change set when practical.

Deploy only via `Infra/deploy.ps1` (Phase 7) — it rebuilds the Backend jar, runs tests, shows
`cdk diff`, requires explicit confirmation, and always uses `--exclusively`, specifically to
prevent repeats of two real incidents (a stale deployed jar, and a placeholder
`GOOGLE_OAUTH_CLIENT_ID` silently reaching a real deploy via an undeclared dependency-stack
inclusion). See `Docs/OPERATIONS.md` §4 for the full procedure.

Never delete AWS resources without explicit user approval.

---

## Security Rules

These are invariants.

### Identity

```text
userId = Cognito JWT `sub`
```

The backend derives this from the validated token.

Never trust client-supplied:

- `userId`
- `ownerId`
- `tenantId`

for authorization.

### S3

The backend generates S3 keys.

User-owned objects live under:

```text
users/<authenticatedSub>/
```

The client must not choose arbitrary bucket keys.

### Bedrock retrieval

Every retrieval call must inject the authenticated user's metadata filter server-side.

The client must never control the tenant filter.

### Citations and downloads

Before generating a presigned GET URL:

1. load the document from the authenticated user's DynamoDB partition,
2. verify ownership,
3. verify the S3 key belongs to the authenticated user's prefix.

### Secrets

Never:

- hardcode credentials,
- commit AWS access keys,
- commit OAuth client secrets,
- print JWTs,
- log presigned URLs,
- expose secrets in frontend code.

Use environment variables / AWS configuration / secret storage as appropriate.

---

## API Rules

Follow `Docs/API.md`.

Do not invent new public endpoints without checking whether the existing contract already supports the feature.

Core endpoints:

```text
GET    /api/v1/health
POST   /api/v1/uploads
GET    /api/v1/documents
GET    /api/v1/documents/{id}
GET    /api/v1/documents/{id}/access-url
POST   /api/v1/search
POST   /api/v1/ask
```

Single and bulk upload use the same upload endpoint.

Search and Ask remain separate:

```text
/search -> Retrieve
/ask    -> RetrieveAndGenerate
```

Do not invoke an LLM for ordinary semantic search.

---

## Data Rules

Follow `Docs/DATA_MODEL.md`.

Canonical document identity:

```text
documentId = backend-generated UUID
```

Canonical user identity:

```text
userId = Cognito sub
```

Document states:

```text
UPLOAD_PENDING
UPLOADED
INDEXING
READY
FAILED
```

Do not add new states casually.

DynamoDB is the source of truth for:

- file ownership,
- metadata,
- UI listing,
- processing status.

Bedrock Knowledge Base is not the application database.

---

## Coding Rules

Prefer simple, readable implementations over abstractions that are not yet needed.

Do not:

- create framework layers with no current use,
- add distributed patterns without a concrete requirement,
- add queues between synchronous user-facing operations,
- build custom functionality that AWS already provides reliably,
- silently change architecture decisions.

Keep changes focused.

One task should usually produce one coherent set of changes.

---

## Backend Rules

Backend language: Java (Spring Boot).

Prefer AWS SDK v2.

The backend should not proxy uploaded file bytes.

The backend should primarily handle:

- auth-derived identity,
- upload initialization,
- metadata,
- presigned URLs,
- Bedrock retrieval,
- citation resolution.

Long-running file processing must stay asynchronous.

---

## Frontend Rules

The frontend should:

- use Cognito for authentication,
- support Google sign-in,
- upload directly to S3 using presigned URLs,
- show per-file upload and processing state,
- treat API cursors as opaque,
- never construct tenant filters,
- never expose AWS credentials.

Keep the first UX simple and demo-friendly.

---

## Testing Rules

Before considering a task complete:

- compile the modified code,
- run relevant unit tests,
- run integration/smoke checks where practical,
- run `cdk synth` after infrastructure changes,
- verify deployment health if deployment changed.

Do not claim a feature is complete without verification.

---

## Documentation Rules

If implementation changes the contract, update the corresponding document in the same task.

Update:

- product behavior -> `Docs/PRODUCT.md`
- architecture -> `Docs/ARCHITECTURE.md`
- data model -> `Docs/DATA_MODEL.md`
- API contract -> `Docs/API.md`
- implementation progress -> `Docs/TASKS.md`
- frontend -> `Docs/FRONTEND.md`

Do not let code and docs intentionally diverge.

---

## Change Discipline

Before implementing a substantial task:

1. inspect the repository,
2. read the relevant documentation,
3. state the intended change,
4. identify files/services affected,
5. implement only the requested phase,
6. validate,
7. report what changed and what remains.

Do not implement later phases opportunistically unless explicitly requested.
