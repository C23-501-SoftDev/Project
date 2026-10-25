(() => {
    'use strict';
    const root = document.getElementById('ragPage');
    if (!root) return;
    const element = id => document.getElementById(id);
    const form = element('ragForm');
    const question = element('ragQuestion');
    const spaces = element('ragSpaces');
    const submit = element('ragSubmit');
    const clearSpaces = element('ragClearSpaces');
    const availability = element('ragAvailability');
    const error = element('ragError');
    const loading = element('ragLoading');
    const result = element('ragResult');
    let enabled = false;
    let revision = 0;
    let pending;

    function invalidate() {
        revision += 1;
        pending?.abort();
        pending = undefined;
        loading.hidden = true;
        result.hidden = true;
        error.hidden = true;
        submit.disabled = !enabled;
        form.removeAttribute('aria-busy');
    }

    function showError(message) {
        error.textContent = message;
        error.hidden = false;
    }

    function render(data) {
        element('ragAnswer').textContent = data.answer || '';
        element('ragInsufficient').hidden = !data.insufficientEvidence;
        element('ragResultTitle').textContent = data.insufficientEvidence ? 'Недостаточно сведений' : 'Ответ';
        const list = element('ragSources');
        list.replaceChildren();
        for (const source of data.sources || []) {
            const item = document.createElement('li');
            const link = document.createElement('a');
            // Only document routes on this origin are allowed, even if a malformed response is received.
            let url;
            try { url = new URL(source.url || '', window.location.origin); }
            catch { continue; }
            if (url.origin !== window.location.origin || url.pathname !== `/documents/${source.documentId}`) continue;
            link.href = url.pathname;
            link.textContent = source.title || `Документ ${source.documentId}`;
            const version = document.createElement('span');
            version.className = 'rag-source-version';
            version.textContent = `Версия: ${source.versionSha || 'не указана'} · Фрагмент ${source.chunkIndex + 1}`;
            item.append(link, version);
            list.append(item);
        }
        element('ragSourcesSection').hidden = list.children.length === 0;
        element('ragRequestId').textContent = data.requestId ? `Номер запроса: ${data.requestId}` : '';
        result.hidden = false;
    }

    question.addEventListener('input', () => {
        element('ragCount').textContent = question.value.length;
        invalidate();
    });
    spaces.addEventListener('change', invalidate);
    clearSpaces.addEventListener('click', () => {
        for (const option of spaces.options) option.selected = false;
        invalidate();
    });

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (!enabled) return;
        invalidate();
        const text = question.value.trim();
        const selected = Array.from(spaces.selectedOptions, option => Number(option.value));
        if (!text || text.length > 2000) {
            showError('Введите вопрос от 1 до 2000 символов.');
            question.focus();
            return;
        }
        if (selected.length > 20) {
            showError('Выберите не более 20 пространств.');
            spaces.focus();
            return;
        }
        const currentRevision = revision;
        pending = new AbortController();
        submit.disabled = true;
        loading.hidden = false;
        form.setAttribute('aria-busy', 'true');
        try {
            const response = await fetch('/api/ai/ask', {
                method: 'POST', credentials: 'same-origin', signal: pending.signal,
                headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': getCsrfToken() },
                body: JSON.stringify({ question: text, spaceIds: selected })
            });
            if (currentRevision !== revision) return;
            const data = await response.json();
            if (currentRevision !== revision) return;
            if (!response.ok) {
                showError(data.message || 'Не удалось получить ответ. Попробуйте позже.');
                return;
            }
            render(data);
        } catch (failure) {
            if (currentRevision === revision && failure.name !== 'AbortError') {
                showError('Не удалось получить ответ. Проверьте соединение и попробуйте снова.');
            }
        } finally {
            if (currentRevision === revision) {
                pending = undefined;
                loading.hidden = true;
                submit.disabled = !enabled;
                form.removeAttribute('aria-busy');
            }
        }
    });

    fetch('/api/ai/rag/status', { credentials: 'same-origin', cache: 'no-store' })
        .then(async response => {
            if (!response.ok) throw new Error('status unavailable');
            const state = await response.json();
            enabled = state.enabled === true;
            question.disabled = spaces.disabled = submit.disabled = clearSpaces.disabled = !enabled;
            availability.hidden = enabled;
            availability.textContent = enabled ? '' : 'Ответы по базе знаний пока недоступны. Обратитесь к администратору.';
        })
        .catch(() => {
            availability.textContent = 'Не удалось проверить доступность сервиса. Обновите страницу позже.';
        });
})();
