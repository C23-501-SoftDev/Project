// Helper to initialize custom selects globally
window.initCustomSelects = () => {
    document.querySelectorAll('.custom-select').forEach(wrapper => {
        if (wrapper.dataset.initialized === 'true') return;
        wrapper.dataset.initialized = 'true';

        const styled = wrapper.querySelector('.select-styled');
        const optionsDiv = wrapper.querySelector('.select-options');
        const hiddenInput = document.getElementById(wrapper.dataset.name || wrapper.dataset.target);

        // Searchable support (typeahead in the styled element)
        const isSearchable = wrapper.dataset.searchable === 'true';
        let filterInput = null;
        const optionsDivExists = !!wrapper.querySelector('.select-options');
        if (isSearchable && optionsDivExists) {
            const optionsDiv = wrapper.querySelector('.select-options');

            // make styled area editable for direct typing
            styled.contentEditable = true;
            styled.classList.add('select-searchable');
            // preserve placeholder
            if (!styled.dataset.placeholder) styled.dataset.placeholder = styled.textContent || '';

            // input handler on styled (typeahead)
            const applyFilter = (q) => {
                const query = (q || '').trim().toLowerCase();
                optionsDiv.querySelectorAll('.select-option').forEach(opt => {
                    const txt = (opt.dataset.search || opt.textContent || '').toLowerCase();
                    opt.style.display = query === '' || txt.includes(query) ? '' : 'none';
                });
            };

            styled.addEventListener('input', (e) => {
                const q = styled.textContent || '';
                applyFilter(q);
                // show dropdown while typing
                optionsDiv.style.display = 'block';
                styled.classList.add('active');
            });

            styled.addEventListener('focus', () => {
                // clear placeholder on focus
                if ((styled.textContent || '').trim() === (styled.dataset.placeholder || '').trim()) {
                    styled.textContent = '';
                }
                optionsDiv.style.display = 'block';
                applyFilter(styled.textContent);
            });

            styled.addEventListener('blur', () => {
                // delay to allow option click
                setTimeout(() => {
                    if ((styled.textContent || '').trim() === '') {
                        styled.textContent = styled.dataset.placeholder || '';
                        if (hiddenInput) hiddenInput.value = '';
                    }
                    optionsDiv.style.display = 'none';
                    styled.classList.remove('active');
                }, 150);
            });

            styled.addEventListener('keydown', (ev) => {
                if (ev.key === 'Enter') {
                    ev.preventDefault();
                    const visible = Array.from(optionsDiv.querySelectorAll('.select-option'))
                        .filter(o => o.style.display !== 'none');
                    if (visible.length === 1) {
                        visible[0].click();
                    }
                    optionsDiv.style.display = 'none';
                    styled.blur();
                } else if (ev.key === 'Escape') {
                    optionsDiv.style.display = 'none';
                    styled.blur();
                } else if (ev.key === 'ArrowDown') {
                    ev.preventDefault();
                    const first = Array.from(optionsDiv.querySelectorAll('.select-option'))
                        .find(o => o.style.display !== 'none');
                    if (first) first.focus();
                }
            });

            // also create optional inline filter input for accessibility (kept for compatibility)
            filterInput = optionsDiv.querySelector('.select-filter');
            if (!filterInput) {
                filterInput = document.createElement('input');
                filterInput.type = 'text';
                filterInput.className = 'select-filter';
                filterInput.placeholder = 'Фильтр...';
                optionsDiv.prepend(filterInput);
            }
            filterInput.addEventListener('input', () => {
                const q = filterInput.value.trim();
                applyFilter(q);
            });
        }

        // Click event on styled box
        styled.addEventListener('click', (e) => {
            e.stopPropagation();
            // Close other selects
            document.querySelectorAll('.select-options').forEach(el => {
                if (el !== optionsDiv) el.style.display = 'none';
            });
            document.querySelectorAll('.select-styled').forEach(el => {
                if (el !== styled) el.classList.remove('active');
            });

            // Toggle current
            const isVisible = optionsDiv.style.display === 'block';
            optionsDiv.style.display = isVisible ? 'none' : 'block';
            styled.classList.toggle('active', !isVisible);
        });

        // Initialize existing options click (for static ones)
        optionsDiv.querySelectorAll('.select-option').forEach(option => {
            // store searchable text
            if (!option.dataset.search) option.dataset.search = option.textContent || '';
            option.addEventListener('click', (e) => {
                e.stopPropagation();
                const text = option.textContent;
                const value = option.dataset.value;
                styled.textContent = text;
                styled.style.color = 'var(--text-color)';
                if (hiddenInput) hiddenInput.value = value;
                optionsDiv.style.display = 'none';
                styled.classList.remove('active');

                // Trigger change event if needed
                if (hiddenInput) {
                    hiddenInput.dispatchEvent(new Event('change', { bubbles: true }));
                }
            });
        });
    });

    if (!window.customSelectGlobalListener) {
        window.addEventListener('click', () => {
            document.querySelectorAll('.select-options').forEach(el => el.style.display = 'none');
            document.querySelectorAll('.select-styled').forEach(el => el.classList.remove('active'));
        });
        window.customSelectGlobalListener = true;
    }
};

window.populateCustomSelect = (wrapperId, value, text, onSelect) => {
    const wrapper = document.getElementById(wrapperId);
    const optionsDiv = wrapper.querySelector('.select-options');
    if (!optionsDiv) return;
    const option = document.createElement('div');
    option.className = 'select-option';
    option.dataset.value = value;
    option.textContent = text;
    option.dataset.search = text;
    option.tabIndex = 0;
    option.addEventListener('keydown', (ev) => {
        if (ev.key === 'Enter' || ev.key === ' ') {
            ev.preventDefault();
            option.click();
        }
    });
    option.addEventListener('click', (e) => {
        e.stopPropagation();
        wrapper.querySelector('.select-styled').textContent = text;
        wrapper.querySelector('.select-styled').style.color = 'var(--text-color)';
        document.getElementById(wrapper.dataset.name || wrapper.dataset.target).value = value;
        optionsDiv.style.display = 'none';
        wrapper.querySelector('.select-styled').classList.remove('active');
        if (onSelect) onSelect(value);
    });
    optionsDiv.appendChild(option);
};
