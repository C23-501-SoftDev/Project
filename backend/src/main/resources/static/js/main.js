function toggleSpaceTree(spaceId) {
    const btn = document.querySelector(`.space-button[onclick*="toggleSpaceTree(${spaceId})"]`);
    const spaceName = btn ? btn.textContent.trim() : '';
    localStorage.setItem('selectedSpaceId', String(spaceId));
    localStorage.setItem('selectedSpaceName', spaceName);

    // If not on the main documents page — redirect there with the space filter
    if (window.location.pathname !== '/') {
        localStorage.setItem('space-tree-' + spaceId, 'visible');
        // Preserve existing URL params, only override spaceId and reset page
        const currentParams = new URLSearchParams(window.location.search);
        currentParams.set('spaceId', spaceId);
        currentParams.delete('page');
        window.location.href = '/?' + currentParams.toString();
        return;
    }

    // On the main page: toggle tree visibility
    const treeElement = document.getElementById('tree-' + spaceId);
    if (treeElement) {
        const isVisible = treeElement.style.display !== 'none';
        treeElement.style.display = isVisible ? 'none' : 'block';
        localStorage.setItem('space-tree-' + spaceId, isVisible ? 'hidden' : 'visible');
    }

    setActiveSpaceButton(spaceId);

    document.dispatchEvent(new CustomEvent('spaceSelected', {
        detail: { spaceId: spaceId, spaceName: spaceName }
    }));
}

function setActiveSpaceButton(spaceId) {
    document.querySelectorAll('.space-button').forEach(b => b.classList.remove('active'));
    if (spaceId == null) return;
    const target = document.querySelector(`.space-button[onclick*="toggleSpaceTree(${spaceId})"]`);
    if (target) target.classList.add('active');
}

document.addEventListener('DOMContentLoaded', () => {
    // Restore tree expand/collapse state from localStorage
    const allTrees = document.querySelectorAll('[id^="tree-"]');
    allTrees.forEach(tree => {
        const spaceId = tree.id.replace('tree-', '');
        const state = localStorage.getItem('space-tree-' + spaceId);
        if (state === 'visible') {
            tree.style.display = 'block';
        } else if (state === 'hidden') {
            tree.style.display = 'none';
        }
    });

    // Highlight the active space based on ?spaceId= URL param (main page only)
    if (window.location.pathname === '/') {
        const urlSpaceId = new URLSearchParams(window.location.search).get('spaceId');
        if (urlSpaceId) {
            setActiveSpaceButton(urlSpaceId);
        }
    }
});

function getCsrfToken() {
    const name = 'XSRF-TOKEN=';
    const cookies = document.cookie.split(';');
    for (let cookie of cookies) {
        const c = cookie.trim();
        if (c.startsWith(name)) {
            return decodeURIComponent(c.substring(name.length));
        }
    }
    return '';
}

async function apiFetch(url, options = {}) {
    const csrfToken = getCsrfToken();
    const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData;
    const headers = {
        'X-XSRF-TOKEN': csrfToken,
        ...options.headers
    };

    if (!isFormData && !headers['Content-Type'] && !headers['content-type']) {
        headers['Content-Type'] = 'application/json';
    }

    if (isFormData) {
        delete headers['Content-Type'];
        delete headers['content-type'];
    }

    const defaultOptions = {
        headers,
        credentials: 'same-origin',
        ...options
    };

    try {
        const response = await fetch(url, defaultOptions);
        if (!response.ok) {
            const contentType = response.headers.get('content-type');
            let errorMessage = `HTTP error! status: ${response.status}`;
            if (contentType && contentType.includes('application/json')) {
                try {
                    const errorBody = await response.json();
                    if (errorBody.message) {
                        errorMessage = errorBody.message;
                    }
                    if (errorBody.fieldErrors && errorBody.fieldErrors.length > 0) {
                        const fieldMessages = errorBody.fieldErrors
                            .map(fe => fe.message)
                            .join('; ');
                        errorMessage = errorMessage + '. ' + fieldMessages;
                    }
                } catch (e) {
                }
            }
            throw new Error(errorMessage);
        }
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            return await response.json();
        }
        return response;
    } catch (error) {
        console.error('apiFetch error:', error);
        if (typeof showToast === 'function') {
            showToast(error.message || 'Request failed', 'error');
        }
        throw error;
    }
}

