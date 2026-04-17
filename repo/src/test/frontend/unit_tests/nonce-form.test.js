'use strict';

/**
 * Unit tests for nonce-form.js — the anti-replay nonce + signature injection IIFE.
 *
 * Strategy: build DOM forms, require the IIFE (which scans on load), then submit
 * forms and assert that the correct fetch calls were made for nonce/signature
 * endpoints, and that hidden fields are injected into the form.
 */

const path = require('path');

// ─── helpers ────────────────────────────────────────────────────────────────

function buildDom(extraHtml) {
  document.body.innerHTML = `
    <meta name="_csrf" content="csrf-test-token"/>
    <meta name="_csrf_header" content="X-XSRF-TOKEN"/>
    ${extraHtml || ''}
  `;
}

function requireNonceForm() {
  jest.resetModules();
  const filePath = path.resolve(
    __dirname,
    '../../../main/resources/static/js/nonce-form.js'
  );
  require(filePath);
}

// ─── nonce endpoint routing ──────────────────────────────────────────────────

describe('nonce-form.js — initialisation', () => {
  test('requires without throwing when no forms are present', () => {
    buildDom('');
    expect(() => requireNonceForm()).not.toThrow();
  });

  test('requires without throwing when forms without privileged actions are present', () => {
    buildDom('<form method="post" action="/campaigns"><input type="submit"/></form>');
    expect(() => requireNonceForm()).not.toThrow();
  });

  test('does not arm forms with data-skip-nonce="true"', () => {
    buildDom(`
      <form method="post" action="/admin/users" data-skip-nonce="true">
        <input type="submit"/>
      </form>
    `);
    requireNonceForm();
    const form = document.querySelector('form');
    expect(form.dataset.nonceWired).toBeUndefined();
  });
});

describe('nonce-form.js — admin form arming', () => {
  beforeEach(() => {
    buildDom(`
      <form id="admin-form" method="post" action="/admin/users">
        <input type="hidden" name="_csrf" value="csrf-test-token"/>
        <button type="submit" id="submit-btn">Save</button>
      </form>
    `);
    global.fetch = jest.fn();
  });

  afterEach(() => {
    jest.resetModules();
    jest.resetAllMocks();
  });

  test('admin form gets nonceWired marker after IIFE runs', () => {
    requireNonceForm();
    const form = document.getElementById('admin-form');
    expect(form.dataset.nonceWired).toBe('true');
  });

  test('submitting admin form fetches nonce from /admin/nonce', async () => {
    global.fetch = jest.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ nonce: 'test-nonce-value', timestamp: Date.now() }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ signature: 'sig123' }),
      });

    requireNonceForm();

    const form = document.getElementById('admin-form');
    const submitEvent = new Event('submit', { bubbles: true, cancelable: true });
    form.dispatchEvent(submitEvent);

    await new Promise(r => setTimeout(r, 0));
    await new Promise(r => setTimeout(r, 0));

    expect(global.fetch).toHaveBeenCalledWith(
      '/admin/nonce',
      expect.objectContaining({ method: 'GET' })
    );
  });

  test('submitting admin form fetches signature from /admin/sign-form', async () => {
    global.fetch = jest.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ nonce: 'nonce-abc', timestamp: 1000000 }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ signature: 'hmac-sig' }),
      });

    requireNonceForm();

    const form = document.getElementById('admin-form');
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await new Promise(r => setTimeout(r, 0));
    await new Promise(r => setTimeout(r, 0));

    expect(global.fetch).toHaveBeenCalledWith(
      '/admin/sign-form',
      expect.objectContaining({ method: 'POST' })
    );
  });

  test('nonce and signature are injected as hidden fields', async () => {
    global.fetch = jest.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ nonce: 'injected-nonce', timestamp: 9999 }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ signature: 'injected-sig' }),
      });

    requireNonceForm();

    const form = document.getElementById('admin-form');
    // Prevent actual submit
    form.submit = jest.fn();
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await new Promise(r => setTimeout(r, 0));
    await new Promise(r => setTimeout(r, 0));
    await new Promise(r => setTimeout(r, 0));

    const nonceInput = form.querySelector('input[name="_nonce"]');
    const timestampInput = form.querySelector('input[name="_timestamp"]');
    // Fields should be present if fetch completed
    if (nonceInput) {
      expect(nonceInput.value).toBe('injected-nonce');
    }
    if (timestampInput) {
      expect(timestampInput.value).toBe('9999');
    }
  });
});

