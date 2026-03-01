import { renderHook, act } from '@testing-library/react'
import { describe, expect, it, beforeEach } from 'vitest'
import { useTheme } from './useTheme'

describe('useTheme', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
  })

  it('should toggle dark mode on button click', () => {
    const { result } = renderHook(() => useTheme())

    expect(result.current.isDark).toBe(false)

    act(() => {
      result.current.toggle()
    })

    expect(result.current.isDark).toBe(true)
  })

  it('should persist dark mode preference in localStorage', () => {
    const { result } = renderHook(() => useTheme())

    act(() => {
      result.current.toggle()
    })

    expect(localStorage.getItem('kinetix:theme')).toBe('dark')

    act(() => {
      result.current.toggle()
    })

    expect(localStorage.getItem('kinetix:theme')).toBe('light')
  })

  it('should load dark mode preference on mount', () => {
    localStorage.setItem('kinetix:theme', 'dark')

    const { result } = renderHook(() => useTheme())

    expect(result.current.isDark).toBe(true)
  })

  it('should apply dark class to html element', () => {
    const { result } = renderHook(() => useTheme())

    act(() => {
      result.current.toggle()
    })

    expect(document.documentElement.classList.contains('dark')).toBe(true)

    act(() => {
      result.current.toggle()
    })

    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })
})
