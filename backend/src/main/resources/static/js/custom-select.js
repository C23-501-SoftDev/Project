(function () {
    const DEBOUNCE_MS = 300;
    const DEFAULT_LIST_SOURCE = '/api/admin/users';

    function getHiddenInput(wrapper) {
        const id = wrapper.dataset.name || wrapper.dataset.target;
        return id ? document.getElementById(id) : null;
    }

    function getOptionsDiv(wrapper) {
        return wrapper.querySelector('.select-options');
    }

    function getDropdownPanel(wrapper) {
        return wrapper.querySelector('.select-panel') || getOptionsDiv(wrapper);
    }

    function isSearchable(wrapper) {
        return wrapper.dataset.searchable === 'true' || !!wrapper.dataset.searchSource;
    }

    function hasServerSearch(wrapper) {
        return !!wrapper.dataset.searchSource;
    }

    window.applyCustomSelectFilter = (wrapper, query) => {
        if (!wrapper) return;
        const optionsDiv = getOptionsDiv(wrapper);
        if (!optionsDiv) return;
        const q = (query || '').trim().toLowerCase();
        optionsDiv.querySelectorAll('.select-option').forEach(opt => {
            if (opt.classList.contains('select-message')) return;
            const txt = (opt.dataset.search || opt.textContent || '').toLowerCase();
            opt.style.display = q === '' || txt.includes(q) ? '' : 'none';
        });
    };

    window.resetCustomSelectFilter = (wrapper) => {
        if (!wrapper) return;
        const filterInput = wrapper.querySelector('.select-filter');
        if (filterInput) filterInput.value = '';
        window.applyCustomSelectFilter(wrapper, '');
    };

    function syncFilterInputs(wrapper, query, source) {
        const styled = wrapper.querySelector('.select-styled');
        const filterInput = wrapper.querySelector('.select-filter');
        if (source !== 'styled' && styled && styled.contentEditable === 'true') {
            const placeholder = styled.dataset.placeholder || '';
            if (query || !hiddenInputHasValue(wrapper)) {
                styled.textContent = query;
            } else if (!query) {
                styled.textContent = placeholder;
            }
        }
        if (source !== 'filter' && filterInput && filterInput.value !== query) {
            filterInput.value = query;
        }
    }

    function hiddenInputHasValue(wrapper) {
        const hidden = getHiddenInput(wrapper);
        return hidden && hidden.value !== '';
    }

    function ensureSearchableStructure(wrapper) {
        if (!isSearchable(wrapper)) return;
        const optionsDiv = getOptionsDiv(wrapper);
        if (!optionsDiv) return;

        let panel = wrapper.querySelector('.select-panel');
        if (!panel) {
            panel = document.createElement('div');
            panel.className = 'select-panel';
            wrapper.insertBefore(panel, optionsDiv);
            panel.appendChild(optionsDiv);
        }

        let searchWrap = panel.querySelector('.select-search');
        if (!searchWrap) {
            searchWrap = document.createElement('div');
            searchWrap.className = 'select-search';
            panel.insertBefore(searchWrap, optionsDiv);
        }

        let filterInput = searchWrap.querySelector('.select-filter');
        if (!filterInput) {
            filterInput = document.createElement('input');
            filterInput.type = 'text';
            filterInput.className = 'select-filter';
            filterInput.placeholder = 'Фильтр...';
            filterInput.setAttribute('autocomplete', 'off');
            searchWrap.appendChild(filterInput);
        }
    }

    function showDropdown(wrapper) {
        const panel = getDropdownPanel(wrapper);
        if (panel) panel.style.display = 'block';
        const styled = wrapper.querySelector('.select-styled');
        if (styled) styled.classList.add('active');
    }

    function hideDropdown(wrapper) {
        const panel = getDropdownPanel(wrapper);
        if (panel) panel.style.display = 'none';
        const styled = wrapper.querySelector('.select-styled');
        if (styled) styled.classList.remove('active');
    }

    function closeAllDropdowns(exceptWrapper) {
        document.querySelectorAll('.custom-select').forEach(wrapper => {
            if (wrapper !== exceptWrapper) hideDropdown(wrapper);
        });
    }

    function setSelectMessage(wrapper, text) {
        const optionsDiv = getOptionsDiv(wrapper);
        if (!optionsDiv) return;
        optionsDiv.querySelectorAll('.select-message').forEach(el => el.remove());
        if (!text) return;
        const msg = document.createElement('div');
        msg.className = 'select-option select-message';
        msg.textContent = text;
        msg.style.display = '';
        optionsDiv.appendChild(msg);
    }

    function passesOptionFilter(wrapper, user) {
        const filter = wrapper.dataset.searchFilter;
        if (!filter) return true;
        const role = (user.roleName || user.role || '').toUpperCase();
        const isAdmin = user.admin === true || user.isAdmin === true;
        if (filter === 'adminOnly') return isAdmin;
        if (filter === 'nonEditor') return role !== 'EDITOR';
        return true;
    }

    function formatUserLabel(user) {
        return `${user.login} (${user.email})`;
    }

    async function fetchServerOptions(wrapper, query) {
        if (typeof adminFetch !== 'function') return [];
        const q = (query || '').trim();
        const pageSize = wrapper.dataset.searchPageSize || '50';
        let data;
        if (q.length === 0) {
            const listUrl = wrapper.dataset.searchListSource || DEFAULT_LIST_SOURCE;
            data = await adminFetch(`${listUrl}?page=0&size=${pageSize}&includeDeleted=false`);
        } else {
            const searchUrl = wrapper.dataset.searchSource || `${DEFAULT_LIST_SOURCE}/search`;
            data = await adminFetch(`${searchUrl}?q=${encodeURIComponent(q)}&page=0&size=${pageSize}`);
        }
        const users = data.content || data.users || [];
        return users.filter(u => passesOptionFilter(wrapper, u));
    }

    function scheduleServerSearch(wrapper) {
        if (!hasServerSearch(wrapper)) return;
        if (wrapper._serverSearchTimer) clearTimeout(wrapper._serverSearchTimer);
        wrapper._serverSearchTimer = setTimeout(() => runServerSearch(wrapper), DEBOUNCE_MS);
    }

    async function runServerSearch(wrapper) {
        const filterInput = wrapper.querySelector('.select-filter');
        const styled = wrapper.querySelector('.select-styled');
        const query = (filterInput?.value || styled?.textContent || '').trim();

        window.clearCustomSelectOptions(wrapper.id, { keepMessages: false });

        if (query.length > 0) {
            setSelectMessage(wrapper, 'Поиск...');
        } else {
            setSelectMessage(wrapper, 'Загрузка...');
        }
        try {
            const users = await fetchServerOptions(wrapper, query);
            window.clearCustomSelectOptions(wrapper.id, { keepMessages: false });
            if (users.length === 0) {
                setSelectMessage(wrapper, 'Ничего не найдено');
                return;
            }
            users.forEach(u => {
                window.populateCustomSelect(wrapper.id, u.id, formatUserLabel(u));
            });
            window.applyCustomSelectFilter(wrapper, query);
        } catch (e) {
            window.clearCustomSelectOptions(wrapper.id, { keepMessages: false });
            setSelectMessage(wrapper, 'Ошибка поиска');
            console.error('Server search failed:', e);
        }
    }

    function hasLoadedOptions(wrapper) {
        const optionsDiv = getOptionsDiv(wrapper);
        if (!optionsDiv) return false;
        return optionsDiv.querySelectorAll('.select-option:not(.select-message)').length > 0;
    }

    function handleFilterInput(wrapper, query, source) {
        syncFilterInputs(wrapper, query, source);
        if (hasServerSearch(wrapper)) {
            const trimmed = (query || '').trim();
            const useServer = source === 'styled' || trimmed.length === 0 || !hasLoadedOptions(wrapper);
            if (useServer) {
                scheduleServerSearch(wrapper);
            } else {
                window.applyCustomSelectFilter(wrapper, query);
            }
        } else {
            window.applyCustomSelectFilter(wrapper, query);
        }
        showDropdown(wrapper);
    }

    function ensureServerOptionsLoaded(wrapper) {
        if (!hasServerSearch(wrapper) || hasLoadedOptions(wrapper)) return;
        scheduleServerSearch(wrapper);
    }

    function selectOption(wrapper, option) {
        const styled = wrapper.querySelector('.select-styled');
        const optionsDiv = getOptionsDiv(wrapper);
        const hiddenInput = getHiddenInput(wrapper);
        const text = option.textContent;
        const value = option.dataset.value;

        if (styled) {
            styled.textContent = text;
            styled.style.color = 'var(--text-color)';
        }
        if (hiddenInput) {
            hiddenInput.value = value;
            hiddenInput.dispatchEvent(new Event('change', { bubbles: true }));
        }
        hideDropdown(wrapper);
        window.resetCustomSelectFilter(wrapper);
        if (styled && styled.contentEditable === 'true' && styled.dataset.placeholder) {
            styled.dataset.selectedLabel = text;
        }
    }

    function bindOption(wrapper, option) {
        if (option.dataset.bound === 'true' || option.classList.contains('select-message')) return;
        option.dataset.bound = 'true';
        if (!option.dataset.search) option.dataset.search = option.textContent || '';
        if (!option.hasAttribute('tabindex')) option.tabIndex = 0;

        option.addEventListener('keydown', (ev) => {
            if (ev.key === 'Enter' || ev.key === ' ') {
                ev.preventDefault();
                option.click();
            }
        });

        option.addEventListener('click', (e) => {
            e.stopPropagation();
            selectOption(wrapper, option);
        });
    }

    function initOneCustomSelect(wrapper) {
        if (wrapper.dataset.initialized === 'true') return;
        wrapper.dataset.initialized = 'true';

        const styled = wrapper.querySelector('.select-styled');
        const optionsDiv = getOptionsDiv(wrapper);
        if (!styled || !optionsDiv) return;

        const searchable = isSearchable(wrapper);
        if (searchable) {
            ensureSearchableStructure(wrapper);
            styled.contentEditable = true;
            styled.classList.add('select-searchable');
            if (!styled.dataset.placeholder) {
                styled.dataset.placeholder = styled.textContent || '';
            }

            const filterInput = wrapper.querySelector('.select-filter');

            if (styled.dataset.searchListenersBound === 'true') {
                optionsDiv.querySelectorAll('.select-option').forEach(option => bindOption(wrapper, option));
                return;
            }
            styled.dataset.searchListenersBound = 'true';

            styled.addEventListener('input', () => {
                handleFilterInput(wrapper, styled.textContent || '', 'styled');
            });

            styled.addEventListener('focus', () => {
                if ((styled.textContent || '').trim() === (styled.dataset.placeholder || '').trim()) {
                    styled.textContent = '';
                }
                showDropdown(wrapper);
                const q = styled.textContent || '';
                if (hasServerSearch(wrapper)) {
                    ensureServerOptionsLoaded(wrapper);
                    if ((q || '').trim().length > 0) scheduleServerSearch(wrapper);
                } else {
                    window.applyCustomSelectFilter(wrapper, q);
                }
            });

            styled.addEventListener('blur', () => {
                setTimeout(() => {
                    const selectedLabel = styled.dataset.selectedLabel;
                    const current = (styled.textContent || '').trim();
                    if (!current && !hiddenInputHasValue(wrapper)) {
                        styled.textContent = styled.dataset.placeholder || '';
                    } else if (hiddenInputHasValue(wrapper) && selectedLabel) {
                        styled.textContent = selectedLabel;
                    }
                    hideDropdown(wrapper);
                }, 150);
            });

            styled.addEventListener('keydown', (ev) => {
                if (ev.key === 'Enter') {
                    ev.preventDefault();
                    const visible = Array.from(optionsDiv.querySelectorAll('.select-option'))
                        .filter(o => o.style.display !== 'none' && !o.classList.contains('select-message'));
                    if (visible.length === 1) visible[0].click();
                    hideDropdown(wrapper);
                    styled.blur();
                } else if (ev.key === 'Escape') {
                    hideDropdown(wrapper);
                    styled.blur();
                } else if (ev.key === 'ArrowDown') {
                    ev.preventDefault();
                    const first = Array.from(optionsDiv.querySelectorAll('.select-option'))
                        .find(o => o.style.display !== 'none' && !o.classList.contains('select-message'));
                    if (first) first.focus();
                }
            });

            if (filterInput) {
                filterInput.addEventListener('focus', () => {
                    showDropdown(wrapper);
                    ensureServerOptionsLoaded(wrapper);
                });
                filterInput.addEventListener('input', () => {
                    handleFilterInput(wrapper, filterInput.value, 'filter');
                });
                filterInput.addEventListener('keydown', (ev) => {
                    if (ev.key === 'Escape') {
                        hideDropdown(wrapper);
                        styled.blur();
                    } else if (ev.key === 'ArrowDown') {
                        ev.preventDefault();
                        const first = Array.from(optionsDiv.querySelectorAll('.select-option'))
                            .find(o => o.style.display !== 'none' && !o.classList.contains('select-message'));
                        if (first) first.focus();
                    }
                });
                filterInput.addEventListener('click', (e) => e.stopPropagation());
            }
        }

        if (styled.dataset.clickListenerBound !== 'true') {
            styled.dataset.clickListenerBound = 'true';
            styled.addEventListener('click', (e) => {
                e.stopPropagation();
                closeAllDropdowns(wrapper);
                const panel = getDropdownPanel(wrapper);
                const isVisible = panel && panel.style.display === 'block';
                if (isVisible) {
                    hideDropdown(wrapper);
                } else {
                    showDropdown(wrapper);
                    if (searchable && hasServerSearch(wrapper)) {
                        ensureServerOptionsLoaded(wrapper);
                        const q = (wrapper.querySelector('.select-filter')?.value || styled.textContent || '').trim();
                        if (q.length > 0) scheduleServerSearch(wrapper);
                    }
                }
            });
        }

        optionsDiv.querySelectorAll('.select-option').forEach(option => bindOption(wrapper, option));
    }

    window.initCustomSelects = () => {
        document.querySelectorAll('.custom-select').forEach(initOneCustomSelect);

        if (!window.customSelectGlobalListener) {
            window.addEventListener('click', () => closeAllDropdowns(null));
            window.customSelectGlobalListener = true;
        }
    };

    window.clearCustomSelectOptions = (wrapperId, opts = {}) => {
        const wrapper = typeof wrapperId === 'string' ? document.getElementById(wrapperId) : wrapperId;
        if (!wrapper) return;
        const optionsDiv = getOptionsDiv(wrapper);
        if (!optionsDiv) return;
        optionsDiv.querySelectorAll('.select-option').forEach(opt => {
            if (opts.keepMessages && opt.classList.contains('select-message')) return;
            opt.remove();
        });
        if (!opts.keepMessages) {
            optionsDiv.querySelectorAll('.select-message').forEach(el => el.remove());
        }
    };

    window.loadCustomSelectOptions = (wrapperId) => {
        const wrapper = typeof wrapperId === 'string' ? document.getElementById(wrapperId) : wrapperId;
        if (!wrapper || !hasServerSearch(wrapper)) return;
        runServerSearch(wrapper);
    };

    window.refreshCustomSelect = (wrapperId) => {
        const wrapper = typeof wrapperId === 'string' ? document.getElementById(wrapperId) : wrapperId;
        if (!wrapper) return;
        delete wrapper.dataset.initialized;
        if (wrapper._serverSearchTimer) {
            clearTimeout(wrapper._serverSearchTimer);
            wrapper._serverSearchTimer = null;
        }
        initOneCustomSelect(wrapper);
        getOptionsDiv(wrapper)?.querySelectorAll('.select-option').forEach(option => {
            bindOption(wrapper, option);
        });
    };

    window.setCustomSelectValue = (wrapperId, value, label) => {
        const wrapper = typeof wrapperId === 'string' ? document.getElementById(wrapperId) : wrapperId;
        if (!wrapper) return;
        const styled = wrapper.querySelector('.select-styled');
        const hiddenInput = getHiddenInput(wrapper);
        if (hiddenInput) hiddenInput.value = value || '';
        if (!styled) return;
        if (label) {
            styled.textContent = label;
            styled.style.color = value ? 'var(--text-color)' : '';
            if (value) {
                styled.dataset.selectedLabel = label;
            } else {
                delete styled.dataset.selectedLabel;
            }
        }
    };

    window.populateCustomSelect = (wrapperId, value, text, onSelect) => {
        const wrapper = document.getElementById(wrapperId);
        const optionsDiv = getOptionsDiv(wrapper);
        if (!wrapper || !optionsDiv) return;

        const option = document.createElement('div');
        option.className = 'select-option';
        option.dataset.value = value;
        option.textContent = text;
        option.dataset.search = text;
        option.tabIndex = 0;

        option.addEventListener('click', (e) => {
            e.stopPropagation();
            selectOption(wrapper, option);
            if (onSelect) onSelect(value);
        });

        option.addEventListener('keydown', (ev) => {
            if (ev.key === 'Enter' || ev.key === ' ') {
                ev.preventDefault();
                option.click();
            }
        });

        optionsDiv.appendChild(option);

        const filterInput = wrapper.querySelector('.select-filter');
        const query = filterInput ? filterInput.value : '';
        if (!hasServerSearch(wrapper)) {
            window.applyCustomSelectFilter(wrapper, query);
        }
    };
})();