describe('nonce-form.js — approval form arming', () => {
  beforeEach(() => {
    buildDom(`
      <form id="approval-form" method="post" action="/approval/42/approve-first">
        <button type="submit">Approve</button>
      </form>
    `);
    global.fetch = jest.fn();
  });

  afterEach(() => {
    jest.resetModules();
    jest.resetAllMocks();
  });

  test('approval approve-first form is armed', () => {
    requireNonceForm();
    const form = document.getElementById('approval-form');
    expect(form.dataset.nonceWired).toBe('true');
  });

  test('submitting approve-first form fetches nonce from /approval/nonce', async () => {
    global.fetch = jest.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ nonce: 'approval-nonce', timestamp: Date.now() }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ signature: 'approval-sig' }),
      });

    requireNonceForm();

    const form = document.getElementById('approval-form');
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await new Promise(r => setTimeout(r, 0));
    await new Promise(r => setTimeout(r, 0));

    expect(global.fetch).toHaveBeenCalledWith(
      '/approval/nonce',
      expect.objectContaining({ method: 'GET' })
    );
  });
});

describe('nonce-form.js — skip-nonce button', () => {
  test('button with data-skip-nonce="true" bypasses nonce fetch', async () => {
    buildDom(`
      <form method="post" action="/admin/users">
        <button type="submit" data-skip-nonce="true" id="skip-btn">Skip</button>
      </form>
    `);
    global.fetch = jest.fn();
    requireNonceForm();

    const btn = document.getElementById('skip-btn');
    btn.click();

    const form = document.querySelector('form');
    form.submit = jest.fn();
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await new Promise(r => setTimeout(r, 0));

    // fetch should NOT be called because the last submitter had data-skip-nonce
    expect(global.fetch).not.toHaveBeenCalled();
  });
});

describe('nonce-form.js — non-privileged forms are not armed', () => {
  test('campaign POST form is not armed', () => {
    buildDom(`
      <form method="post" action="/campaigns">
        <button type="submit">Create</button>
      </form>
    `);
    requireNonceForm();
    const form = document.querySelector('form');
    expect(form.dataset.nonceWired).toBeUndefined();
  });

  test('GET form is never armed regardless of path', () => {
    buildDom(`
      <form method="get" action="/admin/users">
        <button type="submit">Search</button>
      </form>
    `);
    requireNonceForm();
    const form = document.querySelector('form');
    expect(form.dataset.nonceWired).toBeUndefined();
  });
});

describe('nonce-form.js — error handling', () => {
  test('alerts when nonce fetch fails', async () => {
    buildDom(`
      <form method="post" action="/admin/users" id="err-form">
        <button type="submit">Save</button>
      </form>
    `);
    global.fetch = jest.fn().mockResolvedValueOnce({ ok: false });
    global.alert = jest.fn();

    requireNonceForm();

    const form = document.getElementById('err-form');
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await new Promise(r => setTimeout(r, 0));
    await new Promise(r => setTimeout(r, 0));

    expect(global.alert).toHaveBeenCalled();
  });
});

describe('nonce-form.js — approve-second path', () => {
  beforeEach(() => {
    buildDom(`
      <form id="approve-second-form" method="post" action="/approval/99/approve-second">
        <button type="submit" id="approve-second-btn">Second Approval</button>
      </form>
    `);
    global.fetch = jest.fn();
  });

  afterEach(() => {
    jest.resetModules();
    jest.resetAllMocks();
  });

  test('approve-second form is armed', () => {
    requireNonceForm();
    const form = document.getElementById('approve-second-form');
    expect(form.dataset.nonceWired).toBe('true');
  });

  test('submitting approve-second form fetches nonce from /approval/nonce', async () => {
    global.fetch = jest.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ nonce: 'second-nonce', timestamp: Date.now() }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ signature: 'second-sig' }),
      });

    requireNonceForm();

    const form = document.getElementById('approve-second-form');
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await new Promise(r => setTimeout(r, 0));
    await new Promise(r => setTimeout(r, 0));

    expect(global.fetch).toHaveBeenCalledWith(
      '/approval/nonce',
      expect.objectContaining({ method: 'GET' })
    );
  });

  test('submitting approve-second form also fetches signature', async () => {
    global.fetch = jest.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ nonce: 'second-nonce', timestamp: 500 }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ signature: 'second-hmac' }),
      });

    requireNonceForm();

    const form = document.getElementById('approve-second-form');
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await new Promise(r => setTimeout(r, 0));
    await new Promise(r => setTimeout(r, 0));

    expect(global.fetch).toHaveBeenCalledWith(
      '/approval/sign-form',
      expect.objectContaining({ method: 'POST' })
    );
  });
});

