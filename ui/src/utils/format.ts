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
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
  }).format(Number(amount))
}

export function pnlColorClass(amount: string): string {
  const value = Number(amount)
  if (value > 0) return 'text-green-600'
  if (value < 0) return 'text-red-600'
  return 'text-gray-500'
}
