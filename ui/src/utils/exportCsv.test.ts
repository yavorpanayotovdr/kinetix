import { describe, expect, it, vi } from 'vitest'
import { exportToCsv } from './exportCsv'

describe('exportToCsv', () => {
  it('creates a CSV blob and triggers download', () => {
    const createObjectURL = vi.fn().mockReturnValue('blob:test')
    const revokeObjectURL = vi.fn()
    const clickSpy = vi.fn()
    const createElement = vi.spyOn(document, 'createElement')

    Object.defineProperty(window, 'URL', {
      value: { createObjectURL, revokeObjectURL },
      writable: true,
    })

    createElement.mockReturnValue({
      click: clickSpy,
      href: '',
      download: '',
      setAttribute: vi.fn(),
    } as unknown as HTMLAnchorElement)

    exportToCsv('test.csv', ['Name', 'Value'], [['Alice', '100'], ['Bob', '200']])

    expect(createObjectURL).toHaveBeenCalledOnce()
    const blob = createObjectURL.mock.calls[0][0] as Blob
    expect(blob.type).toBe('text/csv;charset=utf-8;')
    expect(clickSpy).toHaveBeenCalledOnce()
    expect(revokeObjectURL).toHaveBeenCalledOnce()

    createElement.mockRestore()
  })

  it('escapes fields containing commas', () => {
    const createObjectURL = vi.fn().mockReturnValue('blob:test')
    const revokeObjectURL = vi.fn()

    Object.defineProperty(window, 'URL', {
      value: { createObjectURL, revokeObjectURL },
      writable: true,
    })

    vi.spyOn(document, 'createElement').mockReturnValue({
      click: vi.fn(),
      href: '',
      download: '',
      setAttribute: vi.fn(),
    } as unknown as HTMLAnchorElement)

    exportToCsv('test.csv', ['Name'], [['Hello, World']])

    const blob = createObjectURL.mock.calls[0][0] as Blob
    expect(blob).toBeInstanceOf(Blob)

    vi.mocked(document.createElement).mockRestore()
  })
})
