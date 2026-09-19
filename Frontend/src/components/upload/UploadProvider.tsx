import { UploadDialog } from '@/components/upload/UploadDialog'
import { UploadContext } from '@/components/upload/uploadContext'
import { useFileUpload } from '@/hooks/useFileUpload'
import { useCallback, useMemo, useState, type ReactNode } from 'react'

export function UploadProvider({ children }: { children: ReactNode }) {
  const { items, uploadFiles } = useFileUpload()
  const [dialogOpen, setDialogOpen] = useState(false)
  const openDialog = useCallback(() => setDialogOpen(true), [])
  const closeDialog = useCallback(() => setDialogOpen(false), [])

  const value = useMemo(
    () => ({ items, uploadFiles, dialogOpen, openDialog, closeDialog }),
    [items, uploadFiles, dialogOpen, openDialog, closeDialog],
  )

  return (
    <UploadContext.Provider value={value}>
      {children}
      <UploadDialog />
    </UploadContext.Provider>
  )
}
