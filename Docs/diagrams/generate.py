"""Generates the three canonical Recollect architecture diagrams (SVG) into this directory.

    python Docs/diagrams/generate.py

These SVGs are the source of truth for the public /engineering page (Frontend inlines them) and for
Docs/ARCHITECTURE.md. Edit the layout here and re-run; do not hand-edit the .svg files.
Only service names and structural key prefixes appear - never account ids, bucket names, ARNs or user ids.
"""
import os

from diagramkit import *  # noqa: F401,F403

OUT = os.path.dirname(os.path.abspath(__file__))


def write(name, diagram):
    with open(os.path.join(OUT, name), "w", encoding="utf8", newline="\n") as f:
        f.write(diagram.svg())
    print("wrote", name)


# =====================================================================================================
# 1. High-level architecture
# =====================================================================================================
def high_level():
    d = Diagram(
        "hla", 1480, 764,
        "Recollect high-level architecture",
        "Browser, Amplify hosting, Cognito and API Gateway in front of a Java Lambda with SnapStart. "
        "Files upload directly to S3; S3 events flow through SQS to an ingestion coordinator that drives a Bedrock "
        "Knowledge Base (Data Automation parser, Titan embeddings, S3 Vectors). DynamoDB holds application state. "
        "EventBridge runs the status reconciler and stale-document cleanup; CloudWatch alarms notify through SNS.",
    )

    # ---- client / edge
    d.node(40, 384, 178, 70, "Amplify Hosting", ["React SPA · Git CI/CD"], "muted", "Client", icon="amplify")
    d.node(40, 232, 150, 70, "Browser", ["React + TypeScript"], "node", "Client")
    d.node(250, 92, 180, 70, "Amazon Cognito", ["Google + email · JWTs"], "node", "Identity", icon="cognito")
    d.node(250, 232, 180, 84, "API Gateway", ["HTTP API · JWT authorizer", "30 s limit"], "node", "Edge", icon="apigw")

    # ---- compute
    d.node(500, 232, 220, 96, "API Lambda", ["Java 21 · SnapStart · 28 s", "identity = validated JWT sub"], "primary", "Compute", icon="lambda")
    d.node(500, 384, 220, 150, "Background workers", ["Ingestion Coordinator", "Status Reconciler", "Stale-document Cleanup"], "node", "Lambda", icon="lambda")

    # ---- state / storage
    d.node(790, 92, 190, 86, "S3 uploads bucket", ["users/ · originals", "kb/ · staging + sidecars"], "node", "Storage", icon="s3")
    d.node(790, 232, 190, 70, "DynamoDB", ["single table · on-demand"], "node", "State", icon="dynamodb")
    d.node(790, 384, 190, 58, "SQS ingestion queue", ["buffers upload bursts"], "node", "Queue", icon="sqs")
    d.node(790, 476, 190, 58, "Dead-letter queue", ["any message raises an alarm"], "fail", "Failure", icon="sqs")

    # ---- Bedrock knowledge base
    d.group(1040, 78, 232, 360, "Bedrock Knowledge Base")
    d.icon("bedrock", 1234, 82, 26)
    d.node(1056, 112, 200, 58, "Data Automation parser", ["kb/multimodal/ · PDF, image,", "audio, video"], "node")
    d.node(1056, 182, 200, 46, "Default text parser", ["kb/text/ · text files"], "node")
    d.node(1056, 240, 200, 58, "Titan Text Embeddings V2", ["1024-d vectors"], "node")
    d.node(1056, 310, 200, 58, "S3 Vectors", ["cosine index · tenant filter"], "primary", icon="s3-vectors")
    d.node(1330, 232, 130, 70, "Nova Lite", ["Ask answers only"], "node", "Generation", icon="bedrock")

    # ---- operations band
    d.group(24, 620, 1432, 96, "Operations")
    d.node(60, 644, 200, 56, "EventBridge", ["rate(1 min) · rate(15 min)"], "muted", icon="eventbridge")
    d.node(310, 644, 220, 56, "CloudWatch", ["structured logs · 8 alarms"], "muted", icon="cloudwatch")
    d.node(580, 644, 210, 56, "SNS topic", ["alarm email"], "fail", icon="sns")
    d.node(840, 644, 220, 56, "AWS CDK", ["all infrastructure as code"], "muted", icon="cdk")
    d.text(1440, 676, "Region ap-south-1", 10.5, 500, TEXT3, anchor="end")

    # ---- edges
    d.edge([(90, 232), (90, 44), (885, 44), (885, 92)], "primary", "direct PUT via presigned URL", 470, 44)
    d.badge(90, 130, 7)
    d.edge([(115, 302), (115, 384)], "secondary")
    d.badge(115, 343, 1)
    d.edge([(165, 232), (165, 127), (250, 127)], "secondary", None)
    d.badge(207, 127, 2)
    d.edge([(190, 274), (250, 274)], "primary")
    d.badge(220, 258, 3)
    d.edge([(340, 162), (340, 232)], "dashed", "validates JWT", 340, 197)
    d.edge([(430, 280), (500, 280)], "primary")
    d.badge(465, 264, 4)
    d.edge([(720, 262), (790, 262)], "primary")
    d.badge(755, 246, 5)
    d.edge([(610, 232), (610, 135), (790, 135)], "dashed", "presigned URLs only", 700, 135)
    d.badge(610, 200, 6)
    d.edge([(690, 232), (690, 22), (1148, 22), (1148, 78)], "primary", "Retrieve · RetrieveAndGenerate", 900, 22)
    d.badge(690, 62, 12)
    d.edge([(980, 118), (1004, 118), (1004, 413), (980, 413)], "secondary")
    d.badge(1004, 250, 8)
    d.edge([(980, 98), (1040, 98)], "secondary")
    d.edge([(790, 424), (720, 424)], "secondary")
    d.badge(755, 408, 9)
    d.edge([(720, 398), (748, 398), (748, 290), (790, 290)], "secondary")
    d.edge([(885, 442), (885, 476)], "fail")
    d.edge([(610, 534), (610, 578), (1148, 578), (1148, 438)], "primary", "StartIngestionJob · GetIngestionJob", 880, 578)
    d.badge(610, 560, 10)
    d.edge([(1156, 170), (1156, 182)], "secondary")
    d.edge([(1156, 298), (1156, 310)], "primary")
    d.badge(1056, 312, 11)
    d.edge([(1272, 267), (1330, 267)], "secondary")
    d.edge([(160, 644), (160, 600), (450, 600), (450, 468), (500, 468)], "secondary", "schedules", 300, 600)
    d.edge([(530, 672), (580, 672)], "fail")

    # ---- legend
    d.legend_swatch(40, 742, "primary", "primary data path")
    d.legend_swatch(230, 742, "secondary", "secondary path")
    d.legend_swatch(410, 742, "dashed", "signed / validated")
    d.legend_swatch(610, 742, "fail", "failure path")
    return d


