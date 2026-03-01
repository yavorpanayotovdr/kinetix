import { renderHook, act } from '@testing-library/react'
import { describe, expect, it, beforeEach } from 'vitest'
import { useWorkspace, DEFAULT_PREFERENCES } from './useWorkspace'

const STORAGE_KEY = 'kinetix:workspace'

describe('useWorkspace', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('provides default preferences when none saved', () => {
    const { result } = renderHook(() => useWorkspace())

    expect(result.current.preferences).toEqual(DEFAULT_PREFERENCES)
  })

  it('saves workspace preferences to localStorage', () => {
    const { result } = renderHook(() => useWorkspace())

    act(() => {
      result.current.updatePreference('defaultTab', 'risk')
    })

    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY)!)
    expect(saved.defaultTab).toBe('risk')
  })

  it('loads workspace preferences on mount', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      ...DEFAULT_PREFERENCES,
      defaultTab: 'pnl',
      defaultPortfolio: 'port-2',
    }))

    const { result } = renderHook(() => useWorkspace())

    expect(result.current.preferences.defaultTab).toBe('pnl')
    expect(result.current.preferences.defaultPortfolio).toBe('port-2')
  })

  it('resets preferences to defaults', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      ...DEFAULT_PREFERENCES,
      defaultTab: 'risk',
    }))

    const { result } = renderHook(() => useWorkspace())

    expect(result.current.preferences.defaultTab).toBe('risk')

    act(() => {
      result.current.resetPreferences()
    })

    expect(result.current.preferences).toEqual(DEFAULT_PREFERENCES)
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY)!)
    expect(saved).toEqual(DEFAULT_PREFERENCES)
  })

  it('updates individual preference without affecting others', () => {
    const { result } = renderHook(() => useWorkspace())

    act(() => {
      result.current.updatePreference('defaultTab', 'scenarios')
    })

    expect(result.current.preferences.defaultTab).toBe('scenarios')
    expect(result.current.preferences.defaultPortfolio).toBe(DEFAULT_PREFERENCES.defaultPortfolio)
  })

  it('handles partial saved preferences gracefully', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ defaultTab: 'risk' }))

    const { result } = renderHook(() => useWorkspace())

    expect(result.current.preferences.defaultTab).toBe('risk')
    expect(result.current.preferences.defaultPortfolio).toBe(DEFAULT_PREFERENCES.defaultPortfolio)
  })
})
