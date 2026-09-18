import type { UploadItem, UploadItemStatus } from '@/hooks/useFileUpload'

const STATUS_LABEL: Record<UploadItemStatus, string> = {
  queued: 'Queued',
  uploading: 'Uploading',
  uploaded: 'Uploaded',
  failed: 'Failed',
}

export function UploadProgressList({ items }: { items: UploadItem[] }) {
  if (items.length === 0) {
    return null
  }

  return (
    <ul className="flex flex-col gap-2">
      {items.map((item) => (
        <li
          key={item.clientFileId}
          className="flex items-center justify-between gap-4 rounded-[var(--radius-md)] border border-border bg-surface-raised px-4 py-2 text-sm"
        >
          <span className="truncate text-text-primary">{item.file.name}</span>
          <span className={item.status === 'failed' ? 'text-error' : 'text-text-secondary'}>
            {item.status === 'uploading' ? `${STATUS_LABEL[item.status]} ${item.progress}%` : STATUS_LABEL[item.status]}
          </span>
        </li>
      ))}
    </ul>
  )
}
