import { Loader2 } from 'lucide-react'

const sizes = {
  sm: 'h-4 w-4',
  md: 'h-6 w-6',
  lg: 'h-8 w-8',
} as const

interface SpinnerProps {
  size?: keyof typeof sizes
}

export function Spinner({ size = 'md' }: SpinnerProps) {
  return <Loader2 className={`animate-spin text-primary-500 ${sizes[size]}`} />
}
