export function changeColorClass(value: number): string {
  // For VaR/ES: negative change (reduction) is good = green, positive (increase) is bad = red
  if (value < 0) return 'text-green-600 dark:text-green-400'
  if (value > 0) return 'text-red-600 dark:text-red-400'
  return 'text-slate-500 dark:text-slate-400'
}
