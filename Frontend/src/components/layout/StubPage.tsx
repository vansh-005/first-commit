/** Placeholder for routes not yet implemented — keeps routing structure in place ahead of later phases. */
export function StubPage({ title }: { title: string }) {
  return (
    <main className="flex min-h-screen items-center justify-center">
      <p className="text-text-secondary">{title} — coming in a later phase.</p>
    </main>
  )
}
