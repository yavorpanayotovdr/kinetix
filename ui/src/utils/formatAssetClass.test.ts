import { describe, expect, it } from 'vitest'
import { formatAssetClassLabel } from './formatAssetClass'

describe('formatAssetClassLabel', () => {
  it('converts SCREAMING_SNAKE_CASE to Title Case', () => {
    expect(formatAssetClassLabel('FIXED_INCOME')).toBe('Fixed Income')
  })

  it('preserves FX as an abbreviation', () => {
    expect(formatAssetClassLabel('FX')).toBe('FX')
  })

  it('preserves ETF as an abbreviation', () => {
    expect(formatAssetClassLabel('ETF')).toBe('ETF')
  })

  it('preserves CDS as an abbreviation', () => {
    expect(formatAssetClassLabel('CDS')).toBe('CDS')
  })

  it('converts single-word enums to Title Case', () => {
    expect(formatAssetClassLabel('EQUITY')).toBe('Equity')
  })

  it('converts COMMODITY to Title Case', () => {
    expect(formatAssetClassLabel('COMMODITY')).toBe('Commodity')
  })

  it('handles already formatted strings gracefully', () => {
    expect(formatAssetClassLabel('Equity')).toBe('Equity')
  })
})
