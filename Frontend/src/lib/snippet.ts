const ENTITIES: Record<string, string> = {
  '&amp;': '&',
  '&lt;': '<',
  '&gt;': '>',
  '&quot;': '"',
  '&#39;': "'",
  '&nbsp;': ' ',
}

/**
 * Display-only cleanup for retrieval snippets. The Knowledge Base returns raw chunk text, which
 * can include Markdown syntax and BDA's XML-like markup (e.g. `<figure>…</figure>`). This turns
 * that into plain, single-paragraph prose for the UI. It never touches what is stored or
 * indexed — only what is rendered — and its output is only ever rendered as React text nodes.
 */
export function cleanSnippet(raw: string, maxChars = 280): string {
  let text = raw.replace(/<!--[\s\S]*?-->/g, ' ')
  // Markup tags (keeps the text inside, e.g. a <figcaption>'s description).
  text = text.replace(/<\/?[a-zA-Z][\w:-]*(\s[^<>]*)?\/?>/g, ' ')
  text = text.replace(/```[\s\S]*?```/g, ' ').replace(/```/g, ' ')

  const lines: string[] = []
  for (const line of text.split(/\r?\n/)) {
    // Horizontal rules and table separator rows carry no content.
    if (/^\s*([-*_=]{3,}|[|:\-\s]+)\s*$/.test(line)) continue
    lines.push(
      line
        .replace(/^\s{0,3}#{1,6}\s*/, '')
        .replace(/^\s*>+\s?/, '')
        .replace(/^\s*([-*+]|\d+[.)])\s+/, '')
        .replace(/^\s*\|\s*|\s*\|\s*$/g, '')
        .replace(/\s*\|\s*/g, ' · '),
    )
  }

  text = lines
    .join(' ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/(\*\*|__)(.+?)\1/g, '$2')
    .replace(/(^|\s)\*(\S[^*]*?)\*(?=\s|$|[.,;:!?])/g, '$1$2')
    .replace(/&(?:amp|lt|gt|quot|nbsp|#39);/g, (entity) => ENTITIES[entity] ?? entity)
    .replace(/\s+/g, ' ')
    .trim()

  if (text.length <= maxChars) return text
  const cut = text.slice(0, maxChars)
  const lastSpace = cut.lastIndexOf(' ')
  return `${(lastSpace > maxChars * 0.6 ? cut.slice(0, lastSpace) : cut).replace(/[\s,;:.\-–—]+$/, '')}…`
}
