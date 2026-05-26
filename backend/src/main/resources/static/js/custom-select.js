// Helper to initialize custom selects globally
window.initCustomSelects = () => {
    document.querySelectorAll('.custom-select').forEach(wrapper => {
        if (wrapper.dataset.initialized === 'true') return;
        wrapper.dataset.initialized = 'true';
        
        const styled = wrapper.querySelector('.select-styled');
        const optionsDiv = wrapper.querySelector('.select-options');
        const hiddenInput = document.getElementById(wrapper.dataset.name || wrapper.dataset.target);

        // Click event on styled box
        styled.addEventListener('click', (e) => {
            e.stopPropagation();
            // Close other selects
            document.querySelectorAll('.select-options').forEach(el => {
                if(el !== optionsDiv) el.style.display = 'none';
            });
            document.querySelectorAll('.select-styled').forEach(el => {
                if(el !== styled) el.classList.remove('active');
            });
            
            // Toggle current
            const isVisible = optionsDiv.style.display === 'block';
            optionsDiv.style.display = isVisible ? 'none' : 'block';
            styled.classList.toggle('active', !isVisible);
        });

        // Initialize existing options click (for static ones)
        optionsDiv.querySelectorAll('.select-option').forEach(option => {
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
