import { createContext, useContext } from 'react'
import { DEFAULT_PERSONA, type DemoPersona } from './demoPersonas'

export interface DemoPersonaContextValue {
  persona: DemoPersona
  setPersona: (p: DemoPersona) => void
}

export const DemoPersonaContext = createContext<DemoPersonaContextValue>({
  persona: DEFAULT_PERSONA,
  setPersona: () => {},
})

export function useDemoPersona(): DemoPersonaContextValue {
  return useContext(DemoPersonaContext)
}
