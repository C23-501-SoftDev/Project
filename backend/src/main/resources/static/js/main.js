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
    const headers = {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': csrfToken,
        ...options.headers
    };

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

function escapeHtml(str) {
    return String(str).replace(/[&<>"']/g, function(m) {
        switch(m) {
            case '&': return '&amp;';
            case '<': return '&lt;';
            case '>': return '&gt;';
            case '"': return '&quot;';
            case "'": return '&#39;';
            default: return m;
        }
    });
}
