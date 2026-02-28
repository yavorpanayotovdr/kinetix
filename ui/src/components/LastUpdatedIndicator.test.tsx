import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LastUpdatedIndicator } from './LastUpdatedIndicator'

describe('LastUpdatedIndicator', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-02-28T14:35:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders null when no timestamp is provided', () => {
    const { container } = render(<LastUpdatedIndicator timestamp={null} />)

    expect(container.firstChild).toBeNull()
  })

  it('displays the formatted time', () => {
    render(<LastUpdatedIndicator timestamp="2026-02-28T14:32:00Z" />)

    const el = screen.getByTestId('last-updated')
    expect(el).toHaveTextContent('Last refreshed')
  })

  it('shows relative time for fresh data (under 1 minute)', () => {
    vi.setSystemTime(new Date('2026-02-28T14:32:30Z'))

    render(<LastUpdatedIndicator timestamp="2026-02-28T14:32:00Z" />)

    expect(screen.getByTestId('last-updated')).toHaveTextContent('just now')
  })

  it('shows minutes-ago for data under 5 minutes old', () => {
    render(<LastUpdatedIndicator timestamp="2026-02-28T14:32:00Z" />)

    expect(screen.getByTestId('last-updated')).toHaveTextContent('3 min ago')
  })

  it('applies neutral styling when data is under 5 minutes old', () => {
    render(<LastUpdatedIndicator timestamp="2026-02-28T14:32:00Z" />)

    const el = screen.getByTestId('last-updated')
    expect(el.className).not.toContain('text-amber')
    expect(el.className).not.toContain('text-red')
  })

  it('applies amber styling when data is 5-15 minutes old', () => {
    render(<LastUpdatedIndicator timestamp="2026-02-28T14:25:00Z" />)

    const el = screen.getByTestId('last-updated')
    expect(el.className).toContain('text-amber-600')
  })

  it('applies red styling when data is over 15 minutes old', () => {
    render(<LastUpdatedIndicator timestamp="2026-02-28T14:15:00Z" />)

    const el = screen.getByTestId('last-updated')
    expect(el.className).toContain('text-red-600')
  })

  it('shows hours-ago for data over 60 minutes old', () => {
    render(<LastUpdatedIndicator timestamp="2026-02-28T12:30:00Z" />)

    expect(screen.getByTestId('last-updated')).toHaveTextContent('2 hours ago')
  })
})
