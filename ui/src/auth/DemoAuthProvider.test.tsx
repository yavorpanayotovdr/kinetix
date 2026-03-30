import { describe, it, expect, vi } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import { DemoAuthProvider } from './DemoAuthProvider'
import { useDemoPersona } from './useDemoPersona'
import { useAuth } from './useAuth'
import { DEMO_PERSONAS } from './demoPersonas'
import * as authFetchModule from './authFetch'

function AuthConsumer() {
  const auth = useAuth()
  return (
    <div>
      <span data-testid="authenticated">{String(auth.authenticated)}</span>
      <span data-testid="initialising">{String(auth.initialising)}</span>
      <span data-testid="token">{String(auth.token)}</span>
      <span data-testid="username">{auth.username}</span>
      <span data-testid="roles">{JSON.stringify(auth.roles)}</span>
      <button data-testid="logout" onClick={auth.logout}>Logout</button>
    </div>
  )
}

function PersonaSwitcherConsumer() {
  const { persona, setPersona } = useDemoPersona()
  const auth = useAuth()
  const traderPersona = DEMO_PERSONAS.find((p) => p.key === 'trader')!
  return (
    <div>
      <span data-testid="persona-key">{persona.key}</span>
      <span data-testid="auth-username">{auth.username}</span>
      <span data-testid="auth-roles">{JSON.stringify(auth.roles)}</span>
      <button data-testid="switch" onClick={() => setPersona(traderPersona)}>Switch</button>
    </div>
  )
}

describe('DemoAuthProvider', () => {
  it('provides authenticated state immediately without Keycloak', () => {
    render(
      <DemoAuthProvider>
        <AuthConsumer />
      </DemoAuthProvider>,
    )

    expect(screen.getByTestId('authenticated')).toHaveTextContent('true')
    expect(screen.getByTestId('initialising')).toHaveTextContent('false')
    expect(screen.getByTestId('token')).toHaveTextContent('null')
  })

  it('defaults to RISK_MANAGER persona with roles as single-element array', () => {
    render(
      <DemoAuthProvider>
        <AuthConsumer />
      </DemoAuthProvider>,
    )

    expect(screen.getByTestId('username')).toHaveTextContent('risk_mgr')
    expect(screen.getByTestId('roles')).toHaveTextContent('["RISK_MANAGER"]')
  })

  it('updates auth state atomically when persona changes', () => {
    render(
      <DemoAuthProvider>
        <PersonaSwitcherConsumer />
      </DemoAuthProvider>,
    )

    expect(screen.getByTestId('persona-key')).toHaveTextContent('risk_manager')
    expect(screen.getByTestId('auth-username')).toHaveTextContent('risk_mgr')
    expect(screen.getByTestId('auth-roles')).toHaveTextContent('["RISK_MANAGER"]')

    act(() => {
      screen.getByTestId('switch').click()
    })

    expect(screen.getByTestId('persona-key')).toHaveTextContent('trader')
    expect(screen.getByTestId('auth-username')).toHaveTextContent('trader1')
    expect(screen.getByTestId('auth-roles')).toHaveTextContent('["TRADER"]')
  })

  it('logout function exists and does nothing when called', () => {
    render(
      <DemoAuthProvider>
        <AuthConsumer />
      </DemoAuthProvider>,
    )

    act(() => {
      screen.getByTestId('logout').click()
    })

    expect(screen.getByTestId('authenticated')).toHaveTextContent('true')
    expect(screen.getByTestId('username')).toHaveTextContent('risk_mgr')
  })

  it('does not call setAuthToken', () => {
    const spy = vi.spyOn(authFetchModule, 'setAuthToken')

    render(
      <DemoAuthProvider>
        <AuthConsumer />
      </DemoAuthProvider>,
    )

    expect(spy).not.toHaveBeenCalled()
    spy.mockRestore()
  })
})