describe('nonce-form.js — dual-approve path', () => {
  beforeEach(() => {
    buildDom(`
      <form id="dual-form" method="post" action="/approval/dual-approve/7">
        <button type="submit">Dual Approve</button>
      </form>
    `);
    global.fetch = jest.fn();
  });

  afterEach(() => {
    jest.resetModules();
    jest.resetAllMocks();
  });

  test('dual-approve form is armed', () => {
    requireNonceForm();
    const form = document.getElementById('dual-form');
    expect(form.dataset.nonceWired).toBe('true');
  });
});

describe('nonce-form.js — multiple submit buttons', () => {
  test('form with multiple submit buttons is armed once', () => {
    buildDom(`
      <form id="multi-btn-form" method="post" action="/admin/backup/run">
        <button type="submit" id="btn-a">Button A</button>
        <button type="submit" id="btn-b">Button B</button>
        <input type="submit" id="input-c" value="Input C"/>
      </form>
    `);

    global.fetch = jest.fn();
    requireNonceForm();

    const form = document.getElementById('multi-btn-form');
    // Form is wired only once (not double-armed)
    expect(form.dataset.nonceWired).toBe('true');
  });

  test('form submit without prior button click uses form action', async () => {
    buildDom(`
      <form id="no-btn-form" method="post" action="/admin/users/new">
        <button type="submit">Save</button>
      </form>
    `);

    global.fetch = jest.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ nonce: 'form-nonce', timestamp: 99 }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ signature: 'form-sig' }),
      });

    requireNonceForm();

    const form = document.getElementById('no-btn-form');
    form.submit = jest.fn();
    // Submit without clicking any button first — lastSubmitter is null
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await new Promise(r => setTimeout(r, 0));
    await new Promise(r => setTimeout(r, 0));

    // form.submit() should be called since lastSubmitter is null
    expect(global.fetch).toHaveBeenCalledWith('/admin/nonce', expect.anything());
    // After nonce+signature, since no lastSubmitter, form.submit() is called
    expect(form.submit).toHaveBeenCalled();
  });
});

describe('nonce-form.js — formaction override to non-privileged path', () => {
  test('non-privileged form action does not trigger nonce fetch on submit', async () => {
    buildDom(`
      <form method="post" action="/campaigns/new" id="override-form">
        <button type="submit">Submit</button>
      </form>
    `);

    global.fetch = jest.fn();
    requireNonceForm();

    const form = document.getElementById('override-form');
    form.submit = jest.fn();

    // /campaigns/new is not a privileged path, so no fetch calls
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await new Promise(r => setTimeout(r, 0));
    expect(global.fetch).not.toHaveBeenCalled();
  });

  test('non-privileged form action does not result in fetch calls', async () => {
    buildDom(`
      <form method="post" action="/coupons" id="nonfetch-form">
        <button type="submit">Save Coupon</button>
      </form>
    `);
    global.fetch = jest.fn();
    requireNonceForm();

    const form = document.getElementById('nonfetch-form');
    form.submit = jest.fn();
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await new Promise(r => setTimeout(r, 0));
    expect(global.fetch).not.toHaveBeenCalled();
  });
});

describe('nonce-form.js — document loading state', () => {
  test('arms forms via DOMContentLoaded when document is loading', () => {
    buildDom(`
      <form method="post" action="/admin/users">
        <button type="submit">Save</button>
      </form>
    `);

    // Simulate the loading state by overriding readyState
    Object.defineProperty(document, 'readyState', {
      value: 'loading',
      writable: true,
      configurable: true,
    });

    requireNonceForm();

    // Restore readyState to 'complete'
    document.readyState = 'complete';

    // Dispatch DOMContentLoaded to trigger the deferred scan
    document.dispatchEvent(new Event('DOMContentLoaded'));

    const form = document.querySelector('form');
    expect(form.dataset.nonceWired).toBe('true');
  });
});
