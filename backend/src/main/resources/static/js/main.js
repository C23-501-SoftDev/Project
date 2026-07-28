function toggleSpaceTree(spaceId) {
    const treeElement = document.getElementById('tree-' + spaceId);
    if (treeElement) {
        const isVisible = treeElement.style.display !== 'none';
        treeElement.style.display = isVisible ? 'none' : 'block';

        // Save state to localStorage to persist across navigation
        localStorage.setItem('space-tree-' + spaceId, isVisible ? 'hidden' : 'visible');
    }

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
}

document.addEventListener('DOMContentLoaded', () => {
    // Restore state from localStorage
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

    initSpaceSidebarSearch();
});

function initSpaceSidebarSearch() {
    const sidebar = document.getElementById('spacesSidebar');
    const form = document.getElementById('spaceSearchForm');
    const input = document.getElementById('spaceSearchInput');
    const treeContainer = document.getElementById('spaceTreeContainer');
    const resultsContainer = document.getElementById('spaceSearchResults');
    const statusElement = document.getElementById('spaceSearchStatus');

    if (!sidebar || !form || !input || !treeContainer || !resultsContainer || !statusElement) {
        return;
    }

    let requestCounter = 0;
    let debounceTimer = null;

    function showDefaultState() {
        treeContainer.hidden = false;
        resultsContainer.hidden = true;
        resultsContainer.innerHTML = '';
        statusElement.hidden = true;
        statusElement.textContent = '';
        sidebar.classList.remove('sidebar-search-active');
    }

    function showLoading() {
        treeContainer.hidden = true;
        resultsContainer.hidden = false;
        resultsContainer.innerHTML = '';
        statusElement.hidden = false;
        statusElement.textContent = 'Поиск пространств...';
        sidebar.classList.add('sidebar-search-active');
    }

    function showResults(spaces, query) {
        treeContainer.hidden = true;
        resultsContainer.hidden = false;
        sidebar.classList.add('sidebar-search-active');

        if (!spaces || spaces.length === 0) {
            statusElement.hidden = false;
            statusElement.textContent = 'Пространства не найдены';
            resultsContainer.innerHTML = '';
            return;
        }

        statusElement.hidden = false;
        statusElement.textContent = `Найдено: ${spaces.length}`;
        resultsContainer.innerHTML = spaces.map(space => `
            <a class="space-search-result" href="/spaces/${space.id}">
                <span class="space-search-result-name">${escapeHtml(space.name)}</span>
                ${space.description ? `<span class="space-search-result-description">${escapeHtml(space.description)}</span>` : ''}
            </a>
        `).join('');
    }

    async function searchSpaces(query) {
        const normalizedQuery = query.trim();
        requestCounter += 1;
        const currentRequest = requestCounter;

        if (!normalizedQuery) {
            showDefaultState();
            return;
        }

        showLoading();
        try {
            const spaces = await apiFetch(`/api/spaces/search?q=${encodeURIComponent(normalizedQuery)}&size=100`);
            if (currentRequest !== requestCounter) {
                return;
            }
            showResults(Array.isArray(spaces) ? spaces : [], normalizedQuery);
        } catch (error) {
            if (currentRequest !== requestCounter) {
                return;
            }
            statusElement.hidden = false;
            statusElement.textContent = 'Ошибка поиска';
            resultsContainer.hidden = true;
            sidebar.classList.add('sidebar-search-active');
        }
    }

    form.addEventListener('submit', (event) => {
        event.preventDefault();
        searchSpaces(input.value);
    });

    input.addEventListener('input', () => {
        clearTimeout(debounceTimer);
        const value = input.value;

        if (!value.trim()) {
            requestCounter += 1;
            showDefaultState();
            return;
        }

        debounceTimer = setTimeout(() => {
            searchSpaces(value);
        }, 250);
    });

    input.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') {
            input.value = '';
            requestCounter += 1;
            showDefaultState();
        }
    });

    if (!input.value.trim()) {
        showDefaultState();
    } else {
        searchSpaces(input.value);
    }
}

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

// ── Экспорт — прямая загрузка ────────────────────────────────────────────

async function triggerExportDownload(docId, format) {
  try {
    showToast(`Подготовка ${format.toUpperCase()}…`);
    const response = await apiFetch(`/api/documents/${docId}/export/${format}`);
    const blob = await response.blob();

    // Парсим Content-Disposition: сначала filename*=UTF-8''..., затем filename="..."
    const cd = response.headers.get('Content-Disposition') || '';
    let filename = null;
    const rfcMatch = cd.match(/filename\*=UTF-8''([^;\s]+)/i);
    if (rfcMatch) {
      try { filename = decodeURIComponent(rfcMatch[1]); } catch (_) {}
    }
    if (!filename) {
      const plainMatch = cd.match(/filename="([^"]+)"/i);
      if (plainMatch) filename = plainMatch[1];
    }
    // Если заголовок не доступен из JS — берём название документа из DOM
    if (!filename) {
      const titleEl = document.getElementById('docTitle');
      const docTitle = titleEl ? titleEl.textContent.trim() : '';
      filename = (docTitle || `document`) + '.' + format;
    }

    const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);

    showToast(`«${filename}» загружен`);
  } catch (err) {
    console.error('Export download failed:', err);
    if (typeof showToast === 'function') {
      showToast('Ошибка при скачивании файла экспорта', 'error');
    }
  }
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
            if (id) triggerExportDownload(Number(id), fmt);
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

