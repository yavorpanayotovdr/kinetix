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
      className={`bg-white dark:bg-surface-800 rounded-lg border border-slate-200 dark:border-surface-700 shadow-sm ${className}`}
      {...rest}
    >
      {header && (
        <div className="border-b border-slate-100 dark:border-surface-700 px-4 py-3 font-semibold text-sm text-slate-700 dark:text-slate-200">
          {header}
        </div>
      )}
      <div className="px-4 py-4">{children}</div>
    </div>
  )
}
