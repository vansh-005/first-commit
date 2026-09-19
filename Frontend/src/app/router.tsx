import { LandingPage } from '@/pages/LandingPage'
import { createBrowserRouter } from 'react-router-dom'

// Routes per Docs/FRONTEND.md Section 5. "/" and "/login" stand alone; every /app/* route is
// nested under the single persistent AppShell layout (Section 6 — sidebar + top bar), wrapped
// once in ProtectedRoute rather than per-page. The authenticated app is code-split so the public
// landing page (the first thing a visitor loads) doesn't pay for it.
export const router = createBrowserRouter([
  { path: '/', element: <LandingPage /> },
  { path: '/login', lazy: async () => ({ Component: (await import('@/pages/LoginPage')).LoginPage }) },
  {
    path: '/app',
    lazy: async () => {
      const [{ AppShell }, { ProtectedRoute }] = await Promise.all([
        import('@/components/layout/AppShell'),
        import('@/components/layout/ProtectedRoute'),
      ])
      return {
        Component: () => (
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        ),
      }
    },
    children: [
      { index: true, lazy: async () => ({ Component: (await import('@/pages/HomePage')).HomePage }) },
      { path: 'library', lazy: async () => ({ Component: (await import('@/pages/LibraryPage')).LibraryPage }) },
      { path: 'search', lazy: async () => ({ Component: (await import('@/pages/SearchPage')).SearchPage }) },
      { path: 'ask', lazy: async () => ({ Component: (await import('@/pages/AskPage')).AskPage }) },
      { path: 'document/:documentId', lazy: async () => ({ Component: (await import('@/pages/DocumentPage')).DocumentPage }) },
    ],
  },
])
