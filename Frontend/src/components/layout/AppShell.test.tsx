import { ThemeProvider } from '@/app/theme'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppShell } from './AppShell'

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({
    user: { profile: { email: 'ada@example.com', name: 'Ada Lovelace' } },
    removeUser: vi.fn().mockResolvedValue(undefined),
  }),
}))
vi.mock('@/api/client', () => ({
  initUploads: vi.fn(),
  uploadFileToS3: vi.fn(),
  ApiError: class extends Error {},
}))

function renderShell() {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={['/app']}>
        <Routes>
          <Route path="/app" element={<AppShell />}>
            <Route index element={<div>page-content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

beforeEach(() => {
  localStorage.clear()
  document.documentElement.setAttribute('data-theme', 'dark')
})

describe('AppShell', () => {
  it('focuses the global search on Ctrl+K', async () => {
    renderShell()
    await userEvent.keyboard('{Control>}k{/Control}')
    expect(screen.getByRole('searchbox', { name: /search your memory/i })).toHaveFocus()
  })

  it('opens the global upload dialog from the sidebar and closes it with Escape', async () => {
    renderShell()
    await userEvent.click(screen.getByRole('button', { name: 'Upload' }))
    expect(screen.getByRole('dialog', { name: /add to your memory/i })).toBeInTheDocument()

    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: /add to your memory/i })).not.toBeInTheDocument()
  })

  it('shows the signed-in user in an avatar menu and switches + persists the theme', async () => {
    renderShell()
    await userEvent.click(screen.getByRole('button', { name: /account menu/i }))
    expect(screen.getByText('Ada Lovelace')).toBeInTheDocument()
    expect(screen.getByText('ada@example.com')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('radio', { name: /light/i }))
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
    expect(localStorage.getItem('recollect-theme')).toBe('light')
    expect(screen.getByRole('radio', { name: /light/i })).toHaveAttribute('aria-checked', 'true')
  })

  it('opens the mobile drawer as a modal dialog and returns focus to the menu button on Escape', async () => {
    renderShell()
    const menuButton = screen.getByRole('button', { name: /open navigation/i })
    await userEvent.click(menuButton)
    expect(screen.getByRole('dialog', { name: /navigation/i })).toHaveAttribute('aria-modal', 'true')

    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: /navigation/i })).not.toBeInTheDocument()
    expect(menuButton).toHaveFocus()
  })
})
