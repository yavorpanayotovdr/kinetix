import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ScenarioControlBar } from './ScenarioControlBar'

const defaultProps = {
  onRunAll: vi.fn(),
  loading: false,
  confidenceLevel: 'CL_95',
  onConfidenceLevelChange: vi.fn(),
  timeHorizonDays: '1',
  onTimeHorizonDaysChange: vi.fn(),
}

describe('ScenarioControlBar', () => {
  it('should render Run All button, confidence select, horizon select', () => {
    render(<ScenarioControlBar {...defaultProps} />)

    expect(screen.getByTestId('run-all-btn')).toBeInTheDocument()
    expect(screen.getByTestId('confidence-level-select')).toBeInTheDocument()
    expect(screen.getByTestId('time-horizon-select')).toBeInTheDocument()
  })

  it('should call onRunAll when Run All is clicked', () => {
    const onRunAll = vi.fn()
    render(<ScenarioControlBar {...defaultProps} onRunAll={onRunAll} />)

    fireEvent.click(screen.getByTestId('run-all-btn'))
    expect(onRunAll).toHaveBeenCalledOnce()
  })

  it('should call onConfidenceLevelChange when confidence is changed', () => {
    const onChange = vi.fn()
    render(<ScenarioControlBar {...defaultProps} onConfidenceLevelChange={onChange} />)

    fireEvent.change(screen.getByTestId('confidence-level-select'), { target: { value: 'CL_99' } })
    expect(onChange).toHaveBeenCalledWith('CL_99')
  })

  it('should render Custom Scenario button when handler provided', () => {
    render(<ScenarioControlBar {...defaultProps} onCustomScenario={vi.fn()} />)

    expect(screen.getByTestId('custom-scenario-btn')).toBeInTheDocument()
  })

  it('should not render Custom Scenario button when handler not provided', () => {
    render(<ScenarioControlBar {...defaultProps} />)

    expect(screen.queryByTestId('custom-scenario-btn')).not.toBeInTheDocument()
  })

  it('should disable Run All button while loading', () => {
    render(<ScenarioControlBar {...defaultProps} loading={true} />)

    expect(screen.getByTestId('run-all-btn')).toHaveTextContent('Running...')
  })
})
