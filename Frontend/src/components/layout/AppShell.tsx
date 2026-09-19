import { MobileNavDrawer } from '@/components/layout/MobileNavDrawer'
import { Sidebar } from '@/components/layout/Sidebar'
import { TopBar } from '@/components/layout/TopBar'
import { UploadProvider } from '@/components/upload/UploadProvider'
import { useUploads } from '@/components/upload/uploadContext'
import { useState } from 'react'
import { Outlet } from 'react-router-dom'

/**
 * Docs/FRONTEND.md §6 — the persistent authenticated workspace shell: resizable/collapsible
 * sidebar, shared top bar with the global search input, and the routed page content. Every
 * /app/* route renders inside this shell via the nested layout route in router.tsx. The upload
 * queue/dialog is provided here so "Upload" works from every page.
 */
export function AppShell() {
  return (
    <UploadProvider>
      <ShellLayout />
    </UploadProvider>
  )
}

function ShellLayout() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  const { openDialog } = useUploads()

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <a
        href="#main-content"
        className="sr-only z-[60] rounded-[var(--radius-md)] bg-accent px-3 py-2 text-sm text-white focus:not-sr-only focus:absolute focus:left-3 focus:top-3"
      >
        Skip to content
      </a>
      <Sidebar onUploadClick={openDialog} />
      <MobileNavDrawer open={mobileNavOpen} onClose={() => setMobileNavOpen(false)} onUploadClick={openDialog} />

      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar onOpenMobileNav={() => setMobileNavOpen(true)} />
        <main id="main-content" tabIndex={-1} className="flex-1 overflow-y-auto focus:outline-none">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
