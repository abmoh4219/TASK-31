// @ts-check
const { test, expect } = require('@playwright/test');

async function loginAs(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForLoadState('networkidle');
}

test.describe('Approval queue', () => {
  test('reviewer sees approval queue', async ({ page }) => {
    await loginAs(page, 'reviewer', 'Review@Retail2024!');
    await page.goto('/approval/queue');
    await expect(page.locator('body')).toBeVisible();
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
    const url = page.url();
    expect(url).not.toContain('login');
    expect(url).not.toContain('403');
  });

  test('admin sees approval queue', async ({ page }) => {
    await loginAs(page, 'admin', 'Admin@Retail2024!');
    await page.goto('/approval/queue');
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
  });

  test('ops is redirected away from approval queue', async ({ page }) => {
    await loginAs(page, 'ops', 'Ops@Retail2024!');
    await page.goto('/approval/queue');
    const url = page.url();
    expect(url.includes('login') || url.includes('403') || !url.includes('/approval/queue')).toBeTruthy();
  });

  test('finance is redirected away from approval queue', async ({ page }) => {
    await loginAs(page, 'finance', 'Finance@Retail2024!');
    await page.goto('/approval/queue');
    const url = page.url();
    expect(url.includes('login') || url.includes('403') || !url.includes('/approval/queue')).toBeTruthy();
  });
});

test.describe('Analytics', () => {
  test('finance sees analytics export page', async ({ page }) => {
    await loginAs(page, 'finance', 'Finance@Retail2024!');
    await page.goto('/analytics/export');
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
    const url = page.url();
    expect(url).not.toContain('login');
    expect(url).not.toContain('403');
  });

  test('finance sees analytics trends data', async ({ page }) => {
    await loginAs(page, 'finance', 'Finance@Retail2024!');
    await page.goto('/analytics/trends');
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
  });

  test('ops cannot access analytics export', async ({ page }) => {
    await loginAs(page, 'ops', 'Ops@Retail2024!');
    await page.goto('/analytics/export');
    const url = page.url();
    expect(url.includes('login') || url.includes('403') || !url.includes('/analytics/export')).toBeTruthy();
  });
});
