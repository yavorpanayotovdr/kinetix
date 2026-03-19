import { useRef, useState } from 'react'
import { ChevronDown, Building2 } from 'lucide-react'
import { HierarchyBreadcrumb } from './HierarchyBreadcrumb'
import { useClickOutside } from '../hooks/useClickOutside'
import type { UseHierarchySelectorResult, HierarchySelection } from '../hooks/useHierarchySelector'

interface HierarchySelectorProps {
  hierarchy: UseHierarchySelectorResult
}

export function HierarchySelector({ hierarchy }: HierarchySelectorProps) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useClickOutside(containerRef, () => setOpen(false))

  const { selection, setSelection, breadcrumb, divisions, desks, books, loading } = hierarchy

  const handleNavigate = (newSelection: HierarchySelection) => {
    setSelection(newSelection)
    if (newSelection.level === 'book') {
      setOpen(false)
    }
  }

  const handleDivisionClick = (divisionId: string) => {
    setSelection({
      level: 'division',
      divisionId,
      deskId: null,
      bookId: null,
    })
  }

  const handleDeskClick = (deskId: string) => {
    setSelection({
      level: 'desk',
      divisionId: selection.divisionId,
      deskId,
      bookId: null,
    })
  }

  const handleBookClick = (bookId: string) => {
    setSelection({
      level: 'book',
      divisionId: selection.divisionId,
      deskId: selection.deskId,
      bookId,
    })
    setOpen(false)
  }

  const handleFirmClick = () => {
    setSelection({ level: 'firm', divisionId: null, deskId: null, bookId: null })
    setOpen(false)
  }

  // Text-only breadcrumb for display inside the toggle button (no nested buttons)
  const breadcrumbLabel = breadcrumb.map((item) => item.label).join(' / ')

  return (
    <div ref={containerRef} className="relative" data-testid="hierarchy-selector">
      <button
        data-testid="hierarchy-selector-toggle"
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-2 bg-surface-800 border border-surface-700 text-white rounded-md px-3 py-1.5 text-sm hover:bg-surface-700 focus:ring-2 focus:ring-primary-500 transition-colors"
        aria-haspopup="true"
        aria-expanded={open}
        aria-label={`Hierarchy: ${breadcrumbLabel}`}
      >
        <Building2 className="h-4 w-4 text-primary-400" />
        <span className="text-sm text-white">{breadcrumbLabel}</span>
        <ChevronDown className={`h-4 w-4 text-slate-400 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div
          data-testid="hierarchy-panel"
          className="absolute left-0 mt-1 min-w-64 bg-white border border-slate-200 rounded-lg shadow-lg z-20 text-slate-800"
        >
          {/* Clickable breadcrumb at top of panel */}
          {breadcrumb.length > 1 && (
            <div className="px-3 pt-2 pb-1 border-b border-slate-100">
              <HierarchyBreadcrumb breadcrumb={breadcrumb} onNavigate={handleNavigate} />
            </div>
          )}

          <div className="py-1">
            {loading && (
              <div className="px-3 py-2 text-sm text-slate-500">Loading...</div>
            )}

            {!loading && (
              <>
                <button
                  data-testid="hierarchy-firm-option"
                  onClick={handleFirmClick}
                  className={`w-full text-left px-3 py-2 text-sm hover:bg-slate-50 transition-colors ${
                    selection.level === 'firm' ? 'font-semibold text-primary-700' : ''
                  }`}
                >
                  Firm (All)
                </button>

                {selection.level === 'firm' && divisions.length > 0 && (
                  <div className="border-t border-slate-100 mt-1 pt-1">
                    <div className="px-3 py-1 text-xs font-semibold text-slate-500 uppercase tracking-wide">
                      Divisions
                    </div>
                    {divisions.map((div) => (
                      <button
                        key={div.id}
                        data-testid={`hierarchy-division-${div.id}`}
                        onClick={() => handleDivisionClick(div.id)}
                        className="w-full text-left px-4 py-1.5 text-sm hover:bg-slate-50 transition-colors"
                      >
                        {div.name}
                        <span className="ml-1 text-xs text-slate-400">
                          ({div.deskCount} {div.deskCount === 1 ? 'desk' : 'desks'})
                        </span>
                      </button>
                    ))}
                  </div>
                )}

                {selection.level === 'division' && desks.length > 0 && (
                  <div className="border-t border-slate-100 mt-1 pt-1">
                    <div className="px-3 py-1 text-xs font-semibold text-slate-500 uppercase tracking-wide">
                      Desks
                    </div>
                    {desks.map((desk) => (
                      <button
                        key={desk.id}
                        data-testid={`hierarchy-desk-${desk.id}`}
                        onClick={() => handleDeskClick(desk.id)}
                        className={`w-full text-left px-4 py-1.5 text-sm hover:bg-slate-50 transition-colors ${
                          selection.deskId === desk.id ? 'font-semibold text-primary-700' : ''
                        }`}
                      >
                        {desk.name}
                        <span className="ml-1 text-xs text-slate-400">
                          ({desk.bookCount} {desk.bookCount === 1 ? 'book' : 'books'})
                        </span>
                      </button>
                    ))}
                  </div>
                )}

                {(selection.level === 'desk' || selection.level === 'book') && books.length > 0 && (
                  <div className="border-t border-slate-100 mt-1 pt-1">
                    <div className="px-3 py-1 text-xs font-semibold text-slate-500 uppercase tracking-wide">
                      Books
                    </div>
                    {books.map((book) => (
                      <button
                        key={book.bookId}
                        data-testid={`hierarchy-book-${book.bookId}`}
                        onClick={() => handleBookClick(book.bookId)}
                        className={`w-full text-left px-4 py-1.5 text-sm hover:bg-slate-50 transition-colors ${
                          selection.bookId === book.bookId ? 'font-semibold text-primary-700' : ''
                        }`}
                      >
                        {book.bookId}
                      </button>
                    ))}
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
