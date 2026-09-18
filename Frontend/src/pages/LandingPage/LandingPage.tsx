import { DevHealthBadge } from '@/components/layout/DevHealthBadge'

export function LandingPage() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 px-6 text-center">
      <h1 className="text-4xl font-semibold text-text-primary">Your files remember more than you do.</h1>
      <p className="max-w-md text-text-secondary">
        Upload documents, screenshots, audio and video once. Find them later using natural
        language.
      </p>
      {import.meta.env.DEV && <DevHealthBadge />}
    </main>
  )
}
