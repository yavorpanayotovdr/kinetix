import { Component, type ErrorInfo, type ReactNode } from 'react'
import { RefreshCw } from 'lucide-react'

type FallbackRender = (error: Error, reset: () => void) => ReactNode

interface SectionErrorCardProps {
  name: string
  onReload?: () => void
}

export function SectionErrorCard({ name, onReload }: SectionErrorCardProps) {
  return (
    <div
      data-testid={`section-error-${name.toLowerCase().replace(/\s+/g, '-')}`}
      className="rounded-lg border border-red-200 bg-red-50 dark:border-red-900 dark:bg-red-950/20 p-4 flex items-center justify-between gap-4"
      role="alert"
    >
      <p className="text-sm text-red-700 dark:text-red-400">
        <span className="font-medium">{name}</span> encountered an error and could not be displayed.
      </p>
      <button
        data-testid={`section-error-reload-${name.toLowerCase().replace(/\s+/g, '-')}`}
        onClick={onReload ?? (() => window.location.reload())}
        className="flex-shrink-0 inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium text-red-700 dark:text-red-400 border border-red-300 dark:border-red-800 rounded-md hover:bg-red-100 dark:hover:bg-red-900/30 transition-colors"
      >
        <RefreshCw className="h-3.5 w-3.5" />
        Reload
      </button>
    </div>
  )
}

interface Props {
  children: ReactNode
  fallback?: ReactNode | FallbackRender
}

interface State {
  hasError: boolean
  error: Error | null
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('[ErrorBoundary] Uncaught error:', error.message, info.componentStack)
  }

  reset = () => {
    this.setState({ hasError: false, error: null })
  }

  render() {
    if (this.state.hasError && this.state.error) {
      const { fallback } = this.props

      if (fallback !== undefined) {
        if (typeof fallback === 'function') {
          return (fallback as FallbackRender)(this.state.error, this.reset)
        }
        return fallback
      }

      return (
        <div className="min-h-screen flex items-center justify-center bg-surface-50 dark:bg-surface-900" role="alert">
          <div className="max-w-md p-6 bg-white dark:bg-surface-800 rounded-lg shadow-lg text-center">
            <h2 className="text-lg font-semibold text-red-600 dark:text-red-400 mb-2">Something went wrong</h2>
            <p className="text-sm text-gray-600 dark:text-gray-300 mb-4">
              {this.state.error.message || 'An unexpected error occurred.'}
            </p>
            <button
              onClick={() => window.location.reload()}
              className="px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700 transition-colors text-sm font-medium"
            >
              Reload page
            </button>
          </div>
        </div>
      )
    }

    return this.props.children
  }
}