# =====================================================================================================
# 2. Upload + ingestion pipeline (sequence)
# =====================================================================================================
def upload_ingestion():
    d = Diagram(
        "upl", 1480, 980,
        "Recollect upload and ingestion pipeline",
        "A sequence from the browser requesting presigned URLs, uploading directly to S3, the S3 event buffered by SQS, "
        "the Ingestion Coordinator staging files into kb/multimodal or kb/text with metadata sidecars and starting a "
        "Bedrock Knowledge Base ingestion job, and the EventBridge-driven Status Reconciler moving documents to READY or "
        "FAILED. Failure paths: one-job-at-a-time backpressure, the dead-letter queue, and stale-document cleanup.",
    )
    X = {"browser": 105, "api": 285, "ddb": 465, "s3": 645, "sqs": 825, "coord": 1010, "kb": 1195, "recon": 1375}
    heads = [
        ("browser", "Browser", "React SPA", "node", 130),
        ("api", "API Lambda", "Java · SnapStart", "primary", 150),
        ("ddb", "DynamoDB", "document state", "node", 130),
        ("s3", "S3 bucket", "users/ · kb/", "node", 130),
        ("sqs", "SQS queue", "+ dead-letter queue", "node", 150),
        ("coord", "Coordinator", "Lambda", "node", 130),
        ("kb", "Bedrock KB", "ingestion job", "primary", 130),
        ("recon", "Status Reconciler", "Lambda · every 1 min", "node", 160),
    ]
    for key, title, sub, kind, w in heads:
        d.node(X[key] - w / 2, 24, w, 56, title, [sub], kind)
        d.parts.append(
            f'<line x1="{X[key]}" y1="80" x2="{X[key]}" y2="800" style="stroke:{BORDER_STRONG};stroke-width:1;stroke-dasharray:3 5"/>'
        )

    def step(n, y, frm, to, label, kind="primary", lx=None):
        x1, x2 = X[frm], X[to]
        d.edge([(x1 + (13 if x2 > x1 else -13), y), (x2 + (-6 if x2 > x1 else 6), y)], kind, label,
               lx if lx is not None else (x1 + x2) / 2 + (6 if x2 > x1 else -6), y - 12)
        d.badge(x1, y, n)

    step(1, 122, "browser", "api", "POST /uploads (file list)")
    step(2, 170, "api", "ddb", "Document = UPLOAD_PENDING", "secondary")
    step(3, 218, "api", "browser", "presigned PUT URLs", "dashed")
    step(4, 266, "browser", "s3", "PUT bytes directly — never through the API")
    step(5, 314, "s3", "sqs", "ObjectCreated · users/ prefix only", "secondary")
    step(6, 362, "sqs", "coord", "buffered batch", "secondary")
    step(7, 410, "coord", "ddb", "status = UPLOADED", "secondary", lx=740)
    step(8, 458, "coord", "s3", "stage copy + .metadata.json sidecar", "primary", lx=830)
    step(9, 506, "coord", "ddb", "status = INDEXING · save job record", "secondary", lx=740)
    step(10, 554, "coord", "kb", "StartIngestionJob")

    # staging split callout (beside step 8)
    d.node(140, 590, 460, 62, "Routed by file type into two staging prefixes",
           ["kb/multimodal/ → Data Automation parser (PDF, image, audio, video)", "kb/text/ → default text parser"], "muted")

    # backpressure alternative
    d.rect(650, 596, 330, 104, "group", r=12, sw=1)
    d.text(664, 614, "ONE INGESTION JOB PER KNOWLEDGE BASE", 9.5, 600, TEXT3, tracking="1")
    d.note(664, 634, ["ConflictException → documents stay UPLOADED and",
                      "the SQS message retries later. Backpressure,",
                      "not failure — a healthy upload never hits the DLQ."], 10.5, TEXT2)

    # indexing inside KB
    d.node(1100, 596, 250, 82, "Index", ["parse → chunk → Titan Text", "Embeddings V2 → S3 Vectors", "with userId / documentId metadata"], "primary")
    d.edge([(1195, 560), (1195, 596)], "primary")
    d.edge([(1010, 566), (1010, 648), (984, 648)], "dashed", "if a job is already running", 1012, 606)

    step(11, 740, "recon", "kb", "GetIngestionJob (poll)", "secondary")
    step(12, 786, "recon", "ddb", "READY · or FAILED with a safe reason", "primary", lx=920)

    # ---- bottom cards
    d.group(24, 826, 1432, 138, "Document state and safety nets")
    ladder = ["UPLOAD_PENDING", "UPLOADED", "INDEXING", "READY", "FAILED"]
    x = 48
    for i, name in enumerate(ladder):
        w = 26 + 7.4 * len(name)
        kind = "primary" if name == "READY" else ("fail" if name == "FAILED" else "node")
        d.rect(x, 858, w, 30, kind, r=15)
        d.text(x + w / 2, 877, name, 10.5, 600, TEXT, anchor="middle")
        if i < len(ladder) - 1:
            d.edge([(x + w + 3, 873), (x + w + 17, 873)], "secondary")
        x += w + 20
    d.note(48, 912, ["Stale-document cleanup (every 15 min): UPLOAD_PENDING > 30 min with no object → FAILED;",
                     "UPLOADED > 2 h and not covered by an active job → FAILED; INDEXING > 1 h → warning only."], 10.5, TEXT2)
    d.node(880, 852, 290, 92, "Dead-letter queue", ["messages that exhaust their retries", "→ CloudWatch alarm → SNS email"], "fail", "Failure path")
    d.node(1190, 852, 244, 92, "Alarms", ["reconciler not running · queue age", "Lambda errors · DLQ occupancy"], "fail", "Failure path")
    return d


