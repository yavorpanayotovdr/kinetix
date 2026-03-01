import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { DataQualityIndicator } from './DataQualityIndicator'
import type { DataQualityStatus } from '../types'

describe('DataQualityIndicator', () => {
  const allOkStatus: DataQualityStatus = {
    overall: 'OK',
    checks: [
      { name: 'Price Freshness', status: 'OK', message: 'All prices fresh', lastChecked: '2025-01-15T10:00:00Z' },
      { name: 'Position Count', status: 'OK', message: 'Counts consistent', lastChecked: '2025-01-15T10:00:00Z' },
      { name: 'Risk Result Completeness', status: 'OK', message: 'All complete', lastChecked: '2025-01-15T10:00:00Z' },
    ],
  }

  const warningStatus: DataQualityStatus = {
    overall: 'WARNING',
    checks: [
      { name: 'Price Freshness', status: 'WARNING', message: 'Price staleness detected for AAPL', lastChecked: '2025-01-15T10:00:00Z' },
      { name: 'Position Count', status: 'OK', message: 'Counts consistent', lastChecked: '2025-01-15T10:00:00Z' },
      { name: 'Risk Result Completeness', status: 'OK', message: 'All complete', lastChecked: '2025-01-15T10:00:00Z' },
    ],
  }

  const criticalStatus: DataQualityStatus = {
    overall: 'CRITICAL',
    checks: [
      { name: 'Price Freshness', status: 'CRITICAL', message: 'All prices stale', lastChecked: '2025-01-15T10:00:00Z' },
      { name: 'Position Count', status: 'WARNING', message: 'Count mismatch', lastChecked: '2025-01-15T10:00:00Z' },
      { name: 'Risk Result Completeness', status: 'OK', message: 'All complete', lastChecked: '2025-01-15T10:00:00Z' },
    ],
  }

  it('shows green status when all checks pass', () => {
    render(<DataQualityIndicator status={allOkStatus} loading={false} />)

    const indicator = screen.getByTestId('data-quality-indicator')
    expect(indicator).toBeDefined()
    expect(indicator.querySelector('[data-testid="dq-status-ok"]')).toBeDefined()
  })

  it('shows yellow warning when price staleness detected', () => {
    render(<DataQualityIndicator status={warningStatus} loading={false} />)

    const indicator = screen.getByTestId('data-quality-indicator')
    expect(indicator.querySelector('[data-testid="dq-status-warning"]')).toBeDefined()
  })

  it('shows red alert when critical issues found', () => {
    render(<DataQualityIndicator status={criticalStatus} loading={false} />)

    const indicator = screen.getByTestId('data-quality-indicator')
    expect(indicator.querySelector('[data-testid="dq-status-critical"]')).toBeDefined()
  })

  it('displays check details on click', () => {
    render(<DataQualityIndicator status={allOkStatus} loading={false} />)

    const button = screen.getByTestId('data-quality-indicator')
    fireEvent.click(button)

    expect(screen.getByText('Price Freshness')).toBeDefined()
    expect(screen.getByText('Position Count')).toBeDefined()
    expect(screen.getByText('Risk Result Completeness')).toBeDefined()
  })

  it('shows loading state', () => {
    render(<DataQualityIndicator status={null} loading={true} />)

    expect(screen.getByTestId('data-quality-loading')).toBeDefined()
  })

  it('hides dropdown when clicked outside', () => {
    render(<DataQualityIndicator status={allOkStatus} loading={false} />)

    const button = screen.getByTestId('data-quality-indicator')
    fireEvent.click(button)

    expect(screen.getByText('Price Freshness')).toBeDefined()

    // Click the button again to close
    fireEvent.click(button)

    expect(screen.queryByText('Price Freshness')).toBeNull()
  })
})
