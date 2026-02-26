export function formatCompactCurrency(value: number): string {
  const abs = Math.abs(value)
  const sign = value < 0 ? '-' : ''

  let formatted: string
  if (abs >= 1_000_000_000) {
    formatted = `${(abs / 1_000_000_000).toFixed(1).replace(/\.0$/, '')}B`
  } else if (abs >= 1_000_000) {
    formatted = `${(abs / 1_000_000).toFixed(1).replace(/\.0$/, '')}M`
  } else if (abs >= 1_000) {
    formatted = `${(abs / 1_000).toFixed(1).replace(/\.0$/, '')}K`
  } else {
    formatted = `${Math.round(abs)}`
  }

  return `${sign}$${formatted}`
}
