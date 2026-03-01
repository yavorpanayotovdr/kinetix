import { test, expect } from '@playwright/test'
import { mockAllApiRoutes, TEST_POSITIONS } from './fixtures'

test.describe('WebSocket Reconnect - Banner and Recovery', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('app renders the position grid and shows Disconnected status when WebSocket is unavailable', async ({
    page,
  }) => {
    // The default mockAllApiRoutes aborts WebSocket connections,
    // so the app should show Disconnected status
    await page.goto('/')
    await page.waitForSelector('[data-testid="connection-status"]')

    const connectionStatus = page.getByTestId('connection-status')
    await expect(connectionStatus).toContainText('Disconnected')
  })

  test('reconnecting banner appears when WebSocket connection fails', async ({
    page,
  }) => {
    await page.goto('/')

    // Wait for the positions to load and the WebSocket connection attempt to fail
    await page.waitForSelector('[data-testid="connection-status"]')

    // The banner should appear since the WebSocket was aborted
    // usePriceStream schedules a reconnect attempt after ws.onclose fires,
    // which sets reconnecting=true
    const banner = page.getByTestId('reconnecting-banner')
    await expect(banner).toBeVisible({ timeout: 5000 })
    await expect(banner).toContainText('Reconnecting...')
  })

  test('reconnecting banner has the correct ARIA role for accessibility', async ({
    page,
  }) => {
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

    // Even though WebSocket is disconnected, positions should still show their data
    const aaplRow = page.getByTestId('position-row-AAPL')
    await expect(aaplRow).toBeVisible()
    // The row should still display the position data from the API
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
    // Override the WebSocket route to allow the connection through
    // We use page.addInitScript to inject a mock WebSocket server
    await page.unroute('**/ws/prices')

    await page.addInitScript(() => {
      // Replace the native WebSocket with a mock that immediately opens
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

          // Simulate successful connection after a short delay
          setTimeout(() => {
            this.readyState = 1
            const openEvent = new Event('open')
            if (this.onopen) this.onopen.call(this as unknown as WebSocket, openEvent)
            this.dispatchEvent(openEvent)
          }, 50)
        }

        send(_data: string | ArrayBuffer | Blob | ArrayBufferView): void {
          // Silently accept messages
        }

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

      // Store reference so tests can access it
      ;(window as unknown as Record<string, unknown>).__OriginalWebSocket = OriginalWebSocket
      ;(window as unknown as Record<string, unknown>).WebSocket = MockWebSocket
    })

    await page.goto('/')
    await page.waitForSelector('[data-testid="connection-status"]')

    const connectionStatus = page.getByTestId('connection-status')
    await expect(connectionStatus).toContainText('Live', { timeout: 5000 })
  })

  test('reconnecting banner disappears when WebSocket reconnects', async ({
    page,
  }) => {
    // Use a script that simulates: connect -> disconnect -> reconnect
    await page.unroute('**/ws/prices')

    await page.addInitScript(() => {
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
            // First connection: open, then close after 200ms to simulate a drop
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

        send(_data: string | ArrayBuffer | Blob | ArrayBufferView): void {
          // Silently accept
        }

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

      ;(window as unknown as Record<string, unknown>).WebSocket = MockWebSocket
    })

    await page.goto('/')
    await page.waitForSelector('[data-testid="connection-status"]')

    // First: should show Live briefly
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
