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

  it('renders warning when status is null (API not yet responded or error)', () => {
    render(
      <SodBaselineIndicator {...defaultProps} status={null} />,
    )

    expect(screen.getByTestId('sod-baseline-warning')).toBeInTheDocument()
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
          sourceJobId: null,
          calculationType: null,
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
          sourceJobId: null,
          calculationType: null,
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
          sourceJobId: null,
          calculationType: null,
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
          sourceJobId: null,
          calculationType: null,
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
          sourceJobId: null,
          calculationType: null,
        }}
        onResetBaseline={onResetBaseline}
      />,
    )

    fireEvent.click(screen.getByTestId('sod-reset-button'))

    expect(onResetBaseline).toHaveBeenCalledOnce()
  })

  it('displays job metadata when available', () => {
    render(
      <SodBaselineIndicator
        {...defaultProps}
        status={{
          exists: true,
          baselineDate: '2025-01-15',
          snapshotType: 'MANUAL',
          createdAt: '2025-01-15T08:00:00Z',
          sourceJobId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
          calculationType: 'PARAMETRIC',
        }}
      />,
    )

    expect(screen.getByTestId('sod-calculation-type')).toHaveTextContent('PARAMETRIC')
    expect(screen.getByTestId('sod-source-job-id')).toHaveTextContent('aaaaaaaa')
  })

  it('renders Pick from History button when callback is provided', () => {
    const onPickFromHistory = vi.fn()
    render(
      <SodBaselineIndicator
        {...defaultProps}
        status={{
          exists: false,
          baselineDate: null,
          snapshotType: null,
          createdAt: null,
          sourceJobId: null,
          calculationType: null,
        }}
        onPickFromHistory={onPickFromHistory}
      />,
    )

    expect(screen.getByTestId('sod-pick-history-button')).toBeInTheDocument()
    fireEvent.click(screen.getByTestId('sod-pick-history-button'))
    expect(onPickFromHistory).toHaveBeenCalledOnce()
  })

  it('does not render Pick from History button when callback is not provided', () => {
    render(
      <SodBaselineIndicator
        {...defaultProps}
        status={{
          exists: false,
          baselineDate: null,
          snapshotType: null,
          createdAt: null,
          sourceJobId: null,
          calculationType: null,
        }}
      />,
    )

    expect(screen.queryByTestId('sod-pick-history-button')).not.toBeInTheDocument()
  })
})
