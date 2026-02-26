import { describe, expect, it } from 'vitest'
import { formatCompactCurrency } from './formatCompactCurrency'

describe('formatCompactCurrency', () => {
  it('formats billions with one decimal', () => {
    expect(formatCompactCurrency(1_500_000_000)).toBe('$1.5B')
  })

  it('strips trailing .0 for round billions', () => {
    expect(formatCompactCurrency(2_000_000_000)).toBe('$2B')
  })

  it('formats millions with one decimal', () => {
    expect(formatCompactCurrency(1_200_000)).toBe('$1.2M')
  })

  it('strips trailing .0 for round millions', () => {
    expect(formatCompactCurrency(5_000_000)).toBe('$5M')
  })

  it('formats thousands with one decimal', () => {
    expect(formatCompactCurrency(850_000)).toBe('$850K')
  })

  it('strips trailing .0 for round thousands', () => {
    expect(formatCompactCurrency(3_000)).toBe('$3K')
  })

  it('formats values below 1000 as plain dollars', () => {
    expect(formatCompactCurrency(500)).toBe('$500')
  })

  it('formats zero', () => {
    expect(formatCompactCurrency(0)).toBe('$0')
  })

  it('formats negative billions', () => {
    expect(formatCompactCurrency(-1_500_000_000)).toBe('-$1.5B')
  })

  it('formats negative millions', () => {
    expect(formatCompactCurrency(-1_200_000)).toBe('-$1.2M')
  })

  it('formats negative thousands', () => {
    expect(formatCompactCurrency(-850_000)).toBe('-$850K')
  })

  it('formats negative values below 1000', () => {
    expect(formatCompactCurrency(-42)).toBe('-$42')
  })

  it('formats fractional thousands', () => {
    expect(formatCompactCurrency(1_500)).toBe('$1.5K')
  })

  it('formats values just at the boundary of 1M', () => {
    expect(formatCompactCurrency(1_000_000)).toBe('$1M')
  })

  it('formats values just at the boundary of 1B', () => {
    expect(formatCompactCurrency(1_000_000_000)).toBe('$1B')
  })
})
