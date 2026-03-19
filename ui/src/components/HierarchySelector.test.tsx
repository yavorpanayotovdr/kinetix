import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { HierarchySelector } from './HierarchySelector'
import type { UseHierarchySelectorResult } from '../hooks/useHierarchySelector'

function makeHierarchy(overrides: Partial<UseHierarchySelectorResult> = {}): UseHierarchySelectorResult {
  return {
    selection: { level: 'firm', divisionId: null, deskId: null, bookId: null },
    setSelection: vi.fn(),
    breadcrumb: [{ level: 'firm', id: null, label: 'Firm' }],
    effectiveBookId: null,
    effectiveBookIds: ['book-1', 'book-2'],
    divisions: [
      { id: 'div-1', name: 'Equities', deskCount: 2 },
      { id: 'div-2', name: 'Fixed Income', deskCount: 1 },
    ],
    desks: [],
    books: [{ bookId: 'book-1' }, { bookId: 'book-2' }],
    loading: false,
    error: null,
    ...overrides,
  }
}

describe('HierarchySelector', () => {
  it('renders toggle button', () => {
    render(<HierarchySelector hierarchy={makeHierarchy()} />)
    expect(screen.getByTestId('hierarchy-selector-toggle')).toBeInTheDocument()
  })

  it('panel is not shown initially', () => {
    render(<HierarchySelector hierarchy={makeHierarchy()} />)
    expect(screen.queryByTestId('hierarchy-panel')).not.toBeInTheDocument()
  })

  it('opens panel when toggle clicked', () => {
    render(<HierarchySelector hierarchy={makeHierarchy()} />)
    fireEvent.click(screen.getByTestId('hierarchy-selector-toggle'))
    expect(screen.getByTestId('hierarchy-panel')).toBeInTheDocument()
  })

  it('shows Firm option at firm level', () => {
    render(<HierarchySelector hierarchy={makeHierarchy()} />)
    fireEvent.click(screen.getByTestId('hierarchy-selector-toggle'))
    expect(screen.getByTestId('hierarchy-firm-option')).toBeInTheDocument()
  })

  it('shows divisions at firm level', () => {
    render(<HierarchySelector hierarchy={makeHierarchy()} />)
    fireEvent.click(screen.getByTestId('hierarchy-selector-toggle'))
    expect(screen.getByTestId('hierarchy-division-div-1')).toBeInTheDocument()
    expect(screen.getByTestId('hierarchy-division-div-2')).toBeInTheDocument()
  })

  it('calls setSelection with division when division clicked', () => {
    const setSelection = vi.fn()
    render(<HierarchySelector hierarchy={makeHierarchy({ setSelection })} />)
    fireEvent.click(screen.getByTestId('hierarchy-selector-toggle'))
    fireEvent.click(screen.getByTestId('hierarchy-division-div-1'))

    expect(setSelection).toHaveBeenCalledWith({
      level: 'division',
      divisionId: 'div-1',
      deskId: null,
      bookId: null,
    })
  })

  it('shows desks at division level', () => {
    const hierarchy = makeHierarchy({
      selection: { level: 'division', divisionId: 'div-1', deskId: null, bookId: null },
      desks: [
        { id: 'desk-1', name: 'EU Equities', divisionId: 'div-1', bookCount: 2 },
        { id: 'desk-2', name: 'US Equities', divisionId: 'div-1', bookCount: 1 },
      ],
    })
    render(<HierarchySelector hierarchy={hierarchy} />)
    fireEvent.click(screen.getByTestId('hierarchy-selector-toggle'))
    expect(screen.getByTestId('hierarchy-desk-desk-1')).toBeInTheDocument()
    expect(screen.getByTestId('hierarchy-desk-desk-2')).toBeInTheDocument()
  })

  it('calls setSelection with desk when desk clicked', () => {
    const setSelection = vi.fn()
    const hierarchy = makeHierarchy({
      setSelection,
      selection: { level: 'division', divisionId: 'div-1', deskId: null, bookId: null },
      desks: [{ id: 'desk-1', name: 'EU Equities', divisionId: 'div-1', bookCount: 2 }],
    })
    render(<HierarchySelector hierarchy={hierarchy} />)
    fireEvent.click(screen.getByTestId('hierarchy-selector-toggle'))
    fireEvent.click(screen.getByTestId('hierarchy-desk-desk-1'))

    expect(setSelection).toHaveBeenCalledWith({
      level: 'desk',
      divisionId: 'div-1',
      deskId: 'desk-1',
      bookId: null,
    })
  })

  it('shows books at desk level', () => {
    const hierarchy = makeHierarchy({
      selection: { level: 'desk', divisionId: 'div-1', deskId: 'desk-1', bookId: null },
    })
    render(<HierarchySelector hierarchy={hierarchy} />)
    fireEvent.click(screen.getByTestId('hierarchy-selector-toggle'))
    expect(screen.getByTestId('hierarchy-book-book-1')).toBeInTheDocument()
    expect(screen.getByTestId('hierarchy-book-book-2')).toBeInTheDocument()
  })

  it('calls setSelection with book and closes panel when book clicked', async () => {
    const setSelection = vi.fn()
    const hierarchy = makeHierarchy({
      setSelection,
      selection: { level: 'desk', divisionId: 'div-1', deskId: 'desk-1', bookId: null },
    })
    render(<HierarchySelector hierarchy={hierarchy} />)
    fireEvent.click(screen.getByTestId('hierarchy-selector-toggle'))
    fireEvent.click(screen.getByTestId('hierarchy-book-book-1'))

    expect(setSelection).toHaveBeenCalledWith({
      level: 'book',
      divisionId: 'div-1',
      deskId: 'desk-1',
      bookId: 'book-1',
    })

    await waitFor(() => {
      expect(screen.queryByTestId('hierarchy-panel')).not.toBeInTheDocument()
    })
  })

  it('shows loading state', () => {
    render(<HierarchySelector hierarchy={makeHierarchy({ loading: true })} />)
    fireEvent.click(screen.getByTestId('hierarchy-selector-toggle'))
    expect(screen.getByText('Loading...')).toBeInTheDocument()
  })

  it('displays breadcrumb text in toggle button', () => {
    const hierarchy = makeHierarchy({
      breadcrumb: [
        { level: 'firm', id: null, label: 'Firm' },
        { level: 'division', id: 'div-1', label: 'Equities' },
      ],
      selection: { level: 'division', divisionId: 'div-1', deskId: null, bookId: null },
    })
    render(<HierarchySelector hierarchy={hierarchy} />)
    // Toggle button shows breadcrumb as plain text (no nested buttons)
    const toggle = screen.getByTestId('hierarchy-selector-toggle')
    expect(toggle).toHaveTextContent('Firm / Equities')
  })

  it('shows clickable breadcrumb inside panel when navigated below firm level', () => {
    const hierarchy = makeHierarchy({
      breadcrumb: [
        { level: 'firm', id: null, label: 'Firm' },
        { level: 'division', id: 'div-1', label: 'Equities' },
      ],
      selection: { level: 'division', divisionId: 'div-1', deskId: null, bookId: null },
    })
    render(<HierarchySelector hierarchy={hierarchy} />)
    fireEvent.click(screen.getByTestId('hierarchy-selector-toggle'))
    expect(screen.getByTestId('breadcrumb-firm')).toBeInTheDocument()
    expect(screen.getByTestId('breadcrumb-division')).toHaveTextContent('Equities')
  })
})
