import { render, screen, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { UploadDropzone } from './UploadDropzone'

function makeFile(name: string, type = 'text/plain') {
  return new File(['content'], name, { type })
}

describe('UploadDropzone', () => {
  it('renders a multiple-capable file input', () => {
    render(<UploadDropzone onFilesSelected={vi.fn()} />)
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    expect(input).toHaveAttribute('multiple')
  })

  it('passes every selected file to onFilesSelected when multiple files are chosen via the picker', async () => {
    const onFilesSelected = vi.fn()
    render(<UploadDropzone onFilesSelected={onFilesSelected} />)

    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    const files = [makeFile('a.txt'), makeFile('b.pdf'), makeFile('c.png', 'image/png')]

    await userEvent.upload(input, files)

    expect(onFilesSelected).toHaveBeenCalledTimes(1)
    expect(onFilesSelected.mock.calls[0][0]).toHaveLength(3)
    expect(onFilesSelected.mock.calls[0][0].map((f: File) => f.name)).toEqual(['a.txt', 'b.pdf', 'c.png'])
  })

  it('passes every dropped file to onFilesSelected, not just the first', () => {
    const onFilesSelected = vi.fn()
    render(<UploadDropzone onFilesSelected={onFilesSelected} />)

    const dropzone = screen.getByText('Drop your memories here').closest('div')!
    const files = [makeFile('a.txt'), makeFile('b.pdf')]

    fireEvent.drop(dropzone, { dataTransfer: { files } })

    expect(onFilesSelected).toHaveBeenCalledTimes(1)
    expect(onFilesSelected.mock.calls[0][0]).toHaveLength(2)
  })
})
