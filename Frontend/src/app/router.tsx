import { LandingPage } from '@/pages/LandingPage'
import { createBrowserRouter } from 'react-router-dom'

// Routes per Docs/FRONTEND.md Section 5. "/", "/login" and "/engineering" (the public engineering showcase) stand
// alone and need no authentication; every /app/* route is nested under the single persistent AppShell layout
// (Section 6 - sidebar + top bar), wrapped once in ProtectedRoute rather than per-page. Everything except the
// landing page is code-split, so a first-time visitor only downloads what they open.
export const router = createBrowserRouter([
  { path: '/', element: <LandingPage /> },
  { path: '/engineering', lazy: async () => ({ Component: (await import('@/pages/EngineeringPage')).EngineeringPage }) },
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
    ],
  },
])
