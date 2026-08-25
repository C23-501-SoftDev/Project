function toggleSpaceTree(spaceId) {
    const btn = document.querySelector(`.space-button[onclick*="toggleSpaceTree(${spaceId})"]`);
    const spaceName = btn ? btn.textContent.trim() : '';
    localStorage.setItem('selectedSpaceId', String(spaceId));
    localStorage.setItem('selectedSpaceName', spaceName);

    if (window.location.pathname !== '/') {
        localStorage.setItem('space-tree-' + spaceId, 'visible');
        const currentParams = new URLSearchParams(window.location.search);
        currentParams.set('spaceId', spaceId);
        currentParams.delete('page');
        window.location.href = '/?' + currentParams.toString();
        return;
    }

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

    if (window.location.pathname === '/') {
        const urlSpaceId = new URLSearchParams(window.location.search).get('spaceId');
        if (urlSpaceId) {
            setActiveSpaceButton(urlSpaceId);
        }
    }

    initDragAndDrop();
});

function initDragAndDrop() {
    let draggedItem = null;

    const dropIndicatorClasses = ['drop-before', 'drop-inside', 'drop-after'];

    function closestElement(target, selector) {
        return target instanceof Element ? target.closest(selector) : null;
    }

    function clearDropIndicators() {
        document.querySelectorAll(
            '.drop-before, .drop-inside, .drop-after, .space-button.drag-over, ' +
            '.document-tree-end-drop-zone.drag-over'
        )
            .forEach(element => element.classList.remove(...dropIndicatorClasses, 'drag-over'));
    }

    function isValidDocumentTarget(targetItem) {
        return targetItem
            && targetItem !== draggedItem
            && !draggedItem.contains(targetItem);
    }

    function isValidTreeEndTarget(endDropZone) {
        return endDropZone && !draggedItem.contains(endDropZone);
    }

    function getDropMode(row, pointerY) {
        const rect = row.getBoundingClientRect();
        if (rect.height <= 0) return 'inside';

        const relativeY = (pointerY - rect.top) / rect.height;
        if (relativeY < 0.3) return 'before';
        if (relativeY > 0.7) return 'after';
        return 'inside';
    }

    function showDocumentDropIndicator(row, mode) {
        clearDropIndicators();
        row.classList.add(`drop-${mode}`);
    }

    function directDocumentItems(tree) {
        if (!tree) return [];
        return Array.from(tree.children)
            .filter(child => child.classList.contains('document-item'));
    }

    function childDocumentTree(item) {
        return Array.from(item.children)
            .find(child => child.classList.contains('document-tree')) || null;
    }

    function rootDocumentItems(spaceId) {
        const treeContainer = document.getElementById(`tree-${spaceId}`);
        const rootTree = treeContainer
            ? treeContainer.querySelector('.document-tree:not(.nested-document-list)')
            : null;
        return directDocumentItems(rootTree);
    }

    function treeEndDestination(endDropZone, sourceItem) {
        const tree = endDropZone ? endDropZone.closest('.document-tree') : null;
        const items = directDocumentItems(tree);
        const referenceItem = items[0] || null;
        const treeContainer = tree ? tree.closest('[id^="tree-"]') : null;

        return {
            spaceId: referenceItem
                ? referenceItem.dataset.spaceId
                : treeContainer?.id.replace('tree-', ''),
            parentId: referenceItem?.dataset.parentId || null,
            position: items.filter(item => item !== sourceItem).length
        };
    }

    document.addEventListener('dragstart', (e) => {
        const row = closestElement(e.target, '.document-row');
        if (!row) return;

        draggedItem = row.closest('.document-item');
        draggedItem.classList.add('dragging');
        document.body.classList.add('document-drag-active');
        e.dataTransfer.setData('text/plain', draggedItem.dataset.documentId);
        e.dataTransfer.effectAllowed = 'move';
    });

    document.addEventListener('dragend', () => {
        if (draggedItem) {
            draggedItem.classList.remove('dragging');
        }
        draggedItem = null;
        document.body.classList.remove('document-drag-active');
        clearDropIndicators();
    });

    document.addEventListener('dragover', (e) => {
        if (!draggedItem) return;

        const row = closestElement(e.target, '.document-row');
        const targetItem = row ? row.closest('.document-item') : null;
        const endDropZone = closestElement(e.target, '.document-tree-end-drop-zone');
        const spaceButton = closestElement(e.target, '.space-button');

        if (row && isValidDocumentTarget(targetItem)) {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';
            showDocumentDropIndicator(row, getDropMode(row, e.clientY));
        } else if (isValidTreeEndTarget(endDropZone)) {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';
            clearDropIndicators();
            endDropZone.classList.add('drag-over');
        } else if (spaceButton) {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';
            clearDropIndicators();
            spaceButton.classList.add('drag-over');
        } else {
            clearDropIndicators();
        }
    });

    document.addEventListener('drop', async (e) => {
        if (!draggedItem) return;

        const sourceItem = draggedItem;
        const draggedId = e.dataTransfer.getData('text/plain') || sourceItem.dataset.documentId;
        if (!draggedId) return;

        const row = closestElement(e.target, '.document-row');
        const targetItem = row ? row.closest('.document-item') : null;
        const endDropZone = closestElement(e.target, '.document-tree-end-drop-zone');
        const spaceButton = closestElement(e.target, '.space-button');

        let targetSpaceId = null;
        let targetParentId = null;
        let targetPosition = 0;

        if (row && isValidDocumentTarget(targetItem)) {
            const mode = getDropMode(row, e.clientY);
            targetSpaceId = targetItem.dataset.spaceId;

            if (mode === 'inside') {
                targetParentId = targetItem.dataset.documentId;
                targetPosition = directDocumentItems(childDocumentTree(targetItem))
                    .filter(item => item !== sourceItem).length;
            } else {
                targetParentId = targetItem.dataset.parentId || null;
                const siblings = directDocumentItems(targetItem.parentElement)
                    .filter(item => item !== sourceItem);
                const targetIndex = siblings.indexOf(targetItem);
                targetPosition = Math.max(0, targetIndex + (mode === 'after' ? 1 : 0));
            }
        } else if (isValidTreeEndTarget(endDropZone)) {
            const destination = treeEndDestination(endDropZone, sourceItem);
            targetSpaceId = destination.spaceId;
            targetParentId = destination.parentId;
            targetPosition = destination.position;
        } else if (spaceButton) {
            targetSpaceId = spaceButton.dataset.spaceId;
            targetParentId = null;
            targetPosition = rootDocumentItems(targetSpaceId)
                .filter(item => item !== sourceItem).length;
        } else {
            clearDropIndicators();
            return;
        }

        e.preventDefault();
        clearDropIndicators();

        if (targetSpaceId) {
            try {
                showToast('Перемещение документа...');
                await apiFetch(`/api/documents/${draggedId}/move`, {
                    method: 'POST',
                    body: JSON.stringify({
                        spaceId: Number(targetSpaceId),
                        parentId: targetParentId ? Number(targetParentId) : null,
                        position: targetPosition
                    })
                });
                showToast('Документ успешно перемещен');
                setTimeout(() => location.reload(), 800);
            } catch (err) {
                showToast(err.message || 'Ошибка при перемещении документа', 'error');
            }
        }
    });
}


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
        setTimeout(() => location.reload(), 1000);
    } catch (e) {
        console.error('Delete failed:', e);
    }
}

