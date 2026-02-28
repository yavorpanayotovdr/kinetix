import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { SodBaselineIndicator } from './SodBaselineIndicator'

describe('SodBaselineIndicator', () => {
  const defaultProps = {
    status: null,
    loading: false,
    creating: false,
    resetting: false,
    onCreateSnapshot: vi.fn(),
    onResetBaseline: vi.fn(),
  }

  it('renders nothing when loading', () => {
    const { container } = render(
      <SodBaselineIndicator {...defaultProps} loading={true} />,
    )

    expect(container.firstChild).toBeNull()
  })

  it('renders nothing when status is null', () => {
    const { container } = render(
      <SodBaselineIndicator {...defaultProps} status={null} />,
    )

    expect(container.firstChild).toBeNull()
  })

  it('renders warning when no baseline exists', () => {
    render(
      <SodBaselineIndicator
        {...defaultProps}
        status={{
          exists: false,
          baselineDate: null,
          snapshotType: null,
          createdAt: null,
        }}
      />,
    )

    expect(screen.getByTestId('sod-baseline-warning')).toBeInTheDocument()
    expect(screen.getByText('No SOD baseline for today')).toBeInTheDocument()
    expect(screen.getByTestId('sod-create-button')).toBeInTheDocument()
  })

  it('renders active indicator when baseline exists', () => {
    render(
      <SodBaselineIndicator
        {...defaultProps}
        status={{
          exists: true,
          baselineDate: '2025-01-15',
          snapshotType: 'MANUAL',
          createdAt: '2025-01-15T08:00:00Z',
        }}
      />,
    )

    expect(screen.getByTestId('sod-baseline-active')).toBeInTheDocument()
    expect(screen.getByText('SOD Baseline Active')).toBeInTheDocument()
    expect(screen.getByText('Manual')).toBeInTheDocument()
    expect(screen.getByTestId('sod-reset-button')).toBeInTheDocument()
  })

  it('shows Auto for AUTO snapshot type', () => {
    render(
      <SodBaselineIndicator
        {...defaultProps}
        status={{
          exists: true,
          baselineDate: '2025-01-15',
          snapshotType: 'AUTO',
          createdAt: '2025-01-15T06:00:00Z',
        }}
      />,
    )

    expect(screen.getByText('Auto')).toBeInTheDocument()
  })

  it('calls onCreateSnapshot when Set as SOD Baseline button is clicked', () => {
    const onCreateSnapshot = vi.fn()
    render(
      <SodBaselineIndicator
        {...defaultProps}
        status={{
          exists: false,
          baselineDate: null,
          snapshotType: null,
          createdAt: null,
        }}
        onCreateSnapshot={onCreateSnapshot}
      />,
    )

    fireEvent.click(screen.getByTestId('sod-create-button'))

    expect(onCreateSnapshot).toHaveBeenCalledOnce()
  })

  it('calls onResetBaseline when Reset SOD Baseline button is clicked', () => {
    const onResetBaseline = vi.fn()
    render(
      <SodBaselineIndicator
        {...defaultProps}
        status={{
          exists: true,
          baselineDate: '2025-01-15',
          snapshotType: 'MANUAL',
          createdAt: '2025-01-15T08:00:00Z',
        }}
        onResetBaseline={onResetBaseline}
      />,
    )

    fireEvent.click(screen.getByTestId('sod-reset-button'))

    expect(onResetBaseline).toHaveBeenCalledOnce()
  })
})
