import { describe, expect, it } from 'vitest'
import type { AlertEventDto } from '../types'
import { formatAlertMessage } from './alertMessageFormatter'

function makeAlert(overrides: Partial<AlertEventDto> = {}): AlertEventDto {
  return {
    id: 'alert-1',
    ruleId: 'rule-1',
    ruleName: 'Daily PnL Limit',
    type: 'PNL_THRESHOLD',
    severity: 'WARNING',
    message: 'Daily PnL Alert: PNL_THRESHOLD GREATER_THAN 250000.0 (current: 251954.53464219306) for portfolio macro-hedge',
    currentValue: 251954.53,
    threshold: 250000,
    portfolioId: 'macro-hedge',
    triggeredAt: '2026-02-28T12:00:00Z',
    ...overrides,
  }
}

describe('formatAlertMessage', () => {
  it('formats PNL_THRESHOLD alerts with human-readable message', () => {
    const result = formatAlertMessage(makeAlert())

    expect(result).toBe('WARNING: Daily P&L exceeded $250,000 limit — current: $251,955 (macro-hedge)')
  })

  it('formats VAR_BREACH alerts', () => {
    const result = formatAlertMessage(makeAlert({
      type: 'VAR_BREACH',
      ruleName: 'VaR Limit',
      severity: 'CRITICAL',
      threshold: 2000000,
      currentValue: 2300000,
      portfolioId: 'global-book',
    }))

    expect(result).toBe('CRITICAL: VaR breached $2,000,000 limit — current: $2,300,000 (global-book)')
  })

  it('formats CONCENTRATION alerts', () => {
    const result = formatAlertMessage(makeAlert({
      type: 'CONCENTRATION',
      ruleName: 'Position Concentration',
      severity: 'WARNING',
      threshold: 30,
      currentValue: 42.5,
      portfolioId: 'equity-book',
    }))

    expect(result).toBe('WARNING: Concentration exceeded 30% limit — current: 42.50% (equity-book)')
  })

  it('falls back to raw message for unknown alert types', () => {
    const raw = 'Some unknown alert format'
    const result = formatAlertMessage(makeAlert({
      type: 'UNKNOWN_TYPE',
      message: raw,
    }))

    expect(result).toBe('WARNING: Some unknown alert format')
  })

  it('falls back to raw message when currentValue is NaN', () => {
    const result = formatAlertMessage(makeAlert({
      currentValue: NaN,
    }))

    expect(result).toContain('Daily PnL Alert:')
  })

  it('rounds large values to nearest dollar', () => {
    const result = formatAlertMessage(makeAlert({
      currentValue: 251954.53464219306,
      threshold: 250000,
    }))

    expect(result).toContain('$251,955')
    expect(result).not.toContain('251954.53')
  })

  it('includes severity prefix for CRITICAL alerts', () => {
    const result = formatAlertMessage(makeAlert({ severity: 'CRITICAL' }))

    expect(result).toMatch(/^CRITICAL:/)
  })

  it('includes severity prefix for WARNING alerts', () => {
    const result = formatAlertMessage(makeAlert({ severity: 'WARNING' }))

    expect(result).toMatch(/^WARNING:/)
  })

  it('handles INFO severity without prefix', () => {
    const result = formatAlertMessage(makeAlert({ severity: 'INFO' }))

    expect(result).not.toMatch(/^INFO:/)
  })
})
