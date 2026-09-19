const DECLINE_PATTERNS = [
  // "I can not provide an answer…", "I couldn't find…", "unable to locate…"
  /\b(can\s?not|can't|cannot|could\s?not|couldn't|unable to|do(?:es)? not|don't|doesn't)\b[^.]{0,80}\b(find|provide|answer|locate|determine|assist)\b/i,
  // "…the search results do not contain information…"
  /\b(search results|provided (?:context|information|documents?)|available (?:information|context))\b[^.]{0,60}\b(do(?:es)? not|don't|doesn't|no)\b[^.]{0,40}\b(contain|include|mention|have|information)\b/i,
  /^\s*sorry,?\s+i\s+(?:am|'m)\s+unable\b/i,
]

// A real answer is rarely this short *and* phrased as a refusal; longer text is never treated as one.
const MAX_DECLINE_LENGTH = 240

/**
 * Best-effort: does this answer read as "I couldn't find that in your files"?
 *
 * There is no structural signal for this. Verified live against the Knowledge Base: a decline and
 * a normal answer have an identical response shape (no guardrail action, one citation spanning the
 * whole text) — a decline simply attaches every retrieved chunk as its references. So this is a
 * deliberate, narrow text heuristic, and it is used only to *relabel* attached references as
 * "Context checked" — never to hide them — so a wrong guess costs a label, not a real source.
 */
export function looksLikeNoAnswer(answer: string): boolean {
  const text = answer.trim()
  return text.length > 0 && text.length <= MAX_DECLINE_LENGTH && DECLINE_PATTERNS.some((pattern) => pattern.test(text))
}
