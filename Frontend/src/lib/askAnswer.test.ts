import { describe, expect, it } from 'vitest'
import { looksLikeNoAnswer } from './askAnswer'

describe('looksLikeNoAnswer', () => {
  it('recognises the decline wording seen live from the Knowledge Base', () => {
    expect(
      looksLikeNoAnswer('I can not provide an answer to the question as the search results do not contain information that can answer the question.'),
    ).toBe(true)
  })

  it('recognises other common decline phrasings', () => {
    for (const text of [
      'Sorry, I am unable to assist you with this request.',
      "I couldn't find anything about that in your files.",
      'The provided information does not contain details on this.',
      'I cannot answer that from the available context.',
    ]) {
      expect(looksLikeNoAnswer(text)).toBe(true)
    }
  })

  it('does not flag normal grounded answers', () => {
    for (const text of [
      'You had $200 in AWS promotional credits, valid through December 31.',
      'The offer states relocation expenses are covered up to $2,000.',
      'The words are "giraffe", "umbrella", "cactus", and "42".',
    ]) {
      expect(looksLikeNoAnswer(text)).toBe(false)
    }
  })

  it('never treats a long, substantive answer as a decline', () => {
    const long = `I couldn't find the exact figure, but ${'the document describes the policy in detail and '.repeat(8)}so overall it applies.`
    expect(looksLikeNoAnswer(long)).toBe(false)
  })

  it('handles empty text', () => {
    expect(looksLikeNoAnswer('   ')).toBe(false)
  })
})
