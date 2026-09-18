import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'

const mockUseAuth = vi.fn()
vi.mock('react-oidc-context', () => ({
  useAuth: () => mockUseAuth(),
}))

describe('LoginPage post-authentication redirect', () => {
  it('returns to the originally requested route (router state) after authentication, not always /app', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: true, isLoading: false, user: { state: undefined }, error: undefined })

    render(
      <MemoryRouter initialEntries={[{ pathname: '/login', state: { from: '/app/library' } }]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/app/library" element={<div>library-page</div>} />
          <Route path="/app" element={<div>home-page</div>} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('library-page')).toBeInTheDocument()
  })

  it('prefers the OAuth round-tripped state (survives a real Cognito redirect) over router state', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: true, isLoading: false, user: { state: '/app/library' }, error: undefined })

    render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/app/library" element={<div>library-page</div>} />
          <Route path="/app" element={<div>home-page</div>} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('library-page')).toBeInTheDocument()
  })

  it('falls back to /app when there is no prior destination', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: true, isLoading: false, user: { state: undefined }, error: undefined })

    render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/app" element={<div>home-page</div>} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('home-page')).toBeInTheDocument()
  })
})
