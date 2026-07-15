javascript
    function updateUrl() {
        const params = new URLSearchParams();
        if (state.page > 0) params.set('page', state.page);
        if (state.spaceFilter) params.set('spaceId', state.spaceFilter);
        if (state.searchTerm) params.set('q', state.searchTerm);
if (state.authorFilter) params.set('authorId', state.authorFilter);
if (state.statusFilters.length > 0 && state.statusFilters.length < 3) {
    params.set('status', state.statusFilters.join(','));
}
if (state.sortBy !== 'id') params.set('sortBy', state.sortBy);
if (state.sortDir !== 'desc') params.set('sortDir', state.sortDir);
        const newUrl = window.location.pathname + (params.toString() ? '?' + params.toString() : '');
        history.replaceState(null, '', newUrl);
    }
