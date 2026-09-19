import { ApiError } from '@/api/client'
import { describe, expect, it } from 'vitest'
import { friendlyError } from './errors'

describe('friendlyError', () => {
  it('maps auth, busy and network failures to actionable copy without technical detail', () => {
    expect(friendlyError(new ApiError('Unauthorized', 401))).toMatch(/sign in again/i)
    expect(friendlyError(new ApiError('Bedrock ThrottlingException', 503))).toMatch(/busy/i)
    expect(friendlyError(new ApiError('x', 429))).toMatch(/busy/i)
    expect(friendlyError(new TypeError('Failed to fetch'))).toMatch(/connection/i)
  })

  it('never leaks a 5xx message, but passes through a 400 validation message', () => {
    expect(friendlyError(new ApiError('DynamoDbException: boom', 500))).toBe('Something went wrong. Please try again.')
    expect(friendlyError(new ApiError('Question must not be blank', 400))).toBe('Question must not be blank')
  })

  it('uses the supplied fallback for unknown errors', () => {
    expect(friendlyError(new Error('weird'), 'Custom fallback')).toBe('Custom fallback')
  })
})
