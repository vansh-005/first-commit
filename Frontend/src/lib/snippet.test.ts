import { describe, expect, it } from 'vitest'
import { cleanSnippet } from './snippet'

describe('cleanSnippet', () => {
  it('leaves ordinary text untouched', () => {
    expect(cleanSnippet('AWS promotional credits available...')).toBe('AWS promotional credits available...')
  })

  it('removes Markdown headings, emphasis, list markers, links and images', () => {
    const raw = '## Lecture 4\n\n- **Fading** is [variation](http://x.test) in _signal_\n- ![chart](img.png) second point\n\n---\n1. third'
    expect(cleanSnippet(raw)).toBe('Lecture 4 Fading is variation in _signal_ second point third')
  })

  it('removes BDA/XML-like markup but keeps the descriptive text inside it', () => {
    const raw = '<figure>\n  <figcaption>A bar chart of received signal strength</figcaption>\n</figure>'
    expect(cleanSnippet(raw)).toBe('A bar chart of received signal strength')
    expect(cleanSnippet('<figure></figure>')).toBe('')
  })

  it('flattens Markdown tables', () => {
    expect(cleanSnippet('| Item | Cost |\n|---|---|\n| Laptop | 84,990 |')).toBe('Item · Cost Laptop · 84,990')
  })

  it('collapses whitespace and decodes common entities', () => {
    expect(cleanSnippet('Tom &amp; Jerry\n\n\n   met   at\tnoon&nbsp;today')).toBe('Tom & Jerry met at noon today')
  })

  it('clamps long text at a word boundary with an ellipsis', () => {
    const long = 'word '.repeat(200)
    const out = cleanSnippet(long, 100)
    expect(out.length).toBeLessThanOrEqual(101)
    expect(out.endsWith('…')).toBe(true)
    expect(out).not.toMatch(/wor…$/)
  })

  it('never yields angle-bracket tags, heading hashes or emphasis markers', () => {
    const out = cleanSnippet('# Title\n<figure><b>bold</b></figure> **x** `code`')
    expect(out).not.toMatch(/[<>#*`]/)
  })
})
