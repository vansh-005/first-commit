# OPERATIONS.md

## 1. Purpose

This document covers running and operating the deployed Memory Layer system: alarms, DLQ
inspection/redrive, the deployment procedure, smoke testing, and stale-document handling.

It does not cover product/API behavior (`Docs/API.md`), the data
model (`Docs/DATA_MODEL.md`), or architecture decisions (`Docs/ARCHITECTURE.md`) — only how to
keep the deployed system healthy and how to deploy it safely.

---

## 2. Alarms

`Infra/src/main/java/com/memorylayer/infra/AlarmsStack.java` defines every alarm. All of them
notify one SNS topic (`memory-layer-alarms`) with a single email subscription — deliberately
simple for MVP scale.

| Alarm | Condition | Why |
|---|---|---|
| `ApiErrors` | API Lambda `Errors` >= 3 in 5 minutes | Real user-facing failures |
| `ApiThrottles` | API Lambda `Throttles` >= 1 in 5 minutes | Concurrency limit being hit |
| `CoordinatorErrors` | Ingestion Coordinator `Errors` >= 1 | Ingestion pipeline broken |
| `ReconcilerErrors` | Status Reconciler `Errors` >= 1 | Status polling broken |
| `ReconcilerScheduleFailedInvocations` | EventBridge `FailedInvocations` >= 1 for the reconciler's rule | EventBridge itself failing to invoke, not a Lambda-side error |
| `ReconcilerNotRunning` | Reconciler `Invocations` < 1 over a 10-minute window | Tolerant of one missed minute — the schedule is `rate(1 minute)`, so ~10 invocations are expected in any 10-minute window; only alarms if the whole window shows zero |
| `IngestionQueueOldestMessage` | Ingestion queue `ApproximateAgeOfOldestMessage` > 35 minutes | Comfortably below the ~100-minute normal SQS backpressure budget (`Docs/ARCHITECTURE.md` §7.3.2), so it fires well before a healthy-but-waiting message could reach the DLQ |
| `IngestionDlqVisible` | DLQ `ApproximateNumberOfMessagesVisible` > 0 | Any DLQ occupancy is anomalous — Phase 4 verification confirmed the DLQ stays empty under normal operation, including through repeated real backpressure |

To change the alarm email, update `ALARM_EMAIL` and redeploy `MemoryLayerAlarmsStack` — the
old subscription is removed and a confirmation email is sent to the new address, which must be
confirmed before it starts receiving notifications.

---

## 3. DLQ inspection and redrive

If `IngestionDlqVisible` fires:

1. **Inspect the messages** without deleting them:
   ```powershell
   aws sqs receive-message --queue-url <IngestionDLQUrl> --region ap-south-1 --max-number-of-messages 10 --visibility-timeout 0
   ```
   (`IngestionDLQUrl` is a `MemoryLayerDataStack` CloudFormation output.) Each message body is
   the original S3 `ObjectCreated` event — the object key embeds `userId`/`documentId`
   (`users/<userId>/documents/<documentId>/original/<fileName>`), recoverable for manual
   triage even without a dedicated sidecar log.

2. **Check why it got there** — CloudWatch Logs for `/aws/lambda/memory-layer-ingestion-coordinator`
   around the message's timestamp will show the real failure (structured `ingestion_job_start_failed`
   or `coordinator_message_failed` events, per §5 below, plus the full exception in the
   human-readable log line).

3. **Redrive once the root cause is fixed** — there is no automatic redrive configured (a
   human should look at DLQ messages before blindly replaying them, since a redrive without a
   fix just refills the DLQ):
   ```powershell
   aws sqs start-message-move-task --source-arn <IngestionDLQArn> --region ap-south-1
   ```
   Or, for a small number of messages, receive-and-resend manually via `send-message` to the
   main ingestion queue URL.

4. **After redriving**, confirm the affected document(s) actually reach `READY`/`FAILED` via
   `GET /api/v1/documents/{id}` or a DynamoDB lookup, and confirm the DLQ alarm clears.

---

## 4. Deployment procedure

**`Infra/deploy.ps1` is the only sanctioned way to deploy.** It exists specifically to prevent
repeats of two real incidents:

- **Stale jar (Phase 5):** `cdk deploy` was run without rebuilding `Backend/target/backend.jar`
  first, deploying code that predated the feature being shipped.
