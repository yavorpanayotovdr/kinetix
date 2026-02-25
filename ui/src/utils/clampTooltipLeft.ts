export function clampTooltipLeft(barCenterX: number, tooltipWidth: number, containerWidth: number): number {
  const idealLeft = barCenterX - tooltipWidth / 2
  const maxLeft = Math.max(0, containerWidth - tooltipWidth)
  return Math.max(0, Math.min(idealLeft, maxLeft))
}
