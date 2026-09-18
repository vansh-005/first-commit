import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ProtectedRoute } from './ProtectedRoute'

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({ isLoading: false, isAuthenticated: false }),
}))

function LoginProbe() {
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  return <div>login-page from={from ?? 'none'}</div>
}

describe('ProtectedRoute', () => {
  it('redirects an unauthenticated visit to /login while preserving the originally requested path', () => {
    render(
      <MemoryRouter initialEntries={['/app/library']}>
        <Routes>
          <Route path="/login" element={<LoginProbe />} />
          <Route
            path="/app/library"
            element={
              <ProtectedRoute>
                <div>library-page</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('login-page from=/app/library')).toBeInTheDocument()
  })
})
