import { renderHook, act, waitFor } from '@testing-library/react'
import { describe, expect, it, beforeEach, vi } from 'vitest'
import { useHierarchySelector } from './useHierarchySelector'

vi.mock('../api/hierarchy', () => ({
  fetchDivisions: vi.fn(),
  fetchDesks: vi.fn(),
}))

vi.mock('../api/positions', () => ({
  fetchBooks: vi.fn(),
  fetchPositions: vi.fn(),
}))

import { fetchDivisions, fetchDesks } from '../api/hierarchy'
import { fetchBooks } from '../api/positions'

const mockFetchDivisions = vi.mocked(fetchDivisions)
const mockFetchDesks = vi.mocked(fetchDesks)
const mockFetchBooks = vi.mocked(fetchBooks)

const TEST_DIVISIONS = [
  { id: 'div-1', name: 'Equities', deskCount: 2 },
  { id: 'div-2', name: 'Fixed Income', deskCount: 1 },
]

const TEST_DESKS = [
  { id: 'desk-1', name: 'EU Equities', divisionId: 'div-1', bookCount: 2 },
  { id: 'desk-2', name: 'US Equities', divisionId: 'div-1', bookCount: 1 },
]

const TEST_BOOKS = [
  { bookId: 'book-1' },
  { bookId: 'book-2' },
  { bookId: 'book-3' },
]

describe('useHierarchySelector', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockFetchDivisions.mockResolvedValue(TEST_DIVISIONS)
    mockFetchBooks.mockResolvedValue(TEST_BOOKS)
    mockFetchDesks.mockResolvedValue(TEST_DESKS)
  })

  it('starts at firm level with no selection', async () => {
    const { result } = renderHook(() => useHierarchySelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.selection.level).toBe('firm')
    expect(result.current.selection.divisionId).toBeNull()
    expect(result.current.selection.deskId).toBeNull()
    expect(result.current.selection.bookId).toBeNull()
  })

  it('loads divisions on mount', async () => {
    const { result } = renderHook(() => useHierarchySelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.divisions).toEqual(TEST_DIVISIONS)
  })

  it('loads books on mount', async () => {
    const { result } = renderHook(() => useHierarchySelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.books).toEqual(TEST_BOOKS)
  })

  it('breadcrumb shows only Firm at firm level', async () => {
    const { result } = renderHook(() => useHierarchySelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.breadcrumb).toHaveLength(1)
    expect(result.current.breadcrumb[0]).toEqual({
      level: 'firm',
      id: null,
      label: 'Firm',
    })
  })

  it('breadcrumb shows Firm > Division when division selected', async () => {
    const { result } = renderHook(() => useHierarchySelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.setSelection({
        level: 'division',
        divisionId: 'div-1',
        deskId: null,
        bookId: null,
      })
    })

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.breadcrumb).toHaveLength(2)
    expect(result.current.breadcrumb[1]).toEqual({
      level: 'division',
      id: 'div-1',
      label: 'Equities',
    })
  })

  it('loads desks when division is selected', async () => {
    const { result } = renderHook(() => useHierarchySelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    await act(async () => {
      result.current.setSelection({
        level: 'division',
        divisionId: 'div-1',
        deskId: null,
        bookId: null,
      })
    })

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchDesks).toHaveBeenCalledWith('div-1')
    expect(result.current.desks).toEqual(TEST_DESKS)
  })

  it('breadcrumb shows Firm > Division > Desk when desk selected', async () => {
    const { result } = renderHook(() => useHierarchySelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.setSelection({
        level: 'division',
        divisionId: 'div-1',
        deskId: null,
        bookId: null,
      })
    })

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.setSelection({
        level: 'desk',
        divisionId: 'div-1',
        deskId: 'desk-1',
        bookId: null,
      })
    })

    expect(result.current.breadcrumb).toHaveLength(3)
    expect(result.current.breadcrumb[2]).toEqual({
      level: 'desk',
      id: 'desk-1',
      label: 'EU Equities',
    })
  })

  it('effectiveBookId is null at firm level', async () => {
    const { result } = renderHook(() => useHierarchySelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.effectiveBookId).toBeNull()
  })

  it('effectiveBookId is set when book level selected', async () => {
    const { result } = renderHook(() => useHierarchySelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.setSelection({
        level: 'book',
        divisionId: 'div-1',
        deskId: 'desk-1',
        bookId: 'book-1',
      })
    })

    expect(result.current.effectiveBookId).toBe('book-1')
  })

  it('effectiveBookIds contains all books at firm level', async () => {
    const { result } = renderHook(() => useHierarchySelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.effectiveBookIds).toEqual(['book-1', 'book-2', 'book-3'])
  })

  it('effectiveBookIds contains only selected book at book level', async () => {
    const { result } = renderHook(() => useHierarchySelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    await act(async () => {
      result.current.setSelection({
        level: 'book',
        divisionId: 'div-1',
        deskId: 'desk-1',
        bookId: 'book-2',
      })
    })

    expect(result.current.effectiveBookIds).toEqual(['book-2'])
  })

  it('sets error state when fetchDivisions fails', async () => {
    mockFetchDivisions.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useHierarchySelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.error).toBe('Network error')
  })

  it('breadcrumb shows full trail at book level', async () => {
    const { result } = renderHook(() => useHierarchySelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.setSelection({
        level: 'division',
        divisionId: 'div-1',
        deskId: null,
        bookId: null,
      })
    })

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.setSelection({
        level: 'desk',
        divisionId: 'div-1',
        deskId: 'desk-1',
        bookId: null,
      })
    })

    act(() => {
      result.current.setSelection({
        level: 'book',
        divisionId: 'div-1',
        deskId: 'desk-1',
        bookId: 'book-1',
      })
    })

    expect(result.current.breadcrumb).toHaveLength(4)
    expect(result.current.breadcrumb.map((b) => b.label)).toEqual([
      'Firm', 'Equities', 'EU Equities', 'book-1',
    ])
  })
})
