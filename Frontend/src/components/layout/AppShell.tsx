import { MobileNavDrawer } from '@/components/layout/MobileNavDrawer'
import { Sidebar } from '@/components/layout/Sidebar'
import { TopBar } from '@/components/layout/TopBar'
import { useState } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'

/**
 * Docs/FRONTEND.md §6 — the persistent authenticated workspace shell: resizable/collapsible
 * sidebar, shared top bar with the global search input, and the routed page content. Every
 * /app/* route renders inside this shell via the nested layout route in router.tsx.
 */
export function AppShell() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  const navigate = useNavigate()

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <Sidebar onUploadClick={() => navigate('/app/library')} />
      <MobileNavDrawer open={mobileNavOpen} onClose={() => setMobileNavOpen(false)} />

      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar onOpenMobileNav={() => setMobileNavOpen(true)} />
        <main className="flex-1 overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
