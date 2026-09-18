import { ProtectedRoute } from '@/components/layout/ProtectedRoute'
import { AskPage } from '@/pages/AskPage'
import { DocumentPage } from '@/pages/DocumentPage'
import { HomePage } from '@/pages/HomePage'
import { LandingPage } from '@/pages/LandingPage'
import { LibraryPage } from '@/pages/LibraryPage'
import { LoginPage } from '@/pages/LoginPage'
import { SearchPage } from '@/pages/SearchPage'
import { createBrowserRouter } from 'react-router-dom'

// Routes per Docs/FRONTEND.md Section 5. "/", "/login" and "/app" have real content as of
// Phase 2; Library/Search/Ask/Document remain stubs for later phases. Everything under
// /app requires authentication.
export const router = createBrowserRouter([
  { path: '/', element: <LandingPage /> },
  { path: '/login', element: <LoginPage /> },
  {
    path: '/app',
    element: (
      <ProtectedRoute>
        <HomePage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/app/library',
    element: (
      <ProtectedRoute>
        <LibraryPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/app/search',
    element: (
      <ProtectedRoute>
        <SearchPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/app/ask',
    element: (
      <ProtectedRoute>
        <AskPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/app/document/:documentId',
    element: (
      <ProtectedRoute>
        <DocumentPage />
      </ProtectedRoute>
    ),
  },
])
