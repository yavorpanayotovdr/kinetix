import { type InputHTMLAttributes } from 'react'

type InputProps = InputHTMLAttributes<HTMLInputElement>

export function Input({ className = '', ...rest }: InputProps) {
  return (
    <input
      className={`border border-slate-300 rounded-md px-3 py-1.5 text-sm bg-white focus:ring-2 focus:ring-primary-500 focus:border-primary-500 transition-colors ${className}`}
      {...rest}
    />
  )
}
