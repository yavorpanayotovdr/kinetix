export const DEMO_MODE = import.meta.env.VITE_DEMO_MODE === 'true'

export interface DemoPersona {
  key: string
  username: string
  role: string
  label: string
  description: string
}

export const DEMO_PERSONAS: DemoPersona[] = [
  { key: 'risk_manager', username: 'risk_mgr', role: 'RISK_MANAGER', label: 'Risk Manager', description: 'VaR monitoring, stress testing, EOD workflows' },
  { key: 'trader', username: 'trader1', role: 'TRADER', label: 'Trader', description: 'Trade booking, position management, real-time P&L' },
  { key: 'admin', username: 'admin', role: 'ADMIN', label: 'Admin', description: 'Full system access — instruments, limits, users' },
  { key: 'compliance', username: 'compliance1', role: 'COMPLIANCE', label: 'Compliance', description: 'Regulatory reporting, audit trail, model governance' },
  { key: 'viewer', username: 'viewer1', role: 'VIEWER', label: 'Viewer', description: 'Read-only portfolio and risk snapshot' },
]

export const DEFAULT_PERSONA = DEMO_PERSONAS[0]
