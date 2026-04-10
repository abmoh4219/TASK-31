// Chunked upload client. Reads the selected File, splits it into 1 MB chunks,
// POSTs each chunk to /files/upload/chunk, polls /status until complete, then finalises.

(function () {
    const CHUNK_SIZE = 1024 * 1024;

    const zone     = document.getElementById('upload-zone');
    const input    = document.getElementById('file-input');
    const progress = document.getElementById('upload-progress');
    const bar      = document.getElementById('upload-bar');
    const counter  = document.getElementById('chunk-counter');
    const nameEl   = document.getElementById('uploading-name');
    const finalDiv = document.getElementById('finalized');
    const shaEl    = document.getElementById('sha256');
    const statusBadge = document.getElementById('upload-status-badge');

    if (!zone) return;

    const csrfToken  = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    zone.addEventListener('click', () => input.click());
    zone.addEventListener('dragover', e => { e.preventDefault(); zone.classList.add('dragover'); });
    zone.addEventListener('dragleave', () => zone.classList.remove('dragover'));
    zone.addEventListener('drop', e => {
        e.preventDefault();
        zone.classList.remove('dragover');
        if (e.dataTransfer.files.length) handleFile(e.dataTransfer.files[0]);
    });
    input.addEventListener('change', e => {
        if (e.target.files.length) handleFile(e.target.files[0]);
    });

    async function handleFile(file) {
        nameEl.textContent = file.name;
        progress.classList.remove('d-none');
        finalDiv.classList.add('d-none');
        statusBadge.textContent = 'UPLOADING';
        statusBadge.className = 'status-badge status-pending';
        bar.style.width = '0%';
        bar.textContent = '0%';

        const totalChunks = Math.max(1, Math.ceil(file.size / CHUNK_SIZE));
        counter.textContent = '0 of ' + totalChunks + ' chunks received';

        // Init
        const initParams = new URLSearchParams();
        initParams.append('campaignId', window.RC_CAMPAIGN_ID);
        initParams.append('filename', file.name);
        initParams.append('totalChunks', totalChunks);

        const initRes = await fetch('/files/upload/init', {
            method: 'POST',
            headers: { [csrfHeader]: csrfToken, 'Content-Type': 'application/x-www-form-urlencoded' },
            body: initParams
        });
        if (!initRes.ok) { failed('Init failed'); return; }
        const init = await initRes.json();
        const uploadId = init.uploadId;

        // Send chunks sequentially
        for (let i = 0; i < totalChunks; i++) {
            const start = i * CHUNK_SIZE;
            const end = Math.min(start + CHUNK_SIZE, file.size);
            const blob = file.slice(start, end);
            const fd = new FormData();
            fd.append('chunk', blob, 'chunk-' + i);

            const r = await fetch('/files/upload/chunk', {
                method: 'POST',
                headers: { [csrfHeader]: csrfToken, 'Upload-Id': uploadId, 'Chunk-Index': String(i) },
                body: fd
            });
            if (!r.ok) { failed('Chunk ' + i + ' upload failed'); return; }
            const status = await r.json();
            updateProgress(status.progress, status.receivedChunks.length, totalChunks);
        }

        // Finalize — forward the visibility flags selected in the upload form.
        const internalOnly = document.getElementById('internalOnly');
        const maskedSelect = document.getElementById('maskedRoles');
        const finParams = new URLSearchParams();
        finParams.append('internalOnly', internalOnly && internalOnly.checked ? 'true' : 'false');
        if (maskedSelect) {
            Array.from(maskedSelect.selectedOptions).forEach(o => finParams.append('maskedRoles', o.value));
        }

        const fin = await fetch('/files/upload/finalize/' + uploadId, {
            method: 'POST',
            headers: {
                [csrfHeader]: csrfToken,
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: finParams
        });
        if (!fin.ok) { failed('Finalize failed'); return; }
        const result = await fin.json();
        statusBadge.textContent = 'COMPLETE';
        statusBadge.className = 'status-badge status-active';
        finalDiv.classList.remove('d-none');
        shaEl.textContent = result.sha256;

        // Reload after a short delay so the version history table refreshes.
        setTimeout(() => window.location.reload(), 1500);
    }

    function updateProgress(progressPct, received, total) {
        bar.style.width = progressPct + '%';
        bar.textContent = progressPct + '%';
        counter.textContent = received + ' of ' + total + ' chunks received';
    }

    function failed(msg) {
        statusBadge.textContent = 'FAILED';
        statusBadge.className = 'status-badge status-rejected';
        counter.textContent = msg;
    }
})();
