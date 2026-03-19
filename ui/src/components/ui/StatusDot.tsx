interface StatusDotProps {
  status: 'up' | 'down'
  pulse?: boolean
  'data-testid'?: string
}

export function StatusDot({ status, pulse, ...rest }: StatusDotProps) {
  const color = status === 'up' ? 'bg-green-500' : 'bg-red-500'
  const border = status === 'up' ? 'border-2 border-green-600' : 'border-2 border-dashed border-red-600'
  const label = status === 'up' ? 'Connected' : 'Disconnected'
  return (
    <span
      role="status"
      aria-label={label}
      className={`inline-block h-3 w-3 rounded-full ${color} ${border} ${pulse ? 'animate-pulse-dot' : ''}`}
      {...rest}
    />
  )
}
