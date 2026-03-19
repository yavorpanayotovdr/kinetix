import { test, expect } from '@playwright/test'
import { mockAllApiRoutes } from './fixtures'

async function injectFailingWebSocket(page: import('@playwright/test').Page) {
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
}

test.describe('DataQuality — synthetic Price Feed check during reconnect', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('shows "Price Feed" WARNING check in DataQuality dropdown when WebSocket is reconnecting', async ({ page }) => {
    await injectFailingWebSocket(page)

    await page.goto('/')

    // Wait for the reconnecting banner to confirm reconnecting state is active
    await expect(page.getByTestId('reconnecting-banner')).toBeVisible({ timeout: 5000 })

    // Click the DataQuality indicator to open the dropdown
    const indicator = page.getByTestId('data-quality-indicator')
    await indicator.click()

    // The dropdown should be open
    const dropdown = page.getByTestId('data-quality-dropdown')
    await expect(dropdown).toBeVisible({ timeout: 3000 })

    // The synthetic "Price Feed" WARNING check should appear
    await expect(dropdown).toContainText('Price Feed')
    await expect(dropdown).toContainText('WebSocket reconnecting')
  })
})
