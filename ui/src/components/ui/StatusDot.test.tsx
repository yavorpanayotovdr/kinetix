import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { StatusDot } from './StatusDot'

describe('StatusDot — accessibility', () => {
  it('has role="status" for screen reader announcements', () => {
    render(<StatusDot status="up" />)

    const dot = screen.getByRole('status')
    expect(dot).toBeInTheDocument()
  })

  it('has aria-label "Connected" when status is up', () => {
    render(<StatusDot status="up" />)

    const dot = screen.getByRole('status')
    expect(dot).toHaveAttribute('aria-label', 'Connected')
  })

  it('has aria-label "Disconnected" when status is down', () => {
    render(<StatusDot status="down" />)

    const dot = screen.getByRole('status')
    expect(dot).toHaveAttribute('aria-label', 'Disconnected')
  })

  it('applies solid border class when status is up', () => {
    const { container } = render(<StatusDot status="up" />)

    const dot = container.firstChild as HTMLElement
    expect(dot.className).toMatch(/border-solid|rounded-full/)
    // The "up" dot should NOT have a dashed border
    expect(dot.className).not.toMatch(/border-dashed/)
  })

  it('applies dashed border class when status is down', () => {
    const { container } = render(<StatusDot status="down" />)

    const dot = container.firstChild as HTMLElement
    expect(dot.className).toMatch(/border-dashed/)
  })
})
