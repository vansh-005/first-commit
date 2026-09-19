import { describe, expect, it } from 'vitest'
import { isLikelySearchable } from './fileTypes'
import { queryTerms, splitByTerms } from './highlight'

describe('queryTerms', () => {
  it('keeps meaningful words and drops stopwords, short words and duplicates', () => {
    expect(queryTerms('Find the AWS credits for AWS')).toEqual(['aws', 'credits'])
  })
})

describe('splitByTerms', () => {
  it('returns alternating plain/match segments, case-insensitively', () => {
    expect(splitByTerms('AWS promotional Credits here', ['aws', 'credits'])).toEqual(['', 'AWS', ' promotional ', 'Credits', ' here'])
  })

  it('returns the text untouched when there is nothing to highlight', () => {
    expect(splitByTerms('anything', [])).toEqual(['anything'])
  })

  it('treats regex metacharacters in the query literally', () => {
    expect(splitByTerms('cost (usd) $5', ['(usd)'])).toEqual(['cost ', '(usd)', ' $5'])
  })
})

describe('isLikelySearchable', () => {
  it('accepts known document, image, audio and video formats', () => {
    for (const name of ['a.PDF', 'b.md', 'c.jpeg', 'd.m4a', 'e.mp4']) expect(isLikelySearchable(name)).toBe(true)
  })

  it('flags unknown or extensionless files', () => {
    for (const name of ['setup.exe', 'archive.zip', 'README', '.env']) expect(isLikelySearchable(name)).toBe(false)
  })
})
