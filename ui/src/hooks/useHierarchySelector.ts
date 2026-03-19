import { useCallback, useEffect, useState } from 'react'
import { fetchDivisions, fetchDesks } from '../api/hierarchy'
import { fetchBooks } from '../api/positions'
import type { DivisionDto, DeskDto, BookDto } from '../types'

export type SelectionLevel = 'firm' | 'division' | 'desk' | 'book'

export interface HierarchySelection {
  level: SelectionLevel
  divisionId: string | null
  deskId: string | null
  bookId: string | null
}

export interface BreadcrumbItem {
  level: SelectionLevel
  id: string | null
  label: string
}

export interface UseHierarchySelectorResult {
  selection: HierarchySelection
  setSelection: (selection: HierarchySelection) => void
  breadcrumb: BreadcrumbItem[]
  effectiveBookId: string | null
  effectiveBookIds: string[]
  divisions: DivisionDto[]
  desks: DeskDto[]
  books: BookDto[]
  loading: boolean
  error: string | null
}

export function useHierarchySelector(): UseHierarchySelectorResult {
  const [selection, setSelectionState] = useState<HierarchySelection>({
    level: 'firm',
    divisionId: null,
    deskId: null,
    bookId: null,
  })

  const [divisions, setDivisions] = useState<DivisionDto[]>([])
  const [desks, setDesks] = useState<DeskDto[]>([])
  const [books, setBooks] = useState<BookDto[]>([])
  const [allBooks, setAllBooks] = useState<BookDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Load divisions on mount
  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const [divisionList, bookList] = await Promise.all([
          fetchDivisions(),
          fetchBooks(),
        ])
        if (cancelled) return
        setDivisions(divisionList)
        setAllBooks(bookList)
        setBooks(bookList)
      } catch (err) {
        if (cancelled) return
        setError(err instanceof Error ? err.message : String(err))
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()
    return () => { cancelled = true }
  }, [])

  // Lazily load desks when division changes
  useEffect(() => {
    if (!selection.divisionId) return

    let cancelled = false
    setLoading(true)

    fetchDesks(selection.divisionId)
      .then((deskList) => {
        if (cancelled) return
        setDesks(deskList)
      })
      .catch((err) => {
        if (cancelled) return
        setError(err instanceof Error ? err.message : String(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => { cancelled = true }
  }, [selection.divisionId])

  // Lazily filter books when desk changes
  useEffect(() => {
    if (!selection.deskId) return

    // Filter books to those associated with the selected desk
    // Books have a deskId property from the backend; for now we show all books
    // and let the backend filtering happen via positions API
    setBooks(allBooks)
  }, [selection.deskId, allBooks])

  const setSelection = useCallback((next: HierarchySelection) => {
    setSelectionState(next)

    // Reset downstream state when navigating up
    if (next.level === 'firm' || next.level === 'division') {
      setDesks([])
    }
    if (next.level === 'firm' || next.level === 'division' || next.level === 'desk') {
      // Reset book filter to all books
    }
  }, [])

  const breadcrumb: BreadcrumbItem[] = [
    { level: 'firm', id: null, label: 'Firm' },
  ]

  if (selection.divisionId) {
    const div = divisions.find((d) => d.id === selection.divisionId)
    breadcrumb.push({
      level: 'division',
      id: selection.divisionId,
      label: div?.name ?? selection.divisionId,
    })
  }

  if (selection.deskId) {
    const desk = desks.find((d) => d.id === selection.deskId)
    breadcrumb.push({
      level: 'desk',
      id: selection.deskId,
      label: desk?.name ?? selection.deskId,
    })
  }

  if (selection.bookId) {
    breadcrumb.push({
      level: 'book',
      id: selection.bookId,
      label: selection.bookId,
    })
  }

  // effectiveBookId: single book when at book level, null otherwise
  const effectiveBookId = selection.level === 'book' ? selection.bookId : null

  // effectiveBookIds: all books in current scope
  const effectiveBookIds: string[] = (() => {
    if (selection.level === 'book' && selection.bookId) {
      return [selection.bookId]
    }
    return books.map((b) => b.bookId)
  })()

  return {
    selection,
    setSelection,
    breadcrumb,
    effectiveBookId,
    effectiveBookIds,
    divisions,
    desks,
    books,
    loading,
    error,
  }
}
