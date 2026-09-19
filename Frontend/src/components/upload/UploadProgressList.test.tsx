import type { UploadItem } from '@/hooks/useFileUpload'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { UploadProgressList } from './UploadProgressList'

function item(overrides: Partial<UploadItem> & { name: string }): UploadItem {
  const { name, ...rest } = overrides
  return { clientFileId: name, file: new File(['x'], name), progress: 0, status: 'queued', ...rest }
}

describe('UploadProgressList', () => {
  it('shows a real progress bar only while uploading', () => {
    render(<UploadProgressList items={[item({ name: 'a.pdf', status: 'uploading', progress: 42 })]} />)
    expect(screen.getByRole('progressbar', { name: /uploading a\.pdf/i })).toHaveAttribute('aria-valuenow', '42')
    expect(screen.getByText('Uploading 42%')).toBeInTheDocument()
  })

  it('shows a friendly failure message', () => {
    render(<UploadProgressList items={[item({ name: 'a.pdf', status: 'failed', error: 'This file didn’t upload. Please try again.' })]} />)
    expect(screen.getByText('Failed')).toBeInTheDocument()
    expect(screen.getByText('This file didn’t upload. Please try again.')).toBeInTheDocument()
  })

  it('warns, without blocking, about file types that may not be searchable', () => {
    render(<UploadProgressList items={[item({ name: 'installer.exe' }), item({ name: 'notes.md' })]} />)
    expect(screen.getAllByText(/may not be searchable/i)).toHaveLength(1)
  })
})
