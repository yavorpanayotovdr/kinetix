import { type ReactNode } from 'react'

const variants = {
  critical: 'bg-red-100 text-red-800',
  warning: 'bg-yellow-100 text-yellow-800',
  info: 'bg-blue-100 text-blue-800',
  success: 'bg-green-100 text-green-800',
  neutral: 'bg-slate-100 text-slate-700',
} as const

interface BadgeProps {
  variant?: keyof typeof variants
  children: ReactNode
  'data-testid'?: string
}

export function Badge({ variant = 'neutral', children, ...rest }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${variants[variant]}`}
      {...rest}
    >
      {children}
    </span>
  )
}
