import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ErrorBoundary } from './ErrorBoundary'

function ThrowingChild({ shouldThrow }: { shouldThrow: boolean }) {
  if (shouldThrow) throw new Error('Test error')
  return <div>Child rendered</div>
}

describe('ErrorBoundary', () => {
  it('renders children when no error occurs', () => {
    render(
      <ErrorBoundary>
        <ThrowingChild shouldThrow={false} />
      </ErrorBoundary>,
    )

    expect(screen.getByText('Child rendered')).toBeDefined()
  })

  it('renders error UI when a child throws', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})

    render(
      <ErrorBoundary>
        <ThrowingChild shouldThrow={true} />
      </ErrorBoundary>,
    )

    expect(screen.getByText('Something went wrong')).toBeDefined()
    expect(screen.getByText('Test error')).toBeDefined()
    expect(screen.getByRole('button', { name: 'Reload page' })).toBeDefined()

    vi.restoreAllMocks()
  })

  it('logs the error to console', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    render(
      <ErrorBoundary>
        <ThrowingChild shouldThrow={true} />
      </ErrorBoundary>,
    )

    expect(consoleSpy).toHaveBeenCalled()
    vi.restoreAllMocks()
  })

  describe('fallback prop', () => {
    it('renders a ReactNode fallback instead of the default full-screen error', () => {
      vi.spyOn(console, 'error').mockImplementation(() => {})

      render(
        <ErrorBoundary fallback={<div data-testid="custom-fallback">Section error</div>}>
          <ThrowingChild shouldThrow={true} />
        </ErrorBoundary>,
      )

      expect(screen.getByTestId('custom-fallback')).toBeDefined()
      expect(screen.getByText('Section error')).toBeDefined()
      expect(screen.queryByText('Something went wrong')).toBeNull()

      vi.restoreAllMocks()
    })

    it('renders a render-function fallback with the error and reset handler', () => {
      vi.spyOn(console, 'error').mockImplementation(() => {})

      render(
        <ErrorBoundary
          fallback={(error, reset) => (
            <div>
              <span data-testid="error-msg">{error.message}</span>
              <button data-testid="reset-btn" onClick={reset}>Reset</button>
            </div>
          )}
        >
          <ThrowingChild shouldThrow={true} />
        </ErrorBoundary>,
      )

      expect(screen.getByTestId('error-msg')).toHaveTextContent('Test error')
      expect(screen.getByTestId('reset-btn')).toBeDefined()

      vi.restoreAllMocks()
    })

    it('exposes a reset handler via the render function that can be invoked', () => {
      vi.spyOn(console, 'error').mockImplementation(() => {})

      const onReset = vi.fn()

      render(
        <ErrorBoundary
          fallback={(_error, reset) => (
            <button
              data-testid="reset-btn"
              onClick={() => { reset(); onReset() }}
            >
              Reset
            </button>
          )}
        >
          <ThrowingChild shouldThrow={true} />
        </ErrorBoundary>,
      )

      expect(screen.getByTestId('reset-btn')).toBeDefined()

      fireEvent.click(screen.getByTestId('reset-btn'))

      // reset() was called without throwing
      expect(onReset).toHaveBeenCalledTimes(1)

      vi.restoreAllMocks()
    })

    it('still renders default full-screen error when no fallback prop is provided', () => {
      vi.spyOn(console, 'error').mockImplementation(() => {})

      render(
        <ErrorBoundary>
          <ThrowingChild shouldThrow={true} />
        </ErrorBoundary>,
      )

      expect(screen.getByRole('alert')).toBeDefined()
      expect(screen.getByText('Something went wrong')).toBeDefined()

      vi.restoreAllMocks()
    })
  })
})
