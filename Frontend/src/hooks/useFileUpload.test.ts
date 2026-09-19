import { initUploads } from '@/api/client'
import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useFileUpload } from './useFileUpload'

vi.mock('@/api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/client')>()
  return { ...actual, initUploads: vi.fn(), uploadFileToS3: vi.fn() }
})

beforeEach(() => {
  vi.clearAllMocks()
})

describe('useFileUpload', () => {
  it('marks every file failed with a friendly message when the batch cannot be started, instead of leaving them queued', async () => {
    vi.mocked(initUploads).mockRejectedValue(new TypeError('Failed to fetch'))
    const { result } = renderHook(() => useFileUpload())

    await act(async () => {
      await result.current.uploadFiles([new File(['a'], 'a.pdf'), new File(['b'], 'b.png')])
    })

    expect(result.current.items).toHaveLength(2)
    for (const item of result.current.items) {
      expect(item.status).toBe('failed')
      expect(item.error).toMatch(/connection/i)
    }
  })
})
