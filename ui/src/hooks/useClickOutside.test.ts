import { renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { useClickOutside } from './useClickOutside'

function createRef(element: EventTarget | null) {
  return { current: element } as React.RefObject<HTMLElement>
}

describe('useClickOutside', () => {
  it('calls callback on mousedown outside the ref element', () => {
    const callback = vi.fn()
    const element = document.createElement('div')
    document.body.appendChild(element)

    renderHook(() => useClickOutside(createRef(element), callback))

    document.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(callback).toHaveBeenCalledTimes(1)

    document.body.removeChild(element)
  })

  it('does not call callback on mousedown inside the ref element', () => {
    const callback = vi.fn()
    const element = document.createElement('div')
    document.body.appendChild(element)

    renderHook(() => useClickOutside(createRef(element), callback))

    element.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(callback).not.toHaveBeenCalled()

    document.body.removeChild(element)
  })

  it('removes listener on unmount', () => {
    const callback = vi.fn()
    const element = document.createElement('div')
    document.body.appendChild(element)

    const { unmount } = renderHook(() => useClickOutside(createRef(element), callback))
    unmount()

    document.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(callback).not.toHaveBeenCalled()

    document.body.removeChild(element)
  })
})
