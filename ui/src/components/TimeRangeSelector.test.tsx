import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { TimeRangeSelector } from './TimeRangeSelector'
import type { TimeRange } from '../types'

const defaultRange: TimeRange = {
  from: '2025-01-15T00:00:00.000Z',
  to: '2025-01-15T23:59:59.000Z',
  label: 'Last 24h',
}

describe('TimeRangeSelector', () => {
  it('renders all preset buttons', () => {
    render(<TimeRangeSelector value={defaultRange} onChange={() => {}} />)

    expect(screen.getByTestId('time-preset-Last 1h')).toBeInTheDocument()
    expect(screen.getByTestId('time-preset-Last 24h')).toBeInTheDocument()
    expect(screen.getByTestId('time-preset-Last 7d')).toBeInTheDocument()
    expect(screen.getByTestId('time-preset-Today')).toBeInTheDocument()
    expect(screen.getByTestId('time-preset-Custom')).toBeInTheDocument()
  })

  it('displays the current range label', () => {
    render(<TimeRangeSelector value={defaultRange} onChange={() => {}} />)

    expect(screen.getByTestId('time-range-label')).toHaveTextContent('Last 24h')
  })

  it('calls onChange with computed range when a preset is clicked', () => {
    const onChange = vi.fn()
    render(<TimeRangeSelector value={defaultRange} onChange={onChange} />)

    fireEvent.click(screen.getByTestId('time-preset-Last 1h'))

    expect(onChange).toHaveBeenCalledTimes(1)
    const arg = onChange.mock.calls[0][0] as TimeRange
    expect(arg.label).toBe('Last 1h')
    expect(arg.from).toBeTruthy()
    expect(arg.to).toBeTruthy()
  })

  it('shows custom inputs when Custom button is clicked', () => {
    render(<TimeRangeSelector value={defaultRange} onChange={() => {}} />)

    expect(screen.queryByTestId('custom-range-inputs')).not.toBeInTheDocument()

    fireEvent.click(screen.getByTestId('time-preset-Custom'))

    expect(screen.getByTestId('custom-range-inputs')).toBeInTheDocument()
    expect(screen.getByTestId('custom-from')).toBeInTheDocument()
    expect(screen.getByTestId('custom-to')).toBeInTheDocument()
  })

  it('hides custom inputs when a preset is selected after Custom', () => {
    render(<TimeRangeSelector value={defaultRange} onChange={() => {}} />)

    fireEvent.click(screen.getByTestId('time-preset-Custom'))
    expect(screen.getByTestId('custom-range-inputs')).toBeInTheDocument()

    fireEvent.click(screen.getByTestId('time-preset-Last 7d'))
    expect(screen.queryByTestId('custom-range-inputs')).not.toBeInTheDocument()
  })

  it('calls onChange with Custom label when custom from input changes', () => {
    const onChange = vi.fn()
    render(<TimeRangeSelector value={defaultRange} onChange={onChange} />)

    fireEvent.click(screen.getByTestId('time-preset-Custom'))
    fireEvent.change(screen.getByTestId('custom-from'), { target: { value: '2025-01-14T08:00' } })

    expect(onChange).toHaveBeenCalled()
    const arg = onChange.mock.calls[0][0] as TimeRange
    expect(arg.label).toBe('Custom')
    expect(arg.to).toBe(defaultRange.to)
  })
})
