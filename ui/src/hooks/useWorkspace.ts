import { useCallback, useState } from 'react'

const STORAGE_KEY = 'kinetix:workspace'

export interface WorkspacePreferences {
  defaultTab: string
  defaultPortfolio: string | null
  timeRange: string
  chartPreferences: {
    showGrid: boolean
    showLegend: boolean
  }
}

export const DEFAULT_PREFERENCES: WorkspacePreferences = {
  defaultTab: 'positions',
  defaultPortfolio: null,
  timeRange: '1d',
  chartPreferences: {
    showGrid: true,
    showLegend: true,
  },
}

function loadPreferences(): WorkspacePreferences {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (!stored) return DEFAULT_PREFERENCES
    const parsed = JSON.parse(stored)
    return { ...DEFAULT_PREFERENCES, ...parsed }
  } catch {
    return DEFAULT_PREFERENCES
  }
}

function savePreferences(prefs: WorkspacePreferences) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs))
}

export interface UseWorkspaceResult {
  preferences: WorkspacePreferences
  updatePreference: <K extends keyof WorkspacePreferences>(key: K, value: WorkspacePreferences[K]) => void
  resetPreferences: () => void
}

export function useWorkspace(): UseWorkspaceResult {
  const [preferences, setPreferences] = useState<WorkspacePreferences>(loadPreferences)

  const updatePreference = useCallback(<K extends keyof WorkspacePreferences>(
    key: K,
    value: WorkspacePreferences[K],
  ) => {
    setPreferences((prev) => {
      const next = { ...prev, [key]: value }
      savePreferences(next)
      return next
    })
  }, [])

  const resetPreferences = useCallback(() => {
    setPreferences(DEFAULT_PREFERENCES)
    savePreferences(DEFAULT_PREFERENCES)
  }, [])

  return { preferences, updatePreference, resetPreferences }
}