# =====================================================================================================
# 3. Search + Ask / retrieval flow
# =====================================================================================================
def search_ask():
    d = Diagram(
        "sra", 1480, 1170,
        "Recollect search and ask retrieval flow",
        "Every request derives identity from the validated JWT subject and injects a server-side tenant filter. Search "
        "retrieves chunks, applies a corpus-calibrated 0.62 relevance gate before de-duplication and returns documents or "
        "an empty list. Ask resolves a server-owned AskSession, runs a gated preflight retrieval, then either returns a "
        "deterministic no-answer, answers file-existence questions from metadata while storing contextDocumentIds, or runs "
        "RetrieveAndGenerate with Nova Lite scoped to relevant or context documents, with one bounded recovery retry and "
        "the successful Bedrock session id persisted into the same AskSession.",
    )
    # ---- shared front
    d.node(40, 30, 140, 62, "Browser", ["Bearer JWT"], "node")
    d.node(230, 30, 190, 62, "API Gateway", ["JWT authorizer"], "node")
    d.node(470, 24, 560, 74, "API Lambda", ["identity = validated JWT sub · never a client-supplied id",
                                             "tenant filter userId == sub is injected server-side on every retrieval"], "primary", "Every request")
    d.edge([(180, 61), (230, 61)], "primary")
    d.edge([(420, 61), (470, 61)], "primary")

    # ================= SEARCH
    d.group(40, 140, 500, 580, "POST /search — Retrieve · no LLM")
    d.edge([(500, 98), (500, 118), (290, 118), (290, 140)], "primary")
    d.node(60, 176, 460, 66, "Retrieve", ["vector search under the tenant filter (+ optional media type)", "oversampled chunks"], "node")
    d.node(60, 280, 460, 96, "Relevance gate", ["keep chunks scoring ≥ 0.62 similarity — corpus-calibrated",
                                                 "against ~75 labelled queries, and tunable",
                                                 "applied per chunk, before document de-duplication"], "primary", "Key step")
    d.node(60, 414, 460, 66, "De-duplicate by document", ["resolve each in DynamoDB under the caller's partition",
                                                           "(ownership is structural, not a filter)"], "node")
    d.node(60, 520, 220, 70, "Ranked results", ["file · snippet · media timestamps"], "node")
    d.node(300, 520, 220, 70, "Nothing relevant", ["results: [] — never arbitrary", "nearest neighbours"], "muted")
    d.edge([(290, 242), (290, 280)], "primary")
    d.edge([(290, 376), (290, 414)], "primary")
    d.edge([(290, 480), (290, 500), (170, 500), (170, 520)], "primary")
    d.edge([(290, 500), (410, 500), (410, 520)], "secondary")
    d.note(60, 630, ["Scores drive the decision only — they are never shown to users",
                     "or written to logs. Snippets are sanitised for display."], 10.5, TEXT3)
    d.note(60, 680, ["Ask uses this same retrieval and gate as its preflight,",
                     "so Search and Ask never disagree about relevance."], 10.5, TEXT3)

    # ================= ASK
    d.group(580, 140, 860, 1018, "POST /ask — grounded answers")
    d.edge([(750, 98), (750, 140)], "primary")
    L, LW = 604, 320   # spine column
    R, RW = 956, 460   # outcome column
    MID = L + LW / 2

    d.node(L, 176, LW, 94, "Resolve AskSession", ["under the caller's own partition; a foreign or",
                                                    "expired id → 409, indistinguishably",
                                                    "loads contextDocumentIds + optional Bedrock id"], "node", "Server-owned")
    d.node(R, 176, RW, 82, "Conversation context", ["contextDocumentIds is written only by the backend and",
                                                      "re-verified against the caller's own documents every turn;",
                                                      "clients never send document ids · bedrockSessionId is nullable"], "muted")
    d.edge([(MID, 270), (MID, 296)], "primary")

    d.node(L, 296, LW, 70, "Context-only conversation?", ["contextDocumentIds ≠ ∅ · no Bedrock session yet",
                                                            "· not a file-lookup question"], "node", "Decision")
    d.edge([(MID, 366), (MID, 412)], "primary", "no", MID + 18, 393)

    d.node(L, 412, LW, 62, "Preflight Retrieve", ["same tenant filter · same 0.62 relevance gate"], "primary")
    d.edge([(MID, 474), (MID, 512)], "primary")

    d.node(L, 512, LW, 56, "Anything relevant?", ["(context sessions stay on their file)"], "node", "Decision")
    d.edge([(L + LW, 540), (R, 540)], "secondary", "no", 940, 540)
    d.node(R, 504, RW, 74, "Deterministic no-answer", ["“I couldn't find anything in your memories…”",
                                                         "no model call · zero citations · sessionId null"], "muted", "Outcome")
    d.edge([(MID, 568), (MID, 608)], "primary", "yes", MID + 18, 590)

    d.node(L, 608, LW, 62, "File-lookup question?", ["“do I have …?” · “find my …”"], "node", "Decision")
    d.edge([(L + LW, 639), (R, 639)], "secondary", "yes", 940, 639)
    d.node(R, 604, RW, 92, "Answer from file metadata", ["the model never sees filenames, so this is deterministic",
                                                         "writes AskSession.contextDocumentIds · bedrockSessionId = null",
                                                         "returns the opaque sessionId (creates the session if needed)"], "node", "Outcome")
    d.edge([(MID, 670), (MID, 726)], "primary", "no", MID + 18, 698)

    d.node(L, 726, LW, 98, "RetrieveAndGenerate", ["Amazon Nova Lite (APAC inference profile)",
                                                     "scoped: userId ∧ documentId ∈ (relevant | context)",
                                                     "grounded prompt · answers only from the user's files"], "primary", "Generation")
    # context-only branch: left gutter -> generation
    d.edge([(L, 331), (592, 331), (592, 775), (L, 775)], "primary")
    d.pill(592, 553, "yes")
    d.node(R, 296, RW, 70, "Context-scoped follow-up", ["“explain this”, “summarize it”, “what is question 2?”",
                                                          "wording anchored to the file, asked once"], "muted", "Why")
    d.edge([(L + LW, 331), (R, 331)], "dashed", arrow=False)

    d.edge([(MID, 824), (MID, 870)], "primary")
    d.node(L, 870, LW, 56, "Refused or no grounding?", ["only when relevant documents are known"], "node", "Decision")
    d.edge([(L + LW, 898), (R, 898)], "secondary", "yes", 940, 898)
    d.node(R, 862, RW, 74, "One bounded recovery retry", ["fresh Bedrock session · file-anchored wording · at most once",
                                                             "(never on a context-only conversation's first call)"], "muted", "Recovery")
    d.edge([(MID, 926), (MID, 972)], "primary", "no", MID + 18, 950)
    d.edge([(R + RW / 2, 936), (R + RW / 2, 954), (MID + 160, 954), (MID + 160, 972)], "secondary")

    d.node(L, 972, 812, 66, "Persist the session", ["the Bedrock sessionId of the successful attempt is saved into the same AskSession",
                                                      "(replacing a failed attempt's) · contextDocumentIds is kept"], "primary")
    d.edge([(L + 406, 1038), (L + 406, 1064)], "primary")
    d.node(L, 1064, 812, 78, "Response", ["answer + citations only from gate-passing or context documents · refusals carry no sources",
                                            "no reference objects returned? cite the source file only — no invented snippet or timestamp"], "node", "Outcome")
    return d


if __name__ == "__main__":
    write("high-level-architecture.svg", high_level())
    write("upload-ingestion.svg", upload_ingestion())
    write("search-ask.svg", search_ask())