async function restoreDocument(id) {
    try {
        await apiFetch(`/api/documents/${id}/restore`, { method: 'POST' });
        if (typeof showToast === 'function') showToast('Документ восстановлен');
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
    } catch (_) {  }
}

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


(function() {
    let tooltipEl = null;

    function createTooltip() {
        if (tooltipEl) return tooltipEl;
        tooltipEl = document.createElement('div');
        tooltipEl.className = 'global-ui-tooltip';
        tooltipEl.style.cssText = `
            position: fixed;
            background: #1e293b;
            color: #ffffff;
            padding: 6px 12px;
            border-radius: 6px;
            font-size: 13px;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            line-height: 1.4;
            z-index: 99999;
            pointer-events: none;
            box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -1px rgba(0,0,0,0.06);
            opacity: 0;
            transition: opacity 0.15s ease;
            max-width: 320px;
            word-break: break-word;
            white-space: normal;
            display: none;
        `;
        document.body.appendChild(tooltipEl);
        return tooltipEl;
    }

    function findTruncatedElement(el) {
        let current = el;
        while (current && current !== document.body && current !== document.documentElement) {
            const style = window.getComputedStyle(current);
            const overflow = style.overflow || style.overflowX || '';
            const isEllipsis = style.textOverflow === 'ellipsis';
            const isNowrap = style.whiteSpace === 'nowrap';
            
            if (current.scrollWidth > current.clientWidth) {
                if (overflow.includes('hidden') || isEllipsis || isNowrap) {
                    return current;
                }
            }
            current = current.parentElement;
        }
        return null;
    }

    let activeElement = null;

    function showTooltip(el) {
        if (!el) return;
        activeElement = el;
        const tooltip = createTooltip();
        
        tooltip.textContent = el.textContent.trim();
        tooltip.style.display = 'block';
        
        const rect = el.getBoundingClientRect();
        
        tooltip.style.opacity = '0';
        const tooltipWidth = tooltip.offsetWidth;
        const tooltipHeight = tooltip.offsetHeight;
        
        const offset = 8;
        let top = rect.top - tooltipHeight - offset;
        let left = rect.left + (rect.width - tooltipWidth) / 2;
        
        if (top < 10) {
            top = rect.bottom + offset;
        }
        
        if (left < 10) {
            left = 10;
        } else if (left + tooltipWidth > window.innerWidth - 10) {
            left = window.innerWidth - tooltipWidth - 10;
        }
        
        tooltip.style.top = top + 'px';
        tooltip.style.left = left + 'px';
        
        setTimeout(() => {
            if (activeElement === el) {
                tooltip.style.opacity = '1';
            }
        }, 20);
    }

    function hideTooltip() {
        activeElement = null;
        if (tooltipEl) {
            tooltipEl.style.opacity = '0';
            setTimeout(() => {
                if (!activeElement && tooltipEl) {
                    tooltipEl.style.display = 'none';
                }
            }, 150);
        }
    }

    document.addEventListener('mouseover', (e) => {
        const truncated = findTruncatedElement(e.target);
        if (truncated) {
            showTooltip(truncated);
        } else {
            hideTooltip();
        }
    });

    document.addEventListener('mouseout', (e) => {
        if (activeElement) {
            const related = e.relatedTarget;
            if (!related || !activeElement.contains(related)) {
                hideTooltip();
            }
        }
    });

    document.addEventListener('focusin', (e) => {
        const truncated = findTruncatedElement(e.target);
        if (truncated) {
            showTooltip(truncated);
        }
    });

    document.addEventListener('focusout', () => {
        hideTooltip();
    });

    function makeTruncatedElementsFocusable() {
        const selectors = [
            '.space-button',
            '.document-item a',
            '.navbar-menu .user-info',
            '.select-styled',
            '.select-option',
            '.badge',
            '.sidebar-tab',
            '.form-group label',
            '.data-table td:not(.actions)',
            '.text-truncated',
            '.header h1', '.header h2', '.editor-header h2', '.sidebar-header h3', '.modal-content h2', '.modal-content h3', '.login-card h1', '.view-header h1', '#documentListTitle', '#documentListSpaceName'
        ];
        
        document.querySelectorAll(selectors.join(',')).forEach(el => {
            const tag = el.tagName.toLowerCase();
            const naturallyFocusable = ['a', 'button', 'input', 'select', 'textarea'].includes(tag);
            if (!naturallyFocusable && !el.hasAttribute('tabindex')) {
                el.setAttribute('tabindex', '0');
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            makeTruncatedElementsFocusable();
            observeDOM();
        });
    } else {
        makeTruncatedElementsFocusable();
        observeDOM();
    }

    function observeDOM() {
        const observer = new MutationObserver(() => {
            makeTruncatedElementsFocusable();
        });
        observer.observe(document.body, {
            childList: true,
            subtree: true
        });
    }
})();
