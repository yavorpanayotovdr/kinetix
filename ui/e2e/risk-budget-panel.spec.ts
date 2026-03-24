import { test, expect, type Page } from '@playwright/test'
import { mockAllApiRoutes, mockRiskTabRoutes } from './fixtures'

// ---------------------------------------------------------------------------
// Test data
// ---------------------------------------------------------------------------

const HIERARCHY_NODE_WITH_BUDGET = {
  level: 'FIRM',
  entityId: 'FIRM',
  entityName: 'FIRM',
  parentId: null,
  varValue: '2000000.00',
  expectedShortfall: '2500000.00',
  pnlToday: null,
  limitUtilisation: '40.00',
  marginalVar: null,
  incrementalVar: null,
  topContributors: [
    { entityId: 'div-eq', entityName: 'Equities', varContribution: '1200000.00', pctOfTotal: '60.00' },
    { entityId: 'div-fi', entityName: 'Fixed Income', varContribution: '800000.00', pctOfTotal: '40.00' },
  ],
  childCount: 2,
  isPartial: false,
  missingBooks: [],
}

const HIERARCHY_NODE_WARNING = {
  ...HIERARCHY_NODE_WITH_BUDGET,
  limitUtilisation: '85.00',
}

const HIERARCHY_NODE_BREACH = {
  ...HIERARCHY_NODE_WITH_BUDGET,
  limitUtilisation: '110.00',
}

const HIERARCHY_NODE_NO_BUDGET = {
  ...HIERARCHY_NODE_WITH_BUDGET,
  limitUtilisation: null,
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function mockHierarchyNode(page: Page, node: object): Promise<void> {
  await page.route('**/api/v1/risk/hierarchy/**', (route) => {
    route.fulfill({
      status: 200,
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

test.describe('Risk Budget Panel', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
    await mockRiskTabRoutes(page)
  })

  test('shows budget utilisation panel when limitUtilisation is present', async ({ page }) => {
    await mockHierarchyNode(page, HIERARCHY_NODE_WITH_BUDGET)
    await goToRiskTab(page)

    await expect(page.getByTestId('risk-budget-panel')).toBeVisible()
  })

  test('shows utilisation percentage in panel', async ({ page }) => {
    await mockHierarchyNode(page, HIERARCHY_NODE_WITH_BUDGET)
    await goToRiskTab(page)

    await expect(page.getByTestId('risk-budget-panel')).toContainText('40.0%')
  })

  test('does not show budget panel when limitUtilisation is null', async ({ page }) => {
    await mockHierarchyNode(page, HIERARCHY_NODE_NO_BUDGET)
    await goToRiskTab(page)

    await expect(page.getByTestId('risk-budget-panel')).not.toBeAttached()
  })

  test('shows green bar when utilisation is below 80%', async ({ page }) => {
    await mockHierarchyNode(page, HIERARCHY_NODE_WITH_BUDGET)
    await goToRiskTab(page)

    const fill = page.getByTestId('budget-bar-fill')
    await expect(fill).toBeVisible()
    // Green bar — check for emerald color class
    const className = await fill.getAttribute('class')
    expect(className).toMatch(/emerald/)
  })

  test('shows amber bar when utilisation is in warning range (80-99%)', async ({ page }) => {
    await mockHierarchyNode(page, HIERARCHY_NODE_WARNING)
    await goToRiskTab(page)

    await expect(page.getByTestId('risk-budget-panel')).toContainText('85.0%')
    const fill = page.getByTestId('budget-bar-fill')
    const className = await fill.getAttribute('class')
    expect(className).toMatch(/amber/)
  })

  test('shows red bar when utilisation is at or above 100%', async ({ page }) => {
    await mockHierarchyNode(page, HIERARCHY_NODE_BREACH)
    await goToRiskTab(page)

    await expect(page.getByTestId('risk-budget-panel')).toContainText('110.0%')
    const fill = page.getByTestId('budget-bar-fill')
    const className = await fill.getAttribute('class')
    expect(className).toMatch(/red/)
  })
})
