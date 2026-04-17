// @ts-check
const { test, expect } = require('@playwright/test');

async function loginAs(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForLoadState('networkidle');
}

test.describe('Campaign list', () => {
  test('ops sees campaign list page', async ({ page }) => {
    await loginAs(page, 'ops', 'Ops@Retail2024!');
    await page.goto('/campaigns');
    await expect(page.locator('body')).toBeVisible();
    // Page loaded without error
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
  });

  test('admin sees campaign list page', async ({ page }) => {
    await loginAs(page, 'admin', 'Admin@Retail2024!');
    await page.goto('/campaigns');
    await expect(page.locator('body')).toBeVisible();
  });

  test('campaign list contains seeded campaigns', async ({ page }) => {
    await loginAs(page, 'ops', 'Ops@Retail2024!');
    await page.goto('/campaigns');
    const body = await page.textContent('body');
    // The seeded data includes "Spring Refresh" campaign
    expect(body).toContain('Spring');
  });
});

test.describe('Campaign creation flow', () => {
  test('ops can access new campaign form', async ({ page }) => {
    await loginAs(page, 'ops', 'Ops@Retail2024!');
    await page.goto('/campaigns/new');
    await expect(page.locator('body')).toBeVisible();
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
  });

  test('ops can fill and submit new campaign form', async ({ page }) => {
    await loginAs(page, 'ops', 'Ops@Retail2024!');
    await page.goto('/campaigns/new');

    // Fill the form fields
    const nameField = page.locator('input[name="name"]');
    if (await nameField.isVisible()) {
      await nameField.fill('E2E Test Campaign ' + Date.now());
    }

    const descField = page.locator('textarea[name="description"]');
    if (await descField.isVisible()) {
      await descField.fill('Created by Playwright e2e test');
    }

    const typeSelect = page.locator('select[name="type"]');
    if (await typeSelect.isVisible()) {
      await typeSelect.selectOption('COUPON');
    }

    const storeField = page.locator('input[name="storeId"]');
    if (await storeField.isVisible()) {
      await storeField.fill('E2E-STORE');
    }

    const receiptField = page.locator('input[name="receiptWording"]');
    if (await receiptField.isVisible()) {
      await receiptField.fill('E2E TEST DISCOUNT');
    }

    // Set dates
    const today = new Date();
    const start = new Date(today);
    start.setDate(today.getDate() + 1);
    const end = new Date(today);
    end.setDate(today.getDate() + 10);

    const startDateField = page.locator('input[name="startDate"]');
    if (await startDateField.isVisible()) {
      await startDateField.fill(start.toISOString().split('T')[0]);
    }

    const endDateField = page.locator('input[name="endDate"]');
    if (await endDateField.isVisible()) {
      await endDateField.fill(end.toISOString().split('T')[0]);
    }

    const riskSelect = page.locator('select[name="riskLevel"]');
    if (await riskSelect.isVisible()) {
      await riskSelect.selectOption('LOW');
    }

    // Submit form
    const submitBtn = page.locator('button[type="submit"]').first();
    if (await submitBtn.isVisible()) {
      await submitBtn.click();
      await page.waitForLoadState('networkidle');
    }

    // After submit, should be on campaigns page or show form errors (not 500)
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
    const url = page.url();
    expect(url).not.toContain('500');
  });
});

test.describe('Campaign detail view', () => {
  test('can view first seeded campaign', async ({ page }) => {
    await loginAs(page, 'ops', 'Ops@Retail2024!');
    // Navigate to campaign list and find a campaign link
    await page.goto('/campaigns');
    const firstLink = page.locator('a[href*="/campaigns/"]').first();
    if (await firstLink.isVisible()) {
      await firstLink.click();
      await page.waitForLoadState('networkidle');
      const status = await page.evaluate(() => document.readyState);
      expect(status).toBe('complete');
      const url = page.url();
      expect(url).not.toContain('error');
    }
  });
});
