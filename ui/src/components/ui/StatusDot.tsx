interface StatusDotProps {
  status: 'up' | 'down'
  pulse?: boolean
  'data-testid'?: string
}

export function StatusDot({ status, pulse, ...rest }: StatusDotProps) {
  const color = status === 'up' ? 'bg-green-500' : 'bg-red-500'
  return (
    <span
      className={`inline-block h-3 w-3 rounded-full ${color} ${pulse ? 'animate-pulse-dot' : ''}`}
      {...rest}
    />
  )
}
