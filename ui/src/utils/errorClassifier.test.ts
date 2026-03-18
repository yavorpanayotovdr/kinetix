import { describe, it, expect } from 'vitest'
import { classifyFetchError } from './errorClassifier'

describe('classifyFetchError', () => {
  it('classifies 502 as transient and retryable', () => {
    const result = classifyFetchError(new Error('Bad Gateway'), 502)
    expect(result.kind).toBe('transient')
    expect(result.retryable).toBe(true)
    expect(result.message).toContain('temporarily unavailable')
  })

  it('classifies 503 as transient and retryable', () => {
    const result = classifyFetchError(new Error('Service Unavailable'), 503)
    expect(result.kind).toBe('transient')
    expect(result.retryable).toBe(true)
  })

  it('classifies 504 as transient and retryable', () => {
    const result = classifyFetchError(new Error('Gateway Timeout'), 504)
    expect(result.kind).toBe('transient')
    expect(result.retryable).toBe(true)
    expect(result.message).toContain('timed out')
  })

  it('classifies 401 as auth and not retryable', () => {
    const result = classifyFetchError(new Error('Unauthorized'), 401)
    expect(result.kind).toBe('auth')
    expect(result.retryable).toBe(false)
    expect(result.message).toContain('Session expired')
  })

  it('classifies 403 as auth and not retryable', () => {
    const result = classifyFetchError(new Error('Forbidden'), 403)
    expect(result.kind).toBe('auth')
    expect(result.retryable).toBe(false)
  })

  it('classifies 404 as notfound and not retryable', () => {
    const result = classifyFetchError(new Error('Not Found'), 404)
    expect(result.kind).toBe('notfound')
    expect(result.retryable).toBe(false)
    expect(result.message).toContain('No data available')
  })

  it('classifies other 5xx as error and not retryable', () => {
    const result = classifyFetchError(new Error('Internal Server Error'), 500)
    expect(result.kind).toBe('error')
    expect(result.retryable).toBe(false)
  })

  it('classifies network error without status as error', () => {
    const result = classifyFetchError(new TypeError('Failed to fetch'))
    expect(result.kind).toBe('error')
    expect(result.retryable).toBe(false)
    expect(result.message).toBe('Failed to fetch')
  })

  it('uses error message for non-Error values without status', () => {
    const result = classifyFetchError('something broke')
    expect(result.kind).toBe('error')
    expect(result.message).toBe('An unexpected error occurred.')
  })
})
