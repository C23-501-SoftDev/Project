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
