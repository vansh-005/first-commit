import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { LandingPage } from './LandingPage'

describe('LandingPage', () => {
  it('leads with the product promise and routes both CTAs to sign-in', () => {
    render(
      <MemoryRouter>
        <LandingPage />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Your digital life, remembered.')
    for (const cta of screen.getAllByRole('link', { name: /start remembering/i })) {
      expect(cta).toHaveAttribute('href', '/login')
    }
    expect(screen.getByRole('link', { name: /see how it works/i })).toHaveAttribute('href', '#how-it-works')
  })

  it('presents exactly three value propositions', () => {
    render(
      <MemoryRouter>
        <LandingPage />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: 'Upload anything' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Search naturally' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Ask your memory' })).toBeInTheDocument()
  })
})
