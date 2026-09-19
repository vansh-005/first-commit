# CLAUDE.md

## Claude Code Operating Instructions

Read `AGENTS.md` first.

Then read `Docs/KNOWLEDGE_TRANSFER.md` — the operational handoff doc. It is
the fast boot sequence for a fresh session and must be read before making
changes.

Then read the relevant files under `Docs/` before making changes:

- `Docs/PRODUCT.md`
- `Docs/ARCHITECTURE.md`
- `Docs/DATA_MODEL.md`
- `Docs/API.md`
- `Docs/TASKS.md`
- `Docs/OPERATIONS.md`

Treat these as the current design contract.

Whenever a change affects deployed architecture, required environment
variables, deployment commands, discovered AWS behavior, known
incidents/gotchas, current phase status, or important operational
procedures, update `Docs/KNOWLEDGE_TRANSFER.md` in the same task, before
reporting the task complete.

---

## Primary Goal

Build the hackathon MVP incrementally.

Do not attempt to generate the entire application in one pass.

Work phase-by-phase from `Docs/TASKS.md`.

When asked to implement a phase:

1. inspect existing code,
2. summarize the current state,
3. propose a short implementation plan,
4. implement only that phase,
5. run relevant validation,
6. update `Docs/TASKS.md`,
7. report remaining blockers.

---

## AWS Environment

Primary application region:

```text
ap-south-1
```

The local AWS CLI is already authenticated.

AWS CDK is already bootstrapped for the account and region.

Use the AWS Agent Toolkit / AWS MCP for:

- inspecting deployed resources,
- checking service state,
- reviewing CloudWatch logs,
- verifying quotas,
- validating AWS-specific assumptions,
- debugging deployment failures.

Prefer current AWS documentation when service behavior is unclear.

---

## Infrastructure Ownership

All application infrastructure must be managed by AWS CDK under:

```text
Infra/
```

Do not create project infrastructure manually unless explicitly requested.

Do not create duplicate resources if a corresponding CDK-managed resource already exists.

Before destructive actions, ask for approval.

Never run destructive commands such as stack deletion or resource removal without explicit user permission.

---

## Cost Awareness

This project is judged partly on AWS architecture and cost efficiency.

Prefer:

- on-demand,
- serverless,
- scale-to-zero,
- managed services,
- no idle infrastructure.

Avoid introducing recurring baseline cost unless measurements justify it.

Current intended design includes:

- API Gateway HTTP API
- Java Lambda + SnapStart
- DynamoDB on-demand
- S3
- SQS
- Bedrock
- S3 Vectors
- Cognito
- Amplify Hosting
- CloudWatch

ECS Express Mode is a fallback only if measured Lambda latency is unacceptable.

---

## Architecture Guardrails

Do not change these without explicitly explaining the tradeoff first:

- user-facing API is Java Lambda + SnapStart,
- uploads go browser -> S3 directly,
- ingestion is asynchronous,
- single and bulk upload share one pipeline,
- S3 -> SQS buffers ingestion events,
- DynamoDB owns application metadata,
- Bedrock KB owns semantic retrieval,
- `/search` uses `Retrieve`,
- `/ask` uses `RetrieveAndGenerate`,
- tenant isolation is server-side using Cognito `sub`.

---

## Authentication Rules

Authentication is through Amazon Cognito.

Supported login paths:

- Google federated login,
- email/password fallback.

The backend never stores passwords.

Canonical identity:

```text
userId = validated JWT `sub`
```

Never authorize using email address alone.

---

## Security Rules

Never expose or commit:

- AWS access key IDs,
- AWS secret keys,
- OAuth client secrets,
- JWTs,
- presigned URLs,
- private file contents.

Never trust client-provided tenant identifiers.

Never let the frontend define:

- S3 keys,
- Knowledge Base IDs,
- model IDs,
- AWS regions,
- tenant filters.

---

## Development Behavior

Prefer editing existing files over replacing whole modules.

Keep implementations minimal and production-sensible.

Do not introduce:

- unnecessary abstractions,
- speculative microservices,
- unnecessary queues,
- duplicate frameworks,
- unused dependencies.

If something can be deferred without weakening the MVP, defer it.

---

## Validation

After backend changes:

```text
compile
run tests
```

After frontend changes:

```text
build
run relevant checks
```

After infrastructure changes:

```text
cdk synth
```

After deployment:

```text
verify GET /api/v1/health
inspect relevant CloudWatch logs if needed
```

Do not report success if validation failed.

---

## Documentation

When architecture, API, or data-model behavior changes, update the corresponding documentation in the same change.

Always update:

```text
Docs/TASKS.md
```

when a task moves from pending to complete or blocked.

---

## Communication Style

For implementation tasks, keep responses operational:

```text
Plan
Changes made
Validation performed
Remaining issues
Next task
```

Do not produce long conceptual explanations unless asked.

When an AWS decision is uncertain, inspect current AWS state/docs instead of guessing.
