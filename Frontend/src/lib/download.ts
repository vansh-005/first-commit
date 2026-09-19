// Beyond this, buffering the file in browser memory is unwise; fall back to opening it in a tab.
export const MAX_IN_MEMORY_DOWNLOAD_BYTES = 200 * 1024 * 1024

/**
 * Saves a file from a short-lived presigned URL under its real filename.
 *
 * Cross-origin `<a download>` is ignored by browsers and the API can't force a
 * Content-Disposition, so the bytes are fetched (the bucket's CORS allows GET from this app) and
 * saved from a Blob. No Authorization header is sent — the presigned URL is the credential — and
 * the URL is never logged.
 */
export async function saveFromUrl(url: string, fileName: string): Promise<void> {
  const response = await fetch(url)
  if (!response.ok) throw new Error(`Download failed with status ${response.status}`)
  const blob = await response.blob()

  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  setTimeout(() => URL.revokeObjectURL(objectUrl), 10_000)
}
