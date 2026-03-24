import { test, expect, type Page } from '@playwright/test'
import { mockAllApiRoutes, mockRiskTabRoutes } from './fixtures'

// ---------------------------------------------------------------------------
// Test data
// ---------------------------------------------------------------------------

const FIRM_HIERARCHY_NODE = {
  level: 'FIRM',
  entityId: 'FIRM',
  entityName: 'FIRM',
  parentId: null,
  varValue: '2500000.00',
  expectedShortfall: '3125000.00',
  pnlToday: null,
  limitUtilisation: null,
  marginalVar: null,
  incrementalVar: null,
  topContributors: [
    { entityId: 'div-equities', entityName: 'Equities', varContribution: '1500000.00', pctOfTotal: '60.00' },
    { entityId: 'div-rates', entityName: 'Rates', varContribution: '1000000.00', pctOfTotal: '40.00' },
  ],
  childCount: 2,
  isPartial: false,
  missingBooks: [],
}

const PARTIAL_HIERARCHY_NODE = {
  ...FIRM_HIERARCHY_NODE,
  isPartial: true,
  missingBooks: ['book-missing-1', 'book-missing-2'],
}

const DESK_HIERARCHY_NODE = {
  level: 'DESK',
  entityId: 'desk-rates',
  entityName: 'Rates Desk',
  parentId: 'div-rates',
  varValue: '1000000.00',
  expectedShortfall: '1250000.00',
  pnlToday: null,
  limitUtilisation: null,
  marginalVar: null,
  incrementalVar: null,
  topContributors: [
    { entityId: 'book-us-rates', entityName: 'US Rates Book', varContribution: '600000.00', pctOfTotal: '60.00' },
    { entityId: 'book-eu-rates', entityName: 'EU Rates Book', varContribution: '400000.00', pctOfTotal: '40.00' },
  ],
  childCount: 2,
  isPartial: false,
  missingBooks: [],
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function mockHierarchyRiskNode(page: Page, node: object | null, status = 200): Promise<void> {
  // Register AFTER the catch-all so this takes precedence
  await page.route('**/api/v1/risk/hierarchy/**', (route) => {
    route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(node),
    })
  })
}

async function goToRiskTab(page: Page): Promise<void> {
  await page.goto('/')
  await page.getByTestId('tab-risk').click()
  await page.waitForSelector('[data-testid="var-dashboard"], [data-testid="var-empty"]')
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test.describe('Hierarchy Contribution Table', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
    await mockRiskTabRoutes(page)
  })

  test('shows firm-level contributor rows at firm-level aggregated view', async ({ page }) => {
    await mockHierarchyRiskNode(page, FIRM_HIERARCHY_NODE)

    await goToRiskTab(page)

    await expect(page.getByTestId('hierarchy-contribution-table')).toBeVisible()
    await expect(page.getByTestId('contributor-row-div-equities')).toBeVisible()
    await expect(page.getByTestId('contributor-row-div-rates')).toBeVisible()
    await expect(page.getByTestId('hierarchy-contribution-table')).toContainText('Equities')
    await expect(page.getByTestId('hierarchy-contribution-table')).toContainText('Rates')
  })

  test('shows correct percentage values for contributors', async ({ page }) => {
    await mockHierarchyRiskNode(page, FIRM_HIERARCHY_NODE)

    await goToRiskTab(page)

    await expect(page.getByTestId('hierarchy-contribution-table')).toContainText('60.0%')
    await expect(page.getByTestId('hierarchy-contribution-table')).toContainText('40.0%')
  })

  test('shows "Top Division Contributors" header at firm level', async ({ page }) => {
    await mockHierarchyRiskNode(page, FIRM_HIERARCHY_NODE)

    await goToRiskTab(page)

    await expect(page.getByTestId('hierarchy-contribution-table')).toContainText('Top Division Contributors')
  })

  test('shows partial badge and missing books note when node is partial', async ({ page }) => {
    await mockHierarchyRiskNode(page, PARTIAL_HIERARCHY_NODE)

    await goToRiskTab(page)

    await expect(page.getByTestId('partial-badge')).toBeVisible()
    await expect(page.getByTestId('missing-books-note')).toBeVisible()
    await expect(page.getByTestId('missing-books-note')).toContainText('book-missing-1')
  })

  test('does not show hierarchy contribution table when API returns 404', async ({ page }) => {
    // The catch-all returns 404 for hierarchy/* — no override needed
    await goToRiskTab(page)

    await expect(page.getByTestId('hierarchy-contribution-section')).not.toBeAttached()
  })

  test('shows "Top Book Contributors" header at desk level after navigation', async ({ page }) => {
    const TEST_DIVISIONS = [
      { id: 'div-rates', name: 'Rates', description: 'Rates division', deskCount: 1 },
    ]
    const TEST_DESKS = [
      { id: 'desk-rates', name: 'Rates Desk', divisionId: 'div-rates', deskHead: 'Alice', bookCount: 2 },
    ]
    const TEST_BOOKS_FOR_DESK = [{ bookId: 'book-us-rates' }, { bookId: 'book-eu-rates' }]

    await page.unroute('**/api/v1/divisions')
    await page.route('**/api/v1/divisions', (route) => {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(TEST_DIVISIONS) })
    })

    await page.unroute('**/api/v1/divisions/*/desks')
    await page.route('**/api/v1/divisions/*/desks', (route) => {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(TEST_DESKS) })
    })

    await page.unroute('**/api/v1/books')
    await page.route('**/api/v1/books', (route) => {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(TEST_BOOKS_FOR_DESK) })
    })

    await page.route('**/api/v1/risk/hierarchy/**', (route) => {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(DESK_HIERARCHY_NODE) })
    })

    // Navigate to desk level
    await page.goto('/')
    await page.getByTestId('hierarchy-selector-toggle').click()
    await page.getByTestId('hierarchy-division-div-rates').click()
    await page.waitForSelector('[data-testid="hierarchy-desk-desk-rates"]')
    await page.getByTestId('hierarchy-desk-desk-rates').click()

    await page.getByTestId('tab-risk').click()
    await page.waitForSelector('[data-testid="var-dashboard"], [data-testid="var-empty"]')

    await expect(page.getByTestId('hierarchy-contribution-table')).toContainText('Top Book Contributors')
    await expect(page.getByTestId('contributor-row-book-us-rates')).toBeVisible()
    await expect(page.getByTestId('contributor-row-book-eu-rates')).toBeVisible()
  })
})
