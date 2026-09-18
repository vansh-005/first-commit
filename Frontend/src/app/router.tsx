import { AskPage } from '@/pages/AskPage'
import { DocumentPage } from '@/pages/DocumentPage'
import { HomePage } from '@/pages/HomePage'
import { LandingPage } from '@/pages/LandingPage'
import { LibraryPage } from '@/pages/LibraryPage'
import { LoginPage } from '@/pages/LoginPage'
import { SearchPage } from '@/pages/SearchPage'
import { createBrowserRouter } from 'react-router-dom'

// Routes per Docs/FRONTEND.md Section 5. Only "/" has real content in Phase 1;
// the rest are stubs that keep the information architecture in place for later phases.
export const router = createBrowserRouter([
  { path: '/', element: <LandingPage /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/app', element: <HomePage /> },
  { path: '/app/library', element: <LibraryPage /> },
  { path: '/app/search', element: <SearchPage /> },
  { path: '/app/ask', element: <AskPage /> },
  { path: '/app/document/:documentId', element: <DocumentPage /> },
])
