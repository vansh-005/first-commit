# Demo sample files

Small, realistic, visually distinct memories for the hackathon demo — not `test.pdf` / `file1.png`
(see `Docs/FRONTEND.md` §25). Upload them through the app (Library → Upload) — Markdown goes through
the text-document path, which was verified end to end in Phase 4.

For a richer demo, add a few real assets alongside these: an AWS credits **screenshot** (PNG/JPG),
a short **voice memo** or lecture clip (M4A/MP3/WAV, real speech), and a short **video** (MP4).
Image / audio / video are what exercise the multimodal path and timestamped citations.

## Suggested demo script

| Step | Do this | Expect |
|---|---|---|
| Search | "that AWS credits screenshot" | `aws-credits-note.md` (and any real screenshot) at the top, query words highlighted |
| Search | "when is my electricity bill due" | `electricity-bill-august.md` |
| Search | "the lecture about fading" | `wireless-lecture-fading-notes.md` |
| Ask | "How much AWS credit did I have?" | "$200", cites `aws-credits-note.md` |
| Ask | "What did my internship offer say about relocation?" | ₹40,000 on joining, cites `internship-offer-letter.md` |
| Ask (follow-up) | "And what was the monthly stipend?" | ₹60,000 — follow-up resolved from the previous turn |
| Ask | "When does my laptop warranty end?" | 11 March 2028, cites `laptop-warranty-receipt.md` |

The Markdown files contain fictional personal details only.
