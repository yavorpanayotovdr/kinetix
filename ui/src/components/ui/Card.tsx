import { type ReactNode } from 'react'

interface CardProps {
  header?: ReactNode
  children: ReactNode
  className?: string
  'data-testid'?: string
}

export function Card({ header, children, className = '', ...rest }: CardProps) {
  return (
    <div
      className={`bg-white rounded-lg border border-slate-200 shadow-sm ${className}`}
      {...rest}
    >
      {header && (
        <div className="border-b border-slate-100 px-4 py-3 font-semibold text-sm text-slate-700">
          {header}
        </div>
      )}
      <div className="px-4 py-4">{children}</div>
    </div>
  )
}
