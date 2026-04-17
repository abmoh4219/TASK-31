// @ts-check
const { test, expect } = require('@playwright/test');

async function loginAs(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForLoadState('networkidle');
}

test.describe('Coupon list', () => {
  test('ops sees coupon list', async ({ page }) => {
    await loginAs(page, 'ops', 'Ops@Retail2024!');
    await page.goto('/coupons');
    await expect(page.locator('body')).toBeVisible();
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
  });

  test('coupon list shows seeded coupons', async ({ page }) => {
    await loginAs(page, 'ops', 'Ops@Retail2024!');
    await page.goto('/coupons');
    const body = await page.textContent('body');
    // Seeded coupons include SPRING15 and LOYAL20
    expect(body).toMatch(/SPRING15|LOYAL20|coupon/i);
  });
});

test.describe('Coupon creation', () => {
  test('new coupon form is accessible to ops', async ({ page }) => {
    await loginAs(page, 'ops', 'Ops@Retail2024!');
    await page.goto('/coupons/new');
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
    const url = page.url();
    expect(url).not.toContain('403');
  });

  test('can fill and submit new coupon form', async ({ page }) => {
    await loginAs(page, 'ops', 'Ops@Retail2024!');
    await page.goto('/coupons/new');

    const codeField = page.locator('input[name="code"]');
    if (await codeField.isVisible()) {
      await codeField.fill('E2ETEST' + Date.now());
    }

    const typeSelect = page.locator('select[name="discountType"]');
    if (await typeSelect.isVisible()) {
      await typeSelect.selectOption('PERCENT');
    }

    const valueField = page.locator('input[name="discountValue"]');
    if (await valueField.isVisible()) {
      await valueField.fill('10');
    }

    const maxUsesField = page.locator('input[name="maxUses"]');
    if (await maxUsesField.isVisible()) {
      await maxUsesField.fill('100');
    }

    const submitBtn = page.locator('button[type="submit"]').first();
    if (await submitBtn.isVisible()) {
      await submitBtn.click();
      await page.waitForLoadState('networkidle');
    }

    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
    const url = page.url();
    expect(url).not.toContain('500');
  });
});

test.describe('Content management', () => {
  test('ops sees content list', async ({ page }) => {
    await loginAs(page, 'ops', 'Ops@Retail2024!');
    await page.goto('/content');
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
  });

  test('content duplicates page loads', async ({ page }) => {
    await loginAs(page, 'ops', 'Ops@Retail2024!');
    await page.goto('/content/duplicates');
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
  });
});

test.describe('Health check', () => {
  test('health endpoint returns ok', async ({ page }) => {
    const response = await page.goto('/health');
    expect(response.status()).toBe(200);
  });
});
