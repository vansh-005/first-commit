import { AppShell } from '@/components/layout/AppShell'
import { ProtectedRoute } from '@/components/layout/ProtectedRoute'
import { AskPage } from '@/pages/AskPage'
import { DocumentPage } from '@/pages/DocumentPage'
import { HomePage } from '@/pages/HomePage'
import { LandingPage } from '@/pages/LandingPage'
import { LibraryPage } from '@/pages/LibraryPage'
import { LoginPage } from '@/pages/LoginPage'
import { SearchPage } from '@/pages/SearchPage'
import { createBrowserRouter } from 'react-router-dom'

// Routes per Docs/FRONTEND.md Section 5. "/" and "/login" stand alone; every /app/* route is
// nested under the single persistent AppShell layout (Section 6 — sidebar + top bar), wrapped
// once in ProtectedRoute rather than per-page. Ask/Document remain content stubs for later
// phases but still render inside the shell, since the nav must stay visible everywhere.
export const router = createBrowserRouter([
  { path: '/', element: <LandingPage /> },
  { path: '/login', element: <LoginPage /> },
  {
    path: '/app',
    element: (
      <ProtectedRoute>
        <AppShell />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <HomePage /> },
      { path: 'library', element: <LibraryPage /> },
      { path: 'search', element: <SearchPage /> },
      { path: 'ask', element: <AskPage /> },
      { path: 'document/:documentId', element: <DocumentPage /> },
    ],
  },
])
