const KNOWN_CURRENCIES: Record<string, string> = {
  USD: 'en-US',
  EUR: 'en-IE',
  GBP: 'en-GB',
  JPY: 'ja-JP',
}

export function formatMoney(amount: string, currency: string): string {
  const locale = KNOWN_CURRENCIES[currency]
  if (!locale) {
    return `${amount} ${currency}`
  }
  const rounded = Math.round(Number(amount) * 100) / 100
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
  }).format(rounded)
}

export function formatQuantity(amount: string): string {
  const num = Number(amount)
  if (!Number.isFinite(num)) return amount
  // Up to 2 decimal places, strip trailing zeros
  const fixed = num.toFixed(2)
  return fixed.replace(/\.?0+$/, '')
}

export function formatRelativeTime(isoString: string): string {
  const now = Date.now()
  const then = new Date(isoString).getTime()
  const diffMs = now - then

  if (diffMs < 0) return 'just now'

  const seconds = Math.floor(diffMs / 1000)
  if (seconds < 60) return 'just now'

  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`

  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`

  const days = Math.floor(hours / 24)
  return `${days}d ago`
}

export function formatTimestamp(isoString: string): string {
  const date = new Date(isoString)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function pnlColorClass(amount: string): string {
  const value = Number(amount)
  if (value > 0) return 'text-green-600'
  if (value < 0) return 'text-red-600'
  return 'text-gray-500'
}
