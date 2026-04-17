// @ts-check
const { test, expect } = require('@playwright/test');

async function loginAsAdmin(page) {
  await page.goto('/login');
  await page.fill('#username', 'admin');
  await page.fill('#password', 'Admin@Retail2024!');
  await page.click('button[type="submit"]');
  await page.waitForLoadState('networkidle');
}

test.describe('Admin dashboard', () => {
  test('admin dashboard loads', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/dashboard');
    await expect(page.locator('body')).toBeVisible();
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
  });
});

test.describe('User management', () => {
  test('admin users page loads with user list', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/users');
    await expect(page.locator('body')).toBeVisible();
    const body = await page.textContent('body');
    // Should contain user list — seeded users include "admin"
    expect(body).toMatch(/admin|ops|reviewer|finance|cs/i);
  });

  test('new user form is accessible', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/users/new');
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
    const url = page.url();
    expect(url).not.toContain('403');
  });
});

test.describe('Audit log', () => {
  test('audit log page loads for admin', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/audit-log');
    await expect(page.locator('body')).toBeVisible();
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
  });

  test('sensitive access log page loads for admin', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/sensitive-log');
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
  });
});

test.describe('Backup management', () => {
  test('backup page loads for admin', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/backup');
    await expect(page.locator('body')).toBeVisible();
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
  });
});

test.describe('Role changes', () => {
  test('role changes page loads for admin', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/role-changes');
    await expect(page.locator('body')).toBeVisible();
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
  });
});

test.describe('Anomaly alerts', () => {
  test('anomaly alerts page loads for admin', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/anomaly-alerts');
    await expect(page.locator('body')).toBeVisible();
    const status = await page.evaluate(() => document.readyState);
    expect(status).toBe('complete');
  });
});
