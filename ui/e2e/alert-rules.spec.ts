import { test, expect } from '@playwright/test'
import { mockAllApiRoutes, mockAlertRuleCrud } from './fixtures'

test.describe('Alert Rules - CRUD and Confirm Dialog', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('creating an alert rule adds it to the rules table', async ({
    page,
  }) => {
    await mockAlertRuleCrud(page)
    await page.goto('/')

    // Navigate to the Alerts tab
    await page.getByTestId('tab-alerts').click()
    await page.waitForSelector('[data-testid="create-rule-form"]')

    // Fill in the form
    await page.getByTestId('rule-name-input').fill('High VaR Alert')
    await page.getByTestId('rule-type-select').selectOption('VAR_BREACH')
    await page.getByTestId('rule-threshold-input').fill('1000000')
    await page.getByTestId('rule-operator-select').selectOption('GREATER_THAN')
    await page.getByTestId('rule-severity-select').selectOption('CRITICAL')

    // Submit the form
    await page.getByTestId('create-rule-btn').click()

    // The new rule should appear in the table
    const rulesTable = page.getByTestId('rules-table')
    await expect(rulesTable).toContainText('High VaR Alert')
    await expect(rulesTable).toContainText('VAR_BREACH')
    await expect(rulesTable).toContainText('CRITICAL')
  })

  test('clicking delete opens the confirmation dialog', async ({ page }) => {
    await mockAlertRuleCrud(page, [
      {
        id: 'rule-1',
        name: 'Test Rule',
        type: 'VAR_BREACH',
        threshold: 500000,
        operator: 'GREATER_THAN',
        severity: 'WARNING',
        channels: ['IN_APP'],
        enabled: true,
      },
    ])
    await page.goto('/')

    await page.getByTestId('tab-alerts').click()
    await page.waitForSelector('[data-testid="rules-table"]')

    // The rule should be visible
    await expect(page.getByTestId('rules-table')).toContainText('Test Rule')

    // Click the delete button for rule-1
    await page.getByTestId('delete-rule-rule-1').click()

    // The confirmation dialog should appear
    await expect(page.getByTestId('confirm-dialog')).toBeVisible()
    await expect(page.getByTestId('confirm-dialog')).toContainText(
      'Delete Alert Rule',
    )
    await expect(page.getByTestId('confirm-dialog')).toContainText(
      'This action cannot be undone',
    )
  })

  test('focus is trapped inside the confirm dialog', async ({ page }) => {
    await mockAlertRuleCrud(page, [
      {
        id: 'rule-1',
        name: 'Focus Trap Rule',
        type: 'PNL_THRESHOLD',
        threshold: 100000,
        operator: 'LESS_THAN',
        severity: 'CRITICAL',
        channels: ['IN_APP'],
        enabled: true,
      },
    ])
    await page.goto('/')

    await page.getByTestId('tab-alerts').click()
    await page.waitForSelector('[data-testid="rules-table"]')
    await page.getByTestId('delete-rule-rule-1').click()
    await page.waitForSelector('[data-testid="confirm-dialog"]')

    // Tab through the dialog: Cancel and Delete buttons are the focusable elements
    const cancelBtn = page.getByTestId('confirm-dialog-cancel')
    const confirmBtn = page.getByTestId('confirm-dialog-confirm')

    // Focus Cancel button first
    await cancelBtn.focus()
    await expect(cancelBtn).toBeFocused()

    // Tab to Confirm/Delete button
    await page.keyboard.press('Tab')
    await expect(confirmBtn).toBeFocused()

    // Tab again -- should cycle back within the dialog
    // (the two buttons are the only focusable elements in the dialog)
    await page.keyboard.press('Tab')
    // Focus should still be inside the dialog (either button)
    const focusedTestId = await page.evaluate(() =>
      document.activeElement?.getAttribute('data-testid'),
    )
    expect(
      focusedTestId === 'confirm-dialog-cancel' ||
        focusedTestId === 'confirm-dialog-confirm',
    ).toBe(true)
  })

  test('Escape dismisses the dialog without deleting the rule', async ({
    page,
  }) => {
    await mockAlertRuleCrud(page, [
      {
        id: 'rule-1',
        name: 'Escape Test Rule',
        type: 'RISK_LIMIT',
        threshold: 250000,
        operator: 'GREATER_THAN',
        severity: 'INFO',
        channels: ['IN_APP'],
        enabled: true,
      },
    ])
    await page.goto('/')

    await page.getByTestId('tab-alerts').click()
    await page.waitForSelector('[data-testid="rules-table"]')
    await page.getByTestId('delete-rule-rule-1').click()
    await page.waitForSelector('[data-testid="confirm-dialog"]')

    // Press Escape
    await page.keyboard.press('Escape')

    // Dialog should be dismissed
    await expect(page.getByTestId('confirm-dialog')).not.toBeVisible()

    // The rule should still be in the table
    await expect(page.getByTestId('rules-table')).toContainText(
      'Escape Test Rule',
    )
  })

  test('confirming delete removes the rule from the table', async ({
    page,
  }) => {
    await mockAlertRuleCrud(page, [
      {
        id: 'rule-1',
        name: 'To Be Deleted',
        type: 'VAR_BREACH',
        threshold: 750000,
        operator: 'GREATER_THAN',
        severity: 'WARNING',
        channels: ['IN_APP'],
        enabled: true,
      },
    ])
    await page.goto('/')

    await page.getByTestId('tab-alerts').click()
    await page.waitForSelector('[data-testid="rules-table"]')

    // Verify rule is present
    await expect(page.getByTestId('rules-table')).toContainText(
      'To Be Deleted',
    )

    // Click delete and confirm
    await page.getByTestId('delete-rule-rule-1').click()
    await page.waitForSelector('[data-testid="confirm-dialog"]')
    await page.getByTestId('confirm-dialog-confirm').click()

    // Dialog should close
    await expect(page.getByTestId('confirm-dialog')).not.toBeVisible()

    // The rule should no longer be in the table
    await expect(page.getByTestId('rules-table')).not.toContainText(
      'To Be Deleted',
    )
  })
})
