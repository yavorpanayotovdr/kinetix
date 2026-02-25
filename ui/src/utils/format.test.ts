import { describe, expect, it, vi, afterEach } from 'vitest'
import { formatMoney, formatQuantity, formatRelativeTime, formatTimestamp, pnlColorClass } from './format'

describe('formatMoney', () => {
  it('formats USD with dollar sign and commas', () => {
    expect(formatMoney('1500.00', 'USD')).toBe('$1,500.00')
  })

  it('formats EUR with euro sign', () => {
    expect(formatMoney('2500.50', 'EUR')).toBe('\u20ac2,500.50')
  })

  it('formats negative amounts', () => {
    expect(formatMoney('-1234.56', 'USD')).toBe('-$1,234.56')
  })

  it('formats large numbers with thousands separators', () => {
    expect(formatMoney('1234567.89', 'USD')).toBe('$1,234,567.89')
  })

  it('falls back to amount + currency code for unknown currencies', () => {
    expect(formatMoney('100.00', 'XYZ')).toBe('100.00 XYZ')
  })

  it('rounds amounts with excessive decimal places', () => {
    expect(formatMoney('28387.500000000000000000000000', 'USD')).toBe('$28,387.50')
  })

  it('rounds to 2 decimal places', () => {
    expect(formatMoney('150.999', 'USD')).toBe('$151.00')
  })
})

describe('pnlColorClass', () => {
  it('returns green for positive amounts', () => {
    expect(pnlColorClass('150.00')).toBe('text-green-600')
  })

  it('returns red for negative amounts', () => {
    expect(pnlColorClass('-50.00')).toBe('text-red-600')
  })

  it('returns gray for zero', () => {
    expect(pnlColorClass('0.00')).toBe('text-gray-500')
  })
})

describe('formatQuantity', () => {
  it('strips trailing zeros from integer', () => {
    expect(formatQuantity('150.000000000000')).toBe('150')
  })

  it('preserves meaningful decimals', () => {
    expect(formatQuantity('0.500000')).toBe('0.5')
  })

  it('handles two decimal places', () => {
    expect(formatQuantity('10.25')).toBe('10.25')
  })

  it('rounds to 2 decimal places', () => {
    expect(formatQuantity('1.999')).toBe('2')
  })

  it('handles plain integers', () => {
    expect(formatQuantity('100')).toBe('100')
  })

  it('handles negative values', () => {
    expect(formatQuantity('-5.500000')).toBe('-5.5')
  })
})

describe('formatRelativeTime', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('returns "just now" for recent times', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2025-01-15T10:00:30Z'))
    expect(formatRelativeTime('2025-01-15T10:00:00Z')).toBe('just now')
  })

  it('returns minutes ago', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2025-01-15T10:05:00Z'))
    expect(formatRelativeTime('2025-01-15T10:00:00Z')).toBe('5m ago')
  })

  it('returns hours ago', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2025-01-15T12:00:00Z'))
    expect(formatRelativeTime('2025-01-15T10:00:00Z')).toBe('2h ago')
  })

  it('returns days ago', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2025-01-17T10:00:00Z'))
    expect(formatRelativeTime('2025-01-15T10:00:00Z')).toBe('2d ago')
  })

  it('returns "just now" for future times', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2025-01-15T09:00:00Z'))
    expect(formatRelativeTime('2025-01-15T10:00:00Z')).toBe('just now')
  })
})

describe('formatTimestamp', () => {
  it('formats an ISO string as YYYY-MM-DD HH:mm:ss in local time', () => {
    const date = new Date(2025, 0, 15, 10, 5, 30)
    expect(formatTimestamp(date.toISOString())).toBe('2025-01-15 10:05:30')
  })

  it('pads single-digit months, days, hours, minutes, and seconds', () => {
    const date = new Date(2025, 2, 3, 4, 5, 6)
    expect(formatTimestamp(date.toISOString())).toBe('2025-03-03 04:05:06')
  })
})
