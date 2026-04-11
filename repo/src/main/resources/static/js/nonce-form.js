/*
 * Browser anti-replay nonce + signature injection.
 *
 * R4 audit HIGH #1 + HIGH #2: NonceValidationFilter and RequestSigningFilter no longer
 * carry blanket bypasses for browser POSTs. Privileged browser submissions must therefore
 * carry both a fresh nonce/timestamp AND a server-issued HMAC signature. Rather than
 * rewriting every admin form to be HTMX/fetch-driven (which would lose Spring's
 * RedirectAttributes flash messages), this script intercepts the form submit, fetches a
 * nonce from the appropriate /admin/nonce or /approval/nonce endpoint, fetches a
 * signature from /admin/sign-form (admin paths only — RequestSigningFilter is scoped to
 * /admin/**), injects _nonce / _timestamp / _signature as hidden fields, and lets the
 * browser perform a normal POST.
 *
 * Why server-side signing? HMAC-SHA256 needs the shared signing secret which can never
 * live in browser JavaScript. The /admin/sign-form endpoint is gated by Spring Security
 * (ROLE_ADMIN + authenticated session + CSRF), so an unauthorized caller cannot obtain a
 * signature. The signature binds (method, path, timestamp, nonce); the body hash is
 * intentionally omitted because the body itself contains _signature (chicken-and-egg).
 * Replay protection comes from the single-use nonce that NonceValidationFilter consumes.
 *
 * Behaviour:
 *   - Auto-arms any <form method="post"> whose action is under /admin/ or
 *     /approval/{id}/approve-first|second|/dual-approve/.
 *   - For /admin/** forms: fetches both nonce and signature.
 *   - For /approval/** forms: fetches nonce only (signing filter is /admin/**-scoped).
 *   - Skips forms tagged data-skip-nonce="true".
 *   - Skips submit buttons tagged data-skip-nonce="true" (e.g. Reject buttons that don't
 *     change campaign state to APPROVED — they fall through to a normal POST).
 *   - Skips logout (handled by Spring Security separately).
 */
(function () {
    var CSRF_TOKEN = (function () {
        var meta = document.querySelector('meta[name="_csrf"]');
        return meta ? meta.getAttribute('content') : '';
    })();
    var CSRF_HEADER = (function () {
        var meta = document.querySelector('meta[name="_csrf_header"]');
        return meta ? meta.getAttribute('content') : 'X-XSRF-TOKEN';
    })();

    function nonceEndpointFor(action) {
        if (action.indexOf('/admin/') === 0) return '/admin/nonce';
        if (action.indexOf('/approval/') === 0) return '/approval/nonce';
        return null;
    }

    function signEndpointFor(action) {
        if (action.indexOf('/admin/') === 0) return '/admin/sign-form';
        // Approval completion endpoints are now covered by RequestSigningFilter —
        // fetch a signature from the approval sign-form endpoint (REVIEWER + ADMIN).
        if (action.indexOf('/approval/') === 0
                && (action.indexOf('/approve-first') !== -1
                    || action.indexOf('/approve-second') !== -1
                    || action.indexOf('/dual-approve/') !== -1)) {
            return '/approval/sign-form';
        }
        return null;
    }

    function needsNonce(form) {
        if (form.method && form.method.toLowerCase() !== 'post') return false;
        if (form.dataset.skipNonce === 'true') return false;
        var action = form.getAttribute('action') || window.location.pathname;
        if (action.indexOf('/logout') === 0) return false;
        if (action.indexOf('/admin/') === 0) return true;
        if (action.indexOf('/approval/') === 0
                && (action.indexOf('/approve-first') !== -1
                    || action.indexOf('/approve-second') !== -1
                    || action.indexOf('/dual-approve/') !== -1)) {
            return true;
        }
        return false;
    }

    function fetchNonce(endpoint) {
        return fetch(endpoint, {
            method: 'GET',
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json' }
        }).then(function (r) {
            if (!r.ok) throw new Error('nonce fetch failed: ' + r.status);
            return r.json();
        });
    }

    function fetchSignature(endpoint, method, path, timestamp, nonce) {
        var headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
        if (CSRF_TOKEN) headers[CSRF_HEADER] = CSRF_TOKEN;
        return fetch(endpoint, {
            method: 'POST',
            credentials: 'same-origin',
            headers: headers,
            body: JSON.stringify({
                method: method,
                path: path,
                timestamp: String(timestamp),
                nonce: nonce
            })
        }).then(function (r) {
            if (!r.ok) throw new Error('signature fetch failed: ' + r.status);
            return r.json();
        });
    }

    function setHidden(form, name, value) {
        var input = form.querySelector('input[type=hidden][name="' + name + '"]');
        if (!input) {
            input = document.createElement('input');
            input.type = 'hidden';
            input.name = name;
            form.appendChild(input);
        }
        input.value = value;
    }

    function arm(form) {
        if (form.dataset.nonceWired === 'true') return;
        form.dataset.nonceWired = 'true';

        var lastSubmitter = null;
        form.querySelectorAll('button[type=submit], input[type=submit]').forEach(function (btn) {
            btn.addEventListener('click', function () { lastSubmitter = btn; });
        });

        form.addEventListener('submit', function (ev) {
            if (lastSubmitter && lastSubmitter.getAttribute('data-skip-nonce') === 'true') {
                return;
            }
            // Use the button's formaction (if set) over the form action so Reject /
            // alternative submit buttons get scoped correctly.
            var action = (lastSubmitter && lastSubmitter.getAttribute('formaction'))
                    || form.getAttribute('action')
                    || window.location.pathname;
            if (form.dataset.nonceArmed === 'true') return;

            var nonceEndpoint = nonceEndpointFor(action);
            if (!nonceEndpoint) return; // not a privileged path — let the browser submit normally
            var signEndpoint = signEndpointFor(action);

            ev.preventDefault();

            fetchNonce(nonceEndpoint).then(function (n) {
                // Inject nonce + timestamp first; if we don't need a signature we can
                // submit immediately.
                setHidden(form, '_nonce', n.nonce);
                setHidden(form, '_timestamp', String(n.timestamp));
                if (!signEndpoint) {
                    return null; // signal "skip signing"
                }
                return fetchSignature(signEndpoint, 'POST', action, n.timestamp, n.nonce);
            }).then(function (sigResp) {
                if (sigResp && sigResp.signature) {
                    setHidden(form, '_signature', sigResp.signature);
                }
                form.dataset.nonceArmed = 'true';
                if (lastSubmitter) {
                    lastSubmitter.click();
                } else {
                    form.submit();
                }
            }).catch(function (err) {
                alert('Could not arm privileged form: ' + err.message);
            });
        });
    }

    function scan(root) {
        (root || document).querySelectorAll('form').forEach(function (form) {
            if (needsNonce(form)) arm(form);
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { scan(document); });
    } else {
        scan(document);
    }
})();