- **Placeholder Google client ID (Phase 5):** `GOOGLE_OAUTH_CLIENT_ID` was unset, so `InfraApp`
  silently fell back to a placeholder value that then got deployed to the real Cognito Google
  identity provider — briefly breaking Google Sign-In — compounded by `cdk deploy` pulling
  `MemoryLayerAuthStack` in as an undeclared dependency of another stack.

### Before running it

Set both required environment variables in the deploying shell:

```powershell
$env:GOOGLE_OAUTH_CLIENT_ID = "<the real Google OAuth client ID>"
$env:ALARM_EMAIL = "<address to receive CloudWatch alarm notifications>"
```

`InfraApp` fails synth immediately (before any AWS call) if either is unset or blank — this is
a hard guard, not just the script's own check, so it holds even if `deploy.ps1` is bypassed and
`cdk` is invoked directly.

### Running it

```powershell
cd Infra
./deploy.ps1 -Stacks MemoryLayerApiStack
```

Multiple stacks can be named at once (`-Stacks MemoryLayerIngestionStack, MemoryLayerApiStack`)
— they're passed to a single `cdk deploy ... --exclusively` call, not deployed in a loop, so
CDK still resolves correct ordering between the stacks you named while never silently including
one you didn't.

The script, in order:

1. Validates `GOOGLE_OAUTH_CLIENT_ID`/`ALARM_EMAIL` are set and the Google client ID doesn't
   look like a placeholder.
2. Runs `mvn clean package` in `Backend/` (use `-SkipTests` only for a fast iteration loop
   after already validating in the same session).
3. Runs `mvn test` in `Infra/`.
4. Runs `cdk diff` for exactly the named stacks and prints it for review.
5. Prompts for explicit confirmation (typing `deploy`) before doing anything to AWS.
6. Deploys with `--exclusively`.

### Frontend deployment

The frontend is **not** deployed via `cdk deploy` — `MemoryLayerFrontendStack`'s Amplify app
auto-builds from a `git push` to `main`. Pushing frontend changes is itself the deploy action
for that stack; `cdk deploy MemoryLayerFrontendStack` only touches the Amplify app's own
CloudFormation-managed configuration (env vars, build spec), not its content.

### After deploying

Run the smoke test (§6) and check the relevant CloudWatch log group for the deployed function(s)
for any new errors.

---

## 5. Structured logging

Every API request, and the key events in the Ingestion Coordinator, Status Reconciler, and
Stale Document Cleanup Lambdas, emit one JSON line (via `com.memorylayer.api.observability.StructuredLog`)
in addition to the existing human-readable log lines — CloudWatch Logs Insights can query these
directly.

Fields used, per event type: `requestId` (API only — the same id returned to the client in the
`Docs/API.md` §6 error envelope), `documentId`, `userIdHash` (a one-way hash, never the raw
Cognito `sub`), `jobId`, `status`/`fromStatus`, `latencyMs`, `errorType` (the exception's class
name, never its message).

**Never logged, anywhere:** JWTs, presigned URLs, `/ask` or `/search` question/query/answer
content, raw file content, or raw Bedrock session IDs. This is enforced by convention (there is
no field for any of these in the structured log call sites) — if you add a new structured log
call, do not add one.

Example query (CloudWatch Logs Insights, API log group):
```
fields @timestamp, event, requestId, status, durationMs, userIdHash
| filter event = "api_request"
| sort @timestamp desc
```

---

## 6. Smoke testing

`Infra/smoke-test.ps1` always checks `GET /api/v1/health` (public, no auth needed).

To also cover the authenticated routes (`GET /documents`, `POST /search`, `POST /ask`) against
the **real** API Gateway JWT authorizer (not a bypass), sign in through the real deployed
frontend, copy the Cognito access token (browser devtools → Application/Storage →
`oidc.user:...` → `access_token`), and run:

```powershell
$env:SMOKE_ACCESS_TOKEN = "<token>"
./Infra/smoke-test.ps1
```

**This project deliberately does not build a Hosted UI login automation harness** to acquire a
token programmatically (per the approved Phase 7 plan) — the Cognito app client is a public
PKCE client with no password-grant flow, by design (`Docs/ARCHITECTURE.md` §5). A manually
obtained token is the accepted tradeoff for exercising the real authorizer without that
investment.

Run this after every deploy, and periodically as a health check.

---

## 7. Frontend retry behavior

