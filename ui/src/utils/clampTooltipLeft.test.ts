import { describe, expect, it } from 'vitest'
import { clampTooltipLeft } from './clampTooltipLeft'

describe('clampTooltipLeft', () => {
  it('centers tooltip when there is enough space', () => {
    expect(clampTooltipLeft(150, 200, 600)).toBe(50)
  })

  it('clamps to the left edge when bar is near the start', () => {
    expect(clampTooltipLeft(10, 200, 600)).toBe(0)
  })

  it('clamps to the right edge when bar is near the end', () => {
    // containerWidth 300, tooltipWidth 200 → max left = 100
    expect(clampTooltipLeft(290, 200, 300)).toBe(100)
  })

  it('returns 0 when tooltip is wider than container', () => {
    expect(clampTooltipLeft(50, 400, 300)).toBe(0)
  })
})
