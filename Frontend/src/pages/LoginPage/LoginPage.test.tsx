import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'

const mockUseAuth = vi.fn()
vi.mock('react-oidc-context', () => ({
  useAuth: () => mockUseAuth(),
}))

describe('LoginPage sign-in panel', () => {
  function renderLogin() {
    return render(
      <MemoryRouter initialEntries={['/login']}>
        <LoginPage />
      </MemoryRouter>,
    )
  }

  it('starts Google sign-in with the identity_provider hint and shows redirect feedback', async () => {
    const signinRedirect = vi.fn().mockResolvedValue(undefined)
    mockUseAuth.mockReturnValue({ isAuthenticated: false, isLoading: false, signinRedirect, error: undefined })
    renderLogin()

    await userEvent.click(screen.getByRole('button', { name: /continue with google/i }))

    expect(signinRedirect).toHaveBeenCalledWith({ extraQueryParams: { identity_provider: 'Google' }, state: undefined })
    expect(screen.getByText(/redirecting to google/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /sign in with email/i })).toBeDisabled()
  })

  it('starts email sign-in without the Google hint', async () => {
    const signinRedirect = vi.fn().mockResolvedValue(undefined)
    mockUseAuth.mockReturnValue({ isAuthenticated: false, isLoading: false, signinRedirect, error: undefined })
    renderLogin()

    await userEvent.click(screen.getByRole('button', { name: /sign in with email/i }))

    expect(signinRedirect).toHaveBeenCalledWith({ state: undefined })
  })

  it('shows a friendly alert when sign-in failed', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: false, isLoading: false, signinRedirect: vi.fn(), error: new Error('boom') })
    renderLogin()

    expect(screen.getByRole('alert')).toHaveTextContent(/couldn’t sign you in/i)
    expect(screen.queryByText('boom')).not.toBeInTheDocument()
  })
})

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
