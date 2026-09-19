const STOPWORDS = new Set([
  'the', 'and', 'for', 'that', 'with', 'from', 'this', 'what', 'was', 'were', 'are', 'how', 'did', 'has', 'have',
  'you', 'your', 'about', 'into', 'not', 'when', 'where', 'who', 'which', 'find', 'show', 'get', 'any', 'all',
])

/** Meaningful lowercase words from a search query, for lexical snippet highlighting. */
export function queryTerms(query: string): string[] {
  const words = query
    .toLowerCase()
    .split(/[^\p{L}\p{N}]+/u)
    .filter((word) => word.length >= 3 && !STOPWORDS.has(word))
  return Array.from(new Set(words))
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/** Splits `text` into alternating [plain, match, plain, match, …] segments (odd indexes are
 * matches). Returns a single plain segment when there is nothing to highlight. */
export function splitByTerms(text: string, terms: string[]): string[] {
  if (terms.length === 0) return [text]
  const pattern = new RegExp(`(${terms.map(escapeRegExp).join('|')})`, 'gi')
  return text.split(pattern)
}
