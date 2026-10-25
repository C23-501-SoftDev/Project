const { test, expect } = require('./helpers/test');
const { request: rawRequest } = require('@playwright/test');
const { createAuthenticatedPage } = require('./helpers/session');

async function openRag(fixtures, mockEnabled = true) {
    const session = await createAuthenticatedPage(fixtures);
    if (mockEnabled) {
        await session.page.route('**/api/ai/rag/status', route => route.fulfill({ json: { enabled: true } }));
    }
    await session.page.goto(`${fixtures.baseURL}/ask`);
    await expect(session.page.locator('#ragTitle')).toHaveText('Вопрос по базе знаний');
    if (mockEnabled) await expect(session.page.locator('#ragSubmit')).toBeEnabled();
    return session;
}

test('RAG-01: disabled mode is visible and does not affect document navigation', async ({ browser, request, baseURL }) => {
    const fixtures = { browser, request, baseURL };
    const { page, context } = await openRag(fixtures, false);
    try {
        await expect(page.locator('#ragAvailability')).toContainText('пока недоступны');
        await expect(page.locator('#ragSubmit')).toBeDisabled();
        await expect(page.locator('#ragQuestion')).toBeDisabled();
        await expect(page.locator('.sidebar a[href="/ask"]')).toBeVisible();
        const status = await fixtures.request.get(`${fixtures.baseURL}/api/ai/rag/status`);
        expect(await status.json()).toEqual({ enabled: false });
        expect(status.headers()['cache-control']).toContain('no-store');
        const answer = await fixtures.request.post(`${fixtures.baseURL}/api/ai/ask`, { data: { question: 'Как создать статью?' } });
        expect(answer.status()).toBe(503);
        expect((await answer.json()).code).toBe('DISABLED');
        await page.locator('.navbar-brand a').click();
        await expect(page.locator('#documentListTitle')).toBeVisible();
    } finally { await context.close(); }
});

test('RAG-02: answer and versioned sources render as text and use CSRF', async ({ browser, request, baseURL }) => {
    const fixtures = { browser, request, baseURL };
    const { page, context } = await openRag(fixtures);
    try {
        let submitted;
        let csrf;
        await page.route('**/api/ai/ask', route => {
            submitted = route.request().postDataJSON();
            csrf = route.request().headers()['x-xsrf-token'];
            return route.fulfill({ json: {
                answer: '<img src=x onerror="window.ragXss=true"> Ответ [1].',
                sources: [
                    { documentId: 42, title: '<script>window.ragXss=true</script>', url: '/documents/42', versionSha: 'abc123', chunkIndex: 2 },
                    { documentId: 43, title: 'Внешний адрес', url: 'https://example.invalid/documents/43', versionSha: 'abc456', chunkIndex: 0 }
                ], insufficientEvidence: false, requestId: 'rag-e2e-42'
            } });
        });
        await page.locator('#ragQuestion').fill('  Как создать статью?  ');
        await page.locator('#ragSubmit').click();
        await expect(page.locator('#ragAnswer')).toContainText('<img src=x');
        await expect(page.locator('#ragAnswer img')).toHaveCount(0);
        await expect(page.locator('#ragSources a')).toHaveCount(1);
        await expect(page.locator('#ragSources a')).toHaveAttribute('href', '/documents/42');
        await expect(page.locator('#ragSources')).toContainText('abc123 · Фрагмент 3');
        await expect(page.locator('#ragRequestId')).toContainText('rag-e2e-42');
        expect(await page.evaluate(() => window.ragXss)).toBeUndefined();
        expect(submitted).toEqual({ question: 'Как создать статью?', spaceIds: [] });
        expect(csrf).toBeTruthy();
    } finally { await context.close(); }
});

test('RAG-03: empty questions, insufficient evidence and controlled failures', async ({ browser, request, baseURL }) => {
    const fixtures = { browser, request, baseURL };
    const { page, context } = await openRag(fixtures);
    try {
        let calls = 0;
        await page.route('**/api/ai/ask', route => {
            calls++;
            return route.fulfill({ json: { answer: '', sources: [], insufficientEvidence: true, requestId: 'no-evidence' } });
        });
        await page.locator('#ragQuestion').fill('   ');
        await page.locator('#ragSubmit').click();
        await expect(page.locator('#ragError')).toContainText('Введите вопрос');
        expect(calls).toBe(0);
        await page.locator('#ragQuestion').fill('Есть ли статья про неизвестный процесс?');
        await page.locator('#ragSubmit').click();
        await expect(page.locator('#ragInsufficient')).toBeVisible();
        await expect(page.locator('#ragSourcesSection')).toBeHidden();
        await page.unroute('**/api/ai/ask');
        await page.route('**/api/ai/ask', route => route.fulfill({ status: 409, json: {
            code: 'SOURCES_CHANGED', message: 'Статьи или права доступа изменились. Задайте вопрос повторно.'
        } }));
        await page.locator('#ragQuestion').fill('Другой вопрос');
        await page.locator('#ragSubmit').click();
        await expect(page.locator('#ragError')).toContainText('права доступа изменились');
        await expect(page.locator('#ragResult')).toBeHidden();
        await expect(page.locator('#ragSubmit')).toBeEnabled();
    } finally { await context.close(); }
});

test('RAG-04: changing the question discards an in-flight answer', async ({ browser, request, baseURL }) => {
    const fixtures = { browser, request, baseURL };
    const { page, context } = await openRag(fixtures);
    try {
        let release;
        const waitForRelease = new Promise(resolve => { release = resolve; });
        let received;
        const waitForRequest = new Promise(resolve => { received = resolve; });
        await page.route('**/api/ai/ask', async route => {
            received();
            await waitForRelease;
            await route.fulfill({ json: { answer: 'Устаревший ответ', sources: [], insufficientEvidence: false, requestId: 'old' } });
        });
        await page.locator('#ragQuestion').fill('Первый вопрос');
        await page.locator('#ragSubmit').click();
        await waitForRequest;
        await expect(page.locator('#ragLoading')).toBeVisible();
        await page.locator('#ragQuestion').fill('Изменённый вопрос');
        release();
        await expect(page.locator('#ragLoading')).toBeHidden();
        await expect(page.locator('#ragResult')).toBeHidden();
        await expect(page.locator('#ragSubmit')).toBeEnabled();
    } finally { await context.close(); }
});

test('RAG-05: real API rejects anonymous access, missing CSRF and invalid requests', async ({ baseURL }) => {
    const api = await rawRequest.newContext({ baseURL });
    try {
        expect((await api.get('/api/ai/rag/status')).status()).toBe(401);
        await api.get('/login');
        const cookie = (await api.storageState()).cookies.find(value => value.name === 'XSRF-TOKEN');
        const headers = { 'X-XSRF-TOKEN': decodeURIComponent(cookie.value) };
        expect((await api.post('/api/ai/ask', { headers, data: { question: 'Вопрос' } })).status()).toBe(401);
        expect((await api.post('/api/auth/login', { headers, data: { login: 'admin', password: 'admin123' } })).ok()).toBeTruthy();
        expect((await api.post('/api/ai/ask', { data: { question: 'Вопрос' } })).status()).toBe(403);
        for (const data of [{ question: '   ' }, { question: 'Вопрос', spaceIds: [0] }, { question: 'Вопрос', spaceIds: [null] }]) {
            const response = await api.post('/api/ai/ask', { headers, data });
            expect(response.status()).toBe(400);
            expect((await response.json()).code).toBe('INVALID_REQUEST');
        }
    } finally { await api.dispose(); }
});
