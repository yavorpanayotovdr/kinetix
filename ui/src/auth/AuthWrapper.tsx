import { Suspense, lazy } from 'react'
import { DEMO_MODE } from './demoPersonas'
import { DemoAuthProvider } from './DemoAuthProvider'

// Lazy-load AuthProvider so keycloak-js is tree-shaken from demo builds.
// AuthProvider.tsx has a module-level window.Keycloak read that executes on import.
const LazyAuthProvider = DEMO_MODE
  ? null
  : lazy(() => import('./AuthProvider').then((m) => ({ default: m.AuthProvider })))

export function AuthWrapper({ children }: { children: React.ReactNode }) {
  if (DEMO_MODE) {
    return <DemoAuthProvider>{children}</DemoAuthProvider>
  }
  const Provider = LazyAuthProvider!
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-surface-900 flex items-center justify-center" aria-busy="true">
          <div className="text-center">
            <h1 className="text-2xl font-bold text-white tracking-tight">Kinetix</h1>
            <p className="text-sm text-slate-400 mt-2">Loading...</p>
          </div>
        </div>
      }
    >
      <Provider>{children}</Provider>
    </Suspense>
  )
}
