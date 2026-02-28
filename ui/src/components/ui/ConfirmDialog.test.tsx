import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ConfirmDialog } from './ConfirmDialog'

describe('ConfirmDialog', () => {
  const defaultProps = {
    open: true,
    title: 'Reset Baseline',
    message: 'Are you sure you want to reset the SOD baseline?',
    onConfirm: vi.fn(),
    onCancel: vi.fn(),
  }

  it('renders title and message when open', () => {
    render(<ConfirmDialog {...defaultProps} />)

    expect(screen.getByText('Reset Baseline')).toBeInTheDocument()
    expect(
      screen.getByText('Are you sure you want to reset the SOD baseline?'),
    ).toBeInTheDocument()
  })

  it('does not render when closed', () => {
    render(<ConfirmDialog {...defaultProps} open={false} />)

    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument()
  })

  it('calls onConfirm when confirm button is clicked', () => {
    const onConfirm = vi.fn()
    render(<ConfirmDialog {...defaultProps} onConfirm={onConfirm} />)

    fireEvent.click(screen.getByTestId('confirm-dialog-confirm'))

    expect(onConfirm).toHaveBeenCalledOnce()
  })

  it('calls onCancel when cancel button is clicked', () => {
    const onCancel = vi.fn()
    render(<ConfirmDialog {...defaultProps} onCancel={onCancel} />)

    fireEvent.click(screen.getByTestId('confirm-dialog-cancel'))

    expect(onCancel).toHaveBeenCalledOnce()
  })

  it('calls onCancel when Escape key is pressed', () => {
    const onCancel = vi.fn()
    render(<ConfirmDialog {...defaultProps} onCancel={onCancel} />)

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(onCancel).toHaveBeenCalledOnce()
  })

  it('calls onCancel when overlay is clicked', () => {
    const onCancel = vi.fn()
    render(<ConfirmDialog {...defaultProps} onCancel={onCancel} />)

    fireEvent.click(screen.getByTestId('confirm-dialog-overlay'))

    expect(onCancel).toHaveBeenCalledOnce()
  })

  it('does not call onCancel when dialog body is clicked', () => {
    const onCancel = vi.fn()
    render(<ConfirmDialog {...defaultProps} onCancel={onCancel} />)

    fireEvent.click(screen.getByTestId('confirm-dialog'))

    expect(onCancel).not.toHaveBeenCalled()
  })

  it('shows custom button labels', () => {
    render(
      <ConfirmDialog
        {...defaultProps}
        confirmLabel="Yes, Reset"
        cancelLabel="No, Keep"
      />,
    )

    expect(screen.getByText('Yes, Reset')).toBeInTheDocument()
    expect(screen.getByText('No, Keep')).toBeInTheDocument()
  })

  it('renders ReactNode as message', () => {
    render(
      <ConfirmDialog
        {...defaultProps}
        message={
          <div>
            <p>Plain text</p>
            <span data-testid="custom-content">Extra info</span>
          </div>
        }
      />,
    )

    expect(screen.getByText('Plain text')).toBeInTheDocument()
    expect(screen.getByTestId('custom-content')).toHaveTextContent('Extra info')
  })
})
