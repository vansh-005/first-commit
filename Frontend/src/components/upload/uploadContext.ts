import type { UploadItem } from '@/hooks/useFileUpload'
import { createContext, useContext } from 'react'

export interface UploadContextValue {
  items: UploadItem[]
  uploadFiles: (files: File[]) => Promise<void>
  dialogOpen: boolean
  openDialog: () => void
  closeDialog: () => void
}

export const UploadContext = createContext<UploadContextValue | null>(null)

/** One upload queue for the whole authenticated app, so uploads keep running (and stay visible)
 * whichever page — or the dialog — started them. */
export function useUploads(): UploadContextValue {
  const value = useContext(UploadContext)
  if (!value) throw new Error('useUploads must be used inside <UploadProvider>')
  return value
}
