import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { ErrorBoundary } from './components/ErrorBoundary.tsx'
import { AuthWrapper } from './auth/AuthWrapper.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <AuthWrapper>
        <App />
      </AuthWrapper>
    </ErrorBoundary>
  </StrictMode>,
)
