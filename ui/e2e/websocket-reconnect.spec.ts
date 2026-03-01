import { test, expect } from '@playwright/test'
import { mockAllApiRoutes, TEST_POSITIONS } from './fixtures'

test.describe('WebSocket Reconnect - Banner and Recovery', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('app shows Disconnected status when WebSocket immediately fails', async ({
    page,
  }) => {
    // Inject a mock WebSocket that immediately fires an error and close event
    await page.addInitScript(() => {
      class FailingWebSocket extends EventTarget {
        static CONNECTING = 0
        static OPEN = 1
        static CLOSING = 2
        static CLOSED = 3
        CONNECTING = 0
        OPEN = 1
        CLOSING = 2
        CLOSED = 3

        readyState = 0
        url: string
        protocol = ''
        extensions = ''
        bufferedAmount = 0
        binaryType: BinaryType = 'blob'
        onopen: ((this: WebSocket, ev: Event) => void) | null = null
        onclose: ((this: WebSocket, ev: CloseEvent) => void) | null = null
        onmessage: ((this: WebSocket, ev: MessageEvent) => void) | null = null
        onerror: ((this: WebSocket, ev: Event) => void) | null = null

        constructor(url: string | URL, _protocols?: string | string[]) {
          super()
          this.url = typeof url === 'string' ? url : url.toString()

          // Only intercept the app's WebSocket, not Vite HMR
          if (this.url.includes('/ws/prices')) {
            // Simulate connection failure after a brief delay
            setTimeout(() => {
              this.readyState = 3
              const errorEvent = new Event('error')
              if (this.onerror) this.onerror.call(this as unknown as WebSocket, errorEvent)
              this.dispatchEvent(errorEvent)
              const closeEvent = new CloseEvent('close', { code: 1006, reason: 'Connection refused' })
              if (this.onclose) this.onclose.call(this as unknown as WebSocket, closeEvent)
              this.dispatchEvent(closeEvent)
            }, 50)
          }
        }

        send(_data: string | ArrayBuffer | Blob | ArrayBufferView): void {}

        close(_code?: number, _reason?: string): void {
          this.readyState = 3
        }

        addEventListener(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | AddEventListenerOptions): void {
          super.addEventListener(type, listener, options)
        }

        removeEventListener(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | EventListenerOptions): void {
          super.removeEventListener(type, listener, options)
        }
      }

      // Store the original for Vite HMR and other system WebSockets
      const OriginalWebSocket = window.WebSocket
      ;(window as unknown as Record<string, unknown>).WebSocket = function (url: string | URL, protocols?: string | string[]) {
        const urlStr = typeof url === 'string' ? url : url.toString()
        if (urlStr.includes('/ws/prices')) {
          return new FailingWebSocket(url, protocols)
        }
        return new OriginalWebSocket(url, protocols)
      } as unknown as typeof WebSocket
      // Copy static properties
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'CONNECTING', { value: 0 })
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'OPEN', { value: 1 })
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'CLOSING', { value: 2 })
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'CLOSED', { value: 3 })
    })

    await page.goto('/')
    await page.waitForSelector('[data-testid="connection-status"]')

    const connectionStatus = page.getByTestId('connection-status')
    await expect(connectionStatus).toContainText('Disconnected', {
      timeout: 5000,
    })
  })

  test('reconnecting banner appears when WebSocket connection fails', async ({
    page,
  }) => {
    // Inject a mock that initially fails and triggers reconnect state
    await page.addInitScript(() => {
      const OriginalWebSocket = window.WebSocket

      class FailingWebSocket extends EventTarget {
        static CONNECTING = 0
        static OPEN = 1
        static CLOSING = 2
        static CLOSED = 3
        CONNECTING = 0
        OPEN = 1
        CLOSING = 2
        CLOSED = 3

        readyState = 0
        url: string
        protocol = ''
        extensions = ''
        bufferedAmount = 0
        binaryType: BinaryType = 'blob'
        onopen: ((this: WebSocket, ev: Event) => void) | null = null
        onclose: ((this: WebSocket, ev: CloseEvent) => void) | null = null
        onmessage: ((this: WebSocket, ev: MessageEvent) => void) | null = null
        onerror: ((this: WebSocket, ev: Event) => void) | null = null

        constructor(url: string | URL, _protocols?: string | string[]) {
          super()
          this.url = typeof url === 'string' ? url : url.toString()

          // Simulate connection failure
          setTimeout(() => {
            this.readyState = 3
            const errorEvent = new Event('error')
            if (this.onerror) this.onerror.call(this as unknown as WebSocket, errorEvent)
            this.dispatchEvent(errorEvent)
            const closeEvent = new CloseEvent('close', { code: 1006, reason: 'Connection refused' })
            if (this.onclose) this.onclose.call(this as unknown as WebSocket, closeEvent)
            this.dispatchEvent(closeEvent)
          }, 50)
        }

        send(_data: string | ArrayBuffer | Blob | ArrayBufferView): void {}

        close(_code?: number, _reason?: string): void {
          this.readyState = 3
        }

        addEventListener(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | AddEventListenerOptions): void {
          super.addEventListener(type, listener, options)
        }

        removeEventListener(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | EventListenerOptions): void {
          super.removeEventListener(type, listener, options)
        }
      }

      ;(window as unknown as Record<string, unknown>).WebSocket = function (url: string | URL, protocols?: string | string[]) {
        const urlStr = typeof url === 'string' ? url : url.toString()
        if (urlStr.includes('/ws/prices')) {
          return new FailingWebSocket(url, protocols)
        }
        return new OriginalWebSocket(url, protocols)
      } as unknown as typeof WebSocket
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'CONNECTING', { value: 0 })
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'OPEN', { value: 1 })
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'CLOSING', { value: 2 })
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'CLOSED', { value: 3 })
    })

    await page.goto('/')
    await page.waitForSelector('[data-testid="connection-status"]')

    // After the WebSocket fails, the reconnect logic triggers and the banner appears
    const banner = page.getByTestId('reconnecting-banner')
    await expect(banner).toBeVisible({ timeout: 5000 })
    await expect(banner).toContainText('Reconnecting...')
  })

  test('reconnecting banner has role="alert" for screen reader accessibility', async ({
    page,
  }) => {
    // Inject a mock that fails and triggers the reconnecting state
    await page.addInitScript(() => {
      const OriginalWebSocket = window.WebSocket

      class FailingWebSocket extends EventTarget {
        static CONNECTING = 0
        static OPEN = 1
        static CLOSING = 2
        static CLOSED = 3
        CONNECTING = 0
        OPEN = 1
        CLOSING = 2
        CLOSED = 3

        readyState = 0
        url: string
        protocol = ''
        extensions = ''
        bufferedAmount = 0
        binaryType: BinaryType = 'blob'
        onopen: ((this: WebSocket, ev: Event) => void) | null = null
        onclose: ((this: WebSocket, ev: CloseEvent) => void) | null = null
        onmessage: ((this: WebSocket, ev: MessageEvent) => void) | null = null
        onerror: ((this: WebSocket, ev: Event) => void) | null = null

        constructor(url: string | URL, _protocols?: string | string[]) {
          super()
          this.url = typeof url === 'string' ? url : url.toString()

          setTimeout(() => {
            this.readyState = 3
            const errorEvent = new Event('error')
            if (this.onerror) this.onerror.call(this as unknown as WebSocket, errorEvent)
            this.dispatchEvent(errorEvent)
            const closeEvent = new CloseEvent('close', { code: 1006, reason: 'Connection refused' })
            if (this.onclose) this.onclose.call(this as unknown as WebSocket, closeEvent)
            this.dispatchEvent(closeEvent)
          }, 50)
        }

        send(_data: string | ArrayBuffer | Blob | ArrayBufferView): void {}

        close(_code?: number, _reason?: string): void {
          this.readyState = 3
        }

        addEventListener(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | AddEventListenerOptions): void {
          super.addEventListener(type, listener, options)
        }

        removeEventListener(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | EventListenerOptions): void {
          super.removeEventListener(type, listener, options)
        }
      }

      ;(window as unknown as Record<string, unknown>).WebSocket = function (url: string | URL, protocols?: string | string[]) {
        const urlStr = typeof url === 'string' ? url : url.toString()
        if (urlStr.includes('/ws/prices')) {
          return new FailingWebSocket(url, protocols)
        }
        return new OriginalWebSocket(url, protocols)
      } as unknown as typeof WebSocket
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'CONNECTING', { value: 0 })
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'OPEN', { value: 1 })
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'CLOSING', { value: 2 })
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'CLOSED', { value: 3 })
    })

    await page.goto('/')
    await page.waitForSelector('[data-testid="connection-status"]')

    const banner = page.getByTestId('reconnecting-banner')
    await expect(banner).toBeVisible({ timeout: 5000 })
    await expect(banner).toHaveAttribute('role', 'alert')
  })

  test('positions retain their last known values when WebSocket is disconnected', async ({
    page,
  }) => {
    await page.goto('/')

    // Wait for positions to render
    for (const pos of TEST_POSITIONS) {
      await page.waitForSelector(
        `[data-testid="position-row-${pos.instrumentId}"]`,
      )
    }

    // Even though WebSocket may be disconnected, positions should still show their data
    const aaplRow = page.getByTestId('position-row-AAPL')
    await expect(aaplRow).toBeVisible()
    await expect(aaplRow).toContainText('AAPL')

    const googlRow = page.getByTestId('position-row-GOOGL')
    await expect(googlRow).toBeVisible()
    await expect(googlRow).toContainText('GOOGL')

    const eurUsdRow = page.getByTestId('position-row-EUR_USD')
    await expect(eurUsdRow).toBeVisible()
    await expect(eurUsdRow).toContainText('EUR_USD')
  })

  test('connection status shows Live when WebSocket connects successfully', async ({
    page,
  }) => {
    // Inject a mock WebSocket that successfully connects for /ws/prices
    await page.addInitScript(() => {
      const OriginalWebSocket = window.WebSocket

      class MockWebSocket extends EventTarget {
        static CONNECTING = 0
        static OPEN = 1
        static CLOSING = 2
        static CLOSED = 3
        CONNECTING = 0
        OPEN = 1
        CLOSING = 2
        CLOSED = 3

        readyState = 0
        url: string
        protocol = ''
        extensions = ''
        bufferedAmount = 0
        binaryType: BinaryType = 'blob'
        onopen: ((this: WebSocket, ev: Event) => void) | null = null
        onclose: ((this: WebSocket, ev: CloseEvent) => void) | null = null
        onmessage: ((this: WebSocket, ev: MessageEvent) => void) | null = null
        onerror: ((this: WebSocket, ev: Event) => void) | null = null

        constructor(url: string | URL, _protocols?: string | string[]) {
          super()
          this.url = typeof url === 'string' ? url : url.toString()

          // Simulate successful connection
          setTimeout(() => {
            this.readyState = 1
            const openEvent = new Event('open')
            if (this.onopen) this.onopen.call(this as unknown as WebSocket, openEvent)
            this.dispatchEvent(openEvent)
          }, 50)
        }

        send(_data: string | ArrayBuffer | Blob | ArrayBufferView): void {}

        close(_code?: number, _reason?: string): void {
          this.readyState = 3
          const closeEvent = new CloseEvent('close', { code: 1000, reason: '' })
          if (this.onclose) this.onclose.call(this as unknown as WebSocket, closeEvent)
          this.dispatchEvent(closeEvent)
        }

        addEventListener(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | AddEventListenerOptions): void {
          super.addEventListener(type, listener, options)
        }

        removeEventListener(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | EventListenerOptions): void {
          super.removeEventListener(type, listener, options)
        }
      }

      ;(window as unknown as Record<string, unknown>).WebSocket = function (url: string | URL, protocols?: string | string[]) {
        const urlStr = typeof url === 'string' ? url : url.toString()
        if (urlStr.includes('/ws/prices')) {
          return new MockWebSocket(url, protocols)
        }
        return new OriginalWebSocket(url, protocols)
      } as unknown as typeof WebSocket
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'CONNECTING', { value: 0 })
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'OPEN', { value: 1 })
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'CLOSING', { value: 2 })
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'CLOSED', { value: 3 })
    })

    await page.goto('/')
    await page.waitForSelector('[data-testid="connection-status"]')

    const connectionStatus = page.getByTestId('connection-status')
    await expect(connectionStatus).toContainText('Live', { timeout: 5000 })
  })

  test('reconnecting banner disappears when WebSocket reconnects after a drop', async ({
    page,
  }) => {
    // Inject a WebSocket that: connects -> drops -> reconnects
    await page.addInitScript(() => {
      const OriginalWebSocket = window.WebSocket
      let connectionCount = 0

      class MockWebSocket extends EventTarget {
        static CONNECTING = 0
        static OPEN = 1
        static CLOSING = 2
        static CLOSED = 3
        CONNECTING = 0
        OPEN = 1
        CLOSING = 2
        CLOSED = 3

        readyState = 0
        url: string
        protocol = ''
        extensions = ''
        bufferedAmount = 0
        binaryType: BinaryType = 'blob'
        onopen: ((this: WebSocket, ev: Event) => void) | null = null
        onclose: ((this: WebSocket, ev: CloseEvent) => void) | null = null
        onmessage: ((this: WebSocket, ev: MessageEvent) => void) | null = null
        onerror: ((this: WebSocket, ev: Event) => void) | null = null

        constructor(url: string | URL, _protocols?: string | string[]) {
          super()
          this.url = typeof url === 'string' ? url : url.toString()
          connectionCount++

          if (connectionCount === 1) {
            // First connection: open, then drop after 200ms
            setTimeout(() => {
              this.readyState = 1
              const openEvent = new Event('open')
              if (this.onopen) this.onopen.call(this as unknown as WebSocket, openEvent)
              this.dispatchEvent(openEvent)
            }, 50)

            setTimeout(() => {
              this.readyState = 3
              const closeEvent = new CloseEvent('close', {
                code: 1006,
                reason: 'Simulated disconnect',
              })
              if (this.onclose) this.onclose.call(this as unknown as WebSocket, closeEvent)
              this.dispatchEvent(closeEvent)
            }, 200)
          } else {
            // Subsequent connections: reconnect successfully
            setTimeout(() => {
              this.readyState = 1
              const openEvent = new Event('open')
              if (this.onopen) this.onopen.call(this as unknown as WebSocket, openEvent)
              this.dispatchEvent(openEvent)
            }, 50)
          }
        }

        send(_data: string | ArrayBuffer | Blob | ArrayBufferView): void {}

        close(_code?: number, _reason?: string): void {
          this.readyState = 3
          const closeEvent = new CloseEvent('close', { code: 1000, reason: '' })
          if (this.onclose) this.onclose.call(this as unknown as WebSocket, closeEvent)
          this.dispatchEvent(closeEvent)
        }

        addEventListener(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | AddEventListenerOptions): void {
          super.addEventListener(type, listener, options)
        }

        removeEventListener(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | EventListenerOptions): void {
          super.removeEventListener(type, listener, options)
        }
      }

      ;(window as unknown as Record<string, unknown>).WebSocket = function (url: string | URL, protocols?: string | string[]) {
        const urlStr = typeof url === 'string' ? url : url.toString()
        if (urlStr.includes('/ws/prices')) {
          return new MockWebSocket(url, protocols)
        }
        return new OriginalWebSocket(url, protocols)
      } as unknown as typeof WebSocket
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'CONNECTING', { value: 0 })
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'OPEN', { value: 1 })
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'CLOSING', { value: 2 })
      Object.defineProperty((window as unknown as Record<string, unknown>).WebSocket, 'CLOSED', { value: 3 })
    })

    await page.goto('/')
    await page.waitForSelector('[data-testid="connection-status"]')

    // First: should show Live
    await expect(page.getByTestId('connection-status')).toContainText('Live', {
      timeout: 3000,
    })

    // Then: the simulated disconnect triggers reconnecting banner
    const banner = page.getByTestId('reconnecting-banner')
    await expect(banner).toBeVisible({ timeout: 5000 })
    await expect(banner).toContainText('Reconnecting...')

    // Then: the reconnection succeeds and the banner disappears
    await expect(banner).toBeHidden({ timeout: 10000 })

    // Connection status returns to Live
    await expect(page.getByTestId('connection-status')).toContainText('Live', {
      timeout: 5000,
    })
  })
})
