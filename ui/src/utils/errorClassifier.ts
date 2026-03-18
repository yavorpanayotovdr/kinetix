export type ErrorKind = 'transient' | 'auth' | 'notfound' | 'error'

export interface ClassifiedError {
  kind: ErrorKind
  message: string
  retryable: boolean
}

export function classifyFetchError(
  err: unknown,
  status?: number,
): ClassifiedError {
  if (status === 502 || status === 503) {
    return {
      kind: 'transient',
      message: 'Service temporarily unavailable. Retrying...',
      retryable: true,
    }
  }
  if (status === 504) {
    return {
      kind: 'transient',
      message: 'Request timed out. Retrying...',
      retryable: true,
    }
  }
  if (status === 401 || status === 403) {
    return {
      kind: 'auth',
      message: 'Session expired. Please refresh the page.',
      retryable: false,
    }
  }
  if (status === 404) {
    return {
      kind: 'notfound',
      message: 'No data available.',
      retryable: false,
    }
  }
  if (status && status >= 500) {
    return {
      kind: 'error',
      message: 'An unexpected error occurred.',
      retryable: false,
    }
  }

  // Network error or unknown
  const message =
    err instanceof Error ? err.message : 'An unexpected error occurred.'
  return { kind: 'error', message, retryable: false }
}
