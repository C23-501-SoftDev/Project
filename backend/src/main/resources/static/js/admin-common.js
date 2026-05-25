/**
 * Admin Common Utilities
 * Shared functions for admin panel pages
 */

/**
 * Extract XSRF-TOKEN from document.cookie
 */
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

/**
 * Wrapper for fetch with XSRF token and error handling
 */
async function adminFetch(url, options = {}) {
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
            if (response.status === 409) {
                throw new Error('Conflict: ' + response.statusText);
            }
            if (response.status === 403) {
                throw new Error('Access denied');
            }
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            return await response.json();
        }
        return response;
    } catch (error) {
        console.error('adminFetch error:', error);
        showToast(error.message || 'Request failed', 'error');
        throw error;
    }
}

/**
 * Show toast notification (auto-hide after 2s)
 * @param {string} message - Notification text
 * @param {string} type - 'success' or 'error'
 */
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

/**
 * Open modal by ID
 * @param {string} id - Modal element ID
 */
function openModal(id) {
    const modal = document.getElementById(id);
    if (modal) {
        modal.style.display = 'flex';
        setTimeout(() => modal.classList.add('show'), 10);
    }
}

/**
 * Close modal by ID
 * @param {string} id - Modal element ID
 */
function closeModal(id) {
    const modal = document.getElementById(id);
    if (modal) {
        modal.classList.remove('show');
        setTimeout(() => {
            if (!modal.classList.contains('show')) {
                modal.style.display = 'none';
            }
        }, 200);
    }
}

/**
 * Escape HTML to prevent XSS
 * @param {string} str - Input string
 * @returns {string} - Escaped HTML string
 */
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

/**
 * Close modals on overlay click or Escape key
 */
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        document.querySelectorAll('.modal.show').forEach(modal => {
            closeModal(modal.id);
        });
    }
});

document.addEventListener('click', (e) => {
    if (e.target.classList.contains('modal')) {
        closeModal(e.target.id);
    }
});
