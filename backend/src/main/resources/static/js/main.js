function toggleSpaceTree(spaceId) {
    const treeElement = document.getElementById('tree-' + spaceId);
    if (treeElement) {
        const isVisible = treeElement.style.display !== 'none';
        treeElement.style.display = isVisible ? 'none' : 'block';

        // Save state to localStorage to persist across navigation
        localStorage.setItem('space-tree-' + spaceId, isVisible ? 'hidden' : 'visible');
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
            throw new Error(`HTTP error! status: ${response.status}`);
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
    }, 2000);
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
