(function () {
    function createCell(className, value) {
        const cell = document.createElement('td');
        cell.className = className;
        cell.textContent = value == null ? '' : String(value);
        return cell;
    }

    function appendSegments(cell, line, allowedType) {
        const segments = line.segments && line.segments.length
            ? line.segments
            : [{type: line.type === 'CONTEXT' ? 'UNCHANGED' : line.type, content: line.content}];
        segments.forEach(segment => {
            const type = String(segment.type || 'UNCHANGED').toLowerCase();
            if (allowedType && type !== 'unchanged' && type !== allowedType) return;
            const span = document.createElement('span');
            span.className = 'diff-segment-' + type;
            span.textContent = segment.content == null ? '' : String(segment.content);
            cell.append(span);
        });
    }

    function createContentCell(className, line, allowedType) {
        const cell = document.createElement('td');
        cell.className = className;
        appendSegments(cell, line, allowedType);
        return cell;
    }

    function renderInlineDiff(container, lines) {
        const table = document.createElement('table');
        table.className = 'diff-table';
        const body = document.createElement('tbody');
        lines.forEach(line => {
            const row = document.createElement('tr');
            const type = String(line.type || 'CONTEXT').toLowerCase();
            row.className = 'diff-line diff-line-' + type;
            row.append(createCell('diff-line-number', line.beforeLineNumber));
            row.append(createCell('diff-line-number', line.afterLineNumber));
            row.append(createCell('diff-line-marker', type === 'removed' ? '−' : type === 'added' ? '+' : type === 'modified' ? '±' : ''));
            row.append(createContentCell('diff-line-content', line));
            body.append(row);
        });
        table.append(body);
        container.append(table);
    }

    function renderSideBySideDiff(container, lines) {
        const table = document.createElement('table');
        table.className = 'diff-table diff-table-side-by-side';
        const body = document.createElement('tbody');
        lines.forEach(line => {
            const row = document.createElement('tr');
            const type = String(line.type || 'CONTEXT').toLowerCase();
            row.className = 'diff-line diff-line-' + type;
            row.append(createCell('diff-line-number diff-before-number', line.beforeLineNumber));
            row.append(createContentCell('diff-line-content diff-before-content', line, 'removed'));
            row.append(createCell('diff-line-marker diff-before-marker',
                type === 'removed' || type === 'modified' ? '−' : ''));
            row.append(createCell('diff-line-marker diff-after-marker',
                type === 'added' || type === 'modified' ? '+' : ''));
            row.append(createCell('diff-line-number diff-after-number', line.afterLineNumber));
            row.append(createContentCell('diff-line-content diff-after-content', line, 'added'));
            body.append(row);
        });
        table.append(body);
        container.append(table);
    }

    function renderDiff(container, lines, mode = 'inline') {
        container.replaceChildren();
        if (!lines || lines.length === 0) {
            const empty = document.createElement('p');
            empty.className = 'diff-empty';
            empty.textContent = 'Версии не отличаются.';
            container.append(empty);
            return;
        }
        if (mode === 'side-by-side') {
            renderSideBySideDiff(container, lines);
        } else {
            renderInlineDiff(container, lines);
        }
    }

    function addVersionOptions(select, versions) {
        select.replaceChildren();
        const placeholder = document.createElement('option');
        placeholder.value = '';
        placeholder.textContent = 'Выберите версию';
        select.append(placeholder);
        versions.forEach(version => {
            const option = document.createElement('option');
            option.value = version.gitHash;
            const date = version.createdAt ? new Date(version.createdAt).toLocaleString() : '';
            option.textContent = `${version.gitHash.slice(0, 7)} — ${version.comment || 'Сохранённая версия'} ${date}`;
            select.append(option);
        });
        // A single version is still useful to inspect; only the compare action
        // requires two distinct hashes.
        select.disabled = versions.length === 0;
        select.setAttribute('aria-busy', 'false');
    }

    window.documentHistoryDiff = {renderDiff, addVersionOptions};

    document.addEventListener('DOMContentLoaded', () => {
        const root = document.getElementById('documentHistory');
        if (!root) return;

        const form = document.getElementById('diffSelector');
        const fromInput = document.getElementById('diffFrom');
        const toInput = document.getElementById('diffTo');
        const status = document.getElementById('diffStatus');
        const result = document.getElementById('diffResult');
        const compareButton = form.querySelector('button[type="submit"]');
        const modeToggle = document.getElementById('diffModeToggle');
        const contextToggle = document.getElementById('diffContextToggle');
        const algorithmInput = document.getElementById('diffAlgorithm');
        const documentId = root.dataset.documentId;
        let diffLines = [];
        let diffMode = 'inline';
        let fullContext = false;

        const updateCompareButton = () => {
            compareButton.disabled = !fromInput.value || !toInput.value || fromInput.value === toInput.value;
        };

        (async () => {
            try {
                const versions = await apiFetch(`/api/documents/${encodeURIComponent(documentId)}/versions`);
                addVersionOptions(fromInput, versions);
                addVersionOptions(toInput, versions);
                if (versions.length >= 2) {
                    fromInput.value = versions[0].gitHash;
                    toInput.value = versions[versions.length - 1].gitHash;
                }
                updateCompareButton();
            } catch (error) {
                fromInput.setAttribute('aria-busy', 'false');
                toInput.setAttribute('aria-busy', 'false');
                status.hidden = false;
                status.textContent = error.message || 'Не удалось загрузить историю версий.';
            }
        })();

        fromInput.addEventListener('change', updateCompareButton);
        toInput.addEventListener('change', updateCompareButton);

        async function loadDiff(from, to) {
            const context = fullContext ? 'all' : 'changed';
            const algorithm = algorithmInput.value || 'CHARACTER';
            const diff = await apiFetch(`/api/documents/${encodeURIComponent(documentId)}/diff?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&context=${context}&algorithm=${encodeURIComponent(algorithm)}`);
            diffLines = diff.lines || [];
            renderDiff(result, diffLines, diffMode);
            result.hidden = false;
            modeToggle.disabled = false;
            contextToggle.disabled = false;
            status.textContent = `Сравнение ${diff.fromHash.slice(0, 7)} → ${diff.toHash.slice(0, 7)}`;
        }

        form.addEventListener('submit', async event => {
            event.preventDefault();
            const from = fromInput.value.trim();
            const to = toInput.value.trim();
            if (!/^[0-9a-f]{40}$/i.test(from) || !/^[0-9a-f]{40}$/i.test(to) || from === to) {
                status.hidden = false;
                status.textContent = 'Укажите две разные версии в формате 40-символьного SHA.';
                result.hidden = true;
                return;
            }

            status.hidden = false;
            status.textContent = 'Загрузка сравнения…';
            result.hidden = true;
            fullContext = false;
            contextToggle.textContent = 'Показать весь файл';
            try {
                await loadDiff(from, to);
            } catch (error) {
                status.textContent = error.message || 'Не удалось загрузить сравнение версий.';
                result.hidden = true;
            }
        });

        modeToggle.addEventListener('click', () => {
            diffMode = diffMode === 'inline' ? 'side-by-side' : 'inline';
            modeToggle.textContent = diffMode === 'inline' ? 'Две панели' : 'Построчно';
            renderDiff(result, diffLines, diffMode);
        });

        contextToggle.addEventListener('click', async () => {
            fullContext = !fullContext;
            contextToggle.textContent = fullContext ? 'Скрыть неизменённые строки' : 'Показать весь файл';
            status.textContent = 'Загрузка сравнения…';
            try {
                await loadDiff(fromInput.value.trim(), toInput.value.trim());
            } catch (error) {
                status.textContent = error.message || 'Не удалось загрузить полный файл.';
            }
        });
    });
})();
