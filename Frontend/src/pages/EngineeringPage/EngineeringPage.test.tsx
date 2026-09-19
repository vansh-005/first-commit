import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { GITHUB_URL } from '@/lib/links'
import { LandingPage } from '@/pages/LandingPage'
import { EngineeringPage } from './EngineeringPage'

const renderPage = () =>
  render(
    <MemoryRouter>
      <EngineeringPage />
    </MemoryRouter>,
  )

describe('EngineeringPage', () => {
  it('renders publicly with the hero, every section and both CTAs', () => {
    renderPage()

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Engineering Recollect')
    for (const id of ['architecture', 'upload', 'retrieval', 'security', 'data-model', 'reliability', 'decisions', 'challenges', 'tradeoffs', 'api']) {
      expect(document.getElementById(id), `section #${id}`).not.toBeNull()
    }
    expect(screen.getAllByRole('link', { name: /try recollect/i })[0]).toHaveAttribute('href', '/login')
    expect(screen.getAllByRole('link', { name: /view github/i })[0]).toHaveAttribute('href', GITHUB_URL)
  })

  it('inlines the three canonical diagrams', () => {
    const { container } = renderPage()
    expect(container.querySelectorAll('figure svg[role="img"], figure svg').length).toBeGreaterThanOrEqual(3)
  })

  it('exposes no account, resource or user identifiers', () => {
    const { container } = renderPage()
    const text = container.innerHTML

    expect(text).not.toMatch(/\b\d{12}\b/) // AWS account id
    expect(text).not.toMatch(/arn:aws/i)
    expect(text).not.toMatch(/ap-south-1_[A-Za-z0-9]+/) // Cognito user pool id
    expect(text).not.toMatch(/[\w.+-]+@[\w-]+\.[\w.]+/) // email addresses
    expect(text).not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i) // UUIDs (user / document ids)
    expect(text).not.toMatch(/\.s3\.[a-z0-9-]*\.?amazonaws\.com|execute-api\.|cloudfront\.net/i)
  })
})

describe('public navigation', () => {
  it('links to the engineering page from the landing header', () => {
    render(
      <MemoryRouter>
        <LandingPage />
      </MemoryRouter>,
    )
    expect(screen.getAllByRole('link', { name: 'Engineering' })[0]).toHaveAttribute('href', '/engineering')
  })
})
