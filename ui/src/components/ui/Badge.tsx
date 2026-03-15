import { type ReactNode } from 'react'

const variants = {
  critical: 'bg-red-100 text-red-800',
  warning: 'bg-yellow-100 text-yellow-800',
  info: 'bg-blue-100 text-blue-800',
  success: 'bg-green-100 text-green-800',
  neutral: 'bg-slate-100 text-slate-700',
  sod: 'bg-sky-100 text-sky-800',
  eod: 'bg-amber-100 text-amber-800 ring-1 ring-amber-300',
  preclose: 'bg-purple-100 text-purple-800 ring-1 ring-purple-300',
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
