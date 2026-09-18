/** Placeholder for routes not yet implemented — keeps routing structure in place ahead of
 * later phases. Renders inside AppShell's own <main>, so this only fills the content area,
 * not the full viewport. */
export function StubPage({ title }: { title: string }) {
  return (
    <div className="flex h-full min-h-[50vh] items-center justify-center p-6">
      <p className="text-text-secondary">{title} — coming in a later phase.</p>
    </div>
  )
}
