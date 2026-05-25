// Helper to initialize custom selects globally
window.initCustomSelects = () => {
    document.querySelectorAll('.custom-select').forEach(wrapper => {
        const styled = wrapper.querySelector('.select-styled');
        const optionsDiv = wrapper.querySelector('.select-options');
        const hiddenInput = document.getElementById(wrapper.dataset.name || wrapper.dataset.target);

        // Click event on styled box
        styled.addEventListener('click', (e) => {
            e.stopPropagation();
            document.querySelectorAll('.select-options').forEach(el => { if(el !== optionsDiv) el.style.display = 'none'; });
            document.querySelectorAll('.select-styled').forEach(el => { if(el !== styled) el.classList.remove('active'); });
            optionsDiv.style.display = optionsDiv.style.display === 'block' ? 'none' : 'block';
            styled.classList.toggle('active');
        });

        // Initialize options click
        optionsDiv.querySelectorAll('.select-option, .select-options > div').forEach(option => {
            option.addEventListener('click', (e) => {
                e.stopPropagation();
                const text = option.textContent;
                const value = option.dataset.value;
                styled.textContent = text;
                styled.style.color = 'var(--text-color)';
                if (hiddenInput) hiddenInput.value = value;
                optionsDiv.style.display = 'none';
                styled.classList.remove('active');
            });
        });
    });

    window.addEventListener('click', () => {
        document.querySelectorAll('.select-options').forEach(el => el.style.display = 'none');
        document.querySelectorAll('.select-styled').forEach(el => el.classList.remove('active'));
    });
};

window.populateCustomSelect = (wrapperId, value, text, onSelect) => {
    const wrapper = document.getElementById(wrapperId);
    const optionsDiv = wrapper.querySelector('.select-options');
    const option = document.createElement('div');
    option.className = 'select-option';
    option.textContent = text;
    option.dataset.value = value;
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