`Frontend/src/api/client.ts`'s `withRetry` helper wraps `listDocuments`, `getDocument`,
`getAccessUrl`, and `searchDocuments` — all idempotent reads — with up to 3 attempts and
exponential backoff (500ms, 1000ms, 2000ms) whenever the API responds `429` or `503`. This
means a transient rate limit or Bedrock throttle is often invisible to the user: the call just
succeeds a second or two later with no error ever surfaced. `initUploads` and `askQuestion` are
deliberately never retried client-side — replaying either could create a duplicate side effect
(re-provisioning an upload slot, or starting a second Bedrock session for `/ask`). If a user
reports a `429`/`503` error on `/ask` specifically, that is expected — not a gap.

## 8. Stale-document handling

`memory-layer-stale-cleanup` (a separate Lambda, handler, and EventBridge schedule from the
Status Reconciler — see `Docs/ARCHITECTURE.md` §7.5 and `com.memorylayer.api.ingestion.StaleDocumentPolicy`
for the full reasoning) runs every 15 minutes and catches documents stuck outside Bedrock's own
tracking:

| Status | Threshold | Action |
|---|---|---|
| `UPLOAD_PENDING` | 30 minutes | If the expected S3 object does not exist, transitions to `FAILED` with a safe upload-timeout reason. If the object *does* exist (an unexplained edge case — the S3 event never reached the pipeline), the document is left alone and a structured warning (`stale_upload_pending_object_exists`) is emitted for manual investigation. |
| `UPLOADED` | 2 hours | Transitions to `FAILED` with a safe processing-timeout reason — **unless** the document is referenced by an ingestion job Bedrock is still actively running (`STARTING`/`IN_PROGRESS`), in which case it is left alone and a `stale_uploaded_active_job` warning is emitted instead. Two hours comfortably exceeds the ~100-minute normal SQS backpressure budget, so a document still `UPLOADED` at that point has already fallen out of normal retry, not been caught mid-retry — but `StartIngestionJob` is a real, non-retractable side effect, so a document can still be genuinely covered by an in-flight job even after its own status write to `INDEXING` failed. See `StaleDocumentPolicy.activeJobDocumentRefs` and its regression tests in `StaleDocumentCleanupHandlerTest`. |
| `INDEXING` | 1 hour | **Never auto-failed.** Bedrock's own ingestion-job status, polled by the Status Reconciler, remains the sole authority for this state. Only a structured warning (`stale_indexing_document`) is emitted, including the `ingestionJobId` for cross-reference against `GetIngestionJob`. |

### Scale note

This job does a **full DynamoDB table scan** filtered by status — there is no GSI for a
status+time access pattern, and per the approved Phase 7 plan, adding one solely for this
low-frequency (every 15 minutes), MVP-scale cleanup job is not justified. **Production scale
would need an indexed status+time access pattern** (e.g. a GSI keyed on `status` with a sort
key on the relevant timestamp) instead of this scan.

### Known limitation: a document orphaned by an already-finished job

The active-job guard above only covers jobs still `STARTING`/`IN_PROGRESS`. If a document's
own status write to `INDEXING` failed but its ingestion job went on to `COMPLETE`/`FAILED`
before the next stale-cleanup run, the document is still auto-failed at the 2-hour mark even
though Bedrock may have actually indexed its content — the Status Reconciler only ever updates
documents still at `INDEXING` (`StatusReconcilerHandler.reconcileComplete`/`reconcileFailed`),
so a document that never reached that state is never resolved by it, however the job turned
out. This is a pre-existing, rare edge case (a per-document DynamoDB write failing after a
successful `StartIngestionJob` call) that predates Phase 7; fixing it would mean teaching the
Reconciler to also resolve `UPLOADED` documents against a job's outcome, which is out of scope
for this phase's stale-cleanup verification.

### Manually investigating a stale-warning document

1. Search CloudWatch Logs (`/aws/lambda/memory-layer-stale-cleanup`) for the `documentId` in the
   warning event.
2. For `stale_indexing_document`, cross-reference the logged `ingestionJobId` against
   `GetIngestionJob` (or the `SYSTEM#INGESTION` DynamoDB partition) to see its real Bedrock
   status.
3. For `stale_upload_pending_object_exists`, check whether the object at the document's
   `s3Key` is genuinely the right content, then decide manually whether to re-trigger ingestion
   (e.g. by re-uploading) or investigate why the original `ObjectCreated` event was lost.