async function deleteDocument(id) {
    try {
        await apiFetch(`/api/documents/${id}`, { method: 'DELETE' });
        if (typeof showToast === 'function') showToast('Документ удален');
        // Reload to update the tree sidebar
        setTimeout(() => location.reload(), 1000);
    } catch (e) {
        console.error('Delete failed:', e);
    }
}

async function restoreDocument(id) {
    try {
        await apiFetch(`/api/documents/${id}/restore`, { method: 'POST' });
        if (typeof showToast === 'function') showToast('Документ восстановлен');
        // Reload to update the tree sidebar
        setTimeout(() => location.reload(), 1000);
    } catch (e) {
        console.error('Restore failed:', e);
    }
}

function showToast(message, type = 'success') {
    let toast = document.getElementById('toast');
    if (!toast) {
        toast = document.createElement('div');
        toast.id = 'toast';
        toast.className = 'toast';
        document.body.appendChild(toast);
    }
    toast.textContent = message;
    toast.className = 'toast show';
    if (type === 'error') {
        toast.classList.add('error');
    }
    setTimeout(() => {
        toast.className = 'toast';
    }, 5000);
}

// ── Export → attachment ───────────────────────────────────────────────────────

async function exportAndAttach(docId, format) {
    try {
        showToast(`Экспорт в ${format.toUpperCase()}…`);
        const response = await apiFetch(`/api/documents/${docId}/export/${format}`);
        const blob = await response.blob();
        const cd = response.headers.get('Content-Disposition') || '';
        const m = cd.match(/filename\*=UTF-8''([^;]+)|filename="?([^";\r\n]+)"?/i);
        const filename = m ? decodeURIComponent(m[1] || m[2]) : `document.${format}`;

        const fd = new FormData();
        fd.append('files', new File([blob], filename, { type: blob.type }));
        await apiFetch(`/api/documents/${docId}/attachments`, { method: 'POST', body: fd });

        showToast(`«${filename}» добавлен как вложение`);
        if (typeof window.loadAttachments === 'function') window.loadAttachments();
    } catch (_) { /* apiFetch уже показывает toast */ }
}

// ── Shared floating export dropdown (position:fixed — не обрезается таблицей) ─

let _exportMenuEl = null;

function _getExportMenuEl() {
    if (_exportMenuEl) return _exportMenuEl;
    _exportMenuEl = document.createElement('div');
    _exportMenuEl.style.cssText =
        'display:none; position:fixed; background:#fff; border:1px solid #E5E7EB;' +
        ' border-radius:8px; box-shadow:0 4px 12px rgba(0,0,0,0.12); z-index:9999;' +
        ' overflow:hidden; white-space:nowrap;';
    ['html', 'pdf', 'docx'].forEach((fmt, i) => {
        const a = document.createElement('a');
        a.textContent = fmt.toUpperCase();
        a.href = '#';
        a.style.cssText = 'display:block; padding:8px 18px; font-size:13px; color:#111827; text-decoration:none;'
            + (i ? ' border-top:1px solid #F3F4F6;' : '');
        a.addEventListener('click', (e) => {
            e.preventDefault();
            const id = _exportMenuEl.dataset.docId;
            _exportMenuEl.style.display = 'none';
            if (id) exportAndAttach(Number(id), fmt);
        });
        _exportMenuEl.appendChild(a);
    });
    document.body.appendChild(_exportMenuEl);
    document.addEventListener('click', () => { _exportMenuEl.style.display = 'none'; });
    return _exportMenuEl;
}

function openExportMenu(e, docId) {
    e.stopPropagation();
    const menu = _getExportMenuEl();
    const btn = e.currentTarget || e.target;
    const rect = btn.getBoundingClientRect();
    const sameDoc = menu.dataset.docId === String(docId);
    const wasVisible = sameDoc && menu.style.display !== 'none';
    menu.style.display = 'none';
    if (wasVisible) return;

    menu.dataset.docId = String(docId);
    menu.style.top = (rect.bottom + 4) + 'px';
    menu.style.left = rect.left + 'px';
    menu.style.display = 'block';
    const mr = menu.getBoundingClientRect();
    if (mr.right > window.innerWidth - 8) {
        menu.style.left = (rect.right - mr.width) + 'px';
    }
}

function escapeHtml(str) {
    return String(str).replace(/[&<>"']/g, function (m) {
        switch (m) {
            case '&': return '&amp;';
            case '<': return '&lt;';
            case '>': return '&gt;';
            case '"': return '&quot;';
            case "'": return '&#39;';
            default: return m;
        }
    });
}
