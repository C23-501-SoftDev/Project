const { test, expect } = require('./helpers/test');
const { createAdminApi, createUserApi, createUser, createSpace, createDocument,
    grantSpacePermission, newBrowserPageFromApi, uniqueSuffix } = require('./helpers/documents');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs/promises');
const path = require('node:path');
const { pathToFileURL, fileURLToPath } = require('node:url');

// Two genuine 2×2 RGB PNGs, with distinct pixels and valid PNG chunk CRCs.
const IMAGE = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEElEQVR4nGOQm/AfiBggFAArdga1oJlfgAAAAABJRU5ErkJggg==', 'base64');
const FOREIGN_IMAGE = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEElEQVR4nGP4LzIZiBggFAAv5gaZN/OSmgAAAABJRU5ErkJggg==', 'base64');
const INSTRUCTIONS = 'Офлайн-инструкция: проверьте схему и сохраните результат.\n';

async function upload(api, baseURL, documentId, name, mimeType, buffer) {
    const response = await api.post(`${baseURL}/api/documents/${documentId}/attachments`, {
        multipart: { files: { name, mimeType, buffer } }
    });
    expect(response.status()).toBe(201);
    return (await response.json())[0];
}

async function fixture(api, baseURL) {
    const tag = uniqueSuffix();
    const space = await createSpace(api, baseURL, `Portable export ${tag}`);
    const created = await createDocument(api, baseURL, {
        title: `Переносимый экспорт ${tag}`, spaceId: space.id, content: '# Автономный документ'
    });
    expect(created.status()).toBe(201);
    const document = await created.json();
    const image = await upload(api, baseURL, document.id, 'Схема.png', 'image/png', IMAGE);
    const text = await upload(api, baseURL, document.id, 'инструкция.txt', 'text/plain', Buffer.from(INSTRUCTIONS));
    const extra = await upload(api, baseURL, document.id, 'дополнение.txt', 'text/plain', Buffer.from('Непроцитированное вложение'));
    const content = `# Автономный документ\n\n![Схема процесса](/api/attachments/${image.id}/download)\n\n`
        + `[Инструкция](/api/attachments/${text.id}/download)\n\nТекст для проверки экспорта.`;
    expect((await api.put(`${baseURL}/api/documents/${document.id}`, { data: { content } })).ok()).toBeTruthy();
    expect((await api.post(`${baseURL}/api/documents/${document.id}/publish`)).ok()).toBeTruthy();
    return { space, document, image, text, extra, content };
}

function unpack(archivePath, directory) {
    // Python stdlib avoids another npm dependency. Validate paths before extracting the downloaded artifact.
    const output = execFileSync(process.env.E2E_PYTHON || 'python3', ['-c', `
import json, pathlib, stat, sys, zipfile
archive, destination = sys.argv[1:]
root = pathlib.Path(destination).resolve()
root.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(archive) as z:
    entries = z.infolist()
    assert len(entries) <= 500, 'Too many archive entries'
    assert sum(e.file_size for e in entries) <= 60 * 1024 * 1024, 'Unexpected archive size'
    for entry in entries:
        p = pathlib.PurePosixPath(entry.filename)
        assert not p.is_absolute() and '..' not in p.parts and '\\\\' not in entry.filename, 'Unsafe archive path'
        assert not stat.S_ISLNK(entry.external_attr >> 16), 'Unexpected symlink'
        assert (root / entry.filename).resolve().is_relative_to(root), 'Archive traversal'
    z.extractall(root)
    print(json.dumps([e.filename for e in entries], ensure_ascii=False))
`, archivePath, directory], { encoding: 'utf8', timeout: 10000 });
    return JSON.parse(output);
}

async function downloadFromPage(page, format, destination) {
    await page.locator('#exportMenuBtn').click();
    const pending = page.waitForEvent('download');
    await page.locator(`#export${format[0].toUpperCase()}${format.slice(1)}`).click();
    const downloaded = await pending;
    expect(downloaded.suggestedFilename()).toMatch(new RegExp(`\\.${format}$`));
    await downloaded.saveAs(destination);
    expect(await downloaded.failure()).toBeNull();
}

async function openOffline(browser, htmlPath) {
    const context = await browser.newContext({ offline: true });
    const page = await context.newPage();
    const networkRequests = [];
    page.on('request', request => { if (/^https?:/.test(request.url())) networkRequests.push(request.url()); });
    await context.route(/^https?:\/\//, route => route.abort());
    await page.goto(pathToFileURL(htmlPath).href);
    return { context, page, networkRequests };
}

test('EXPORT-01: ZIP menu downloads a complete offline document with local image and file links', async ({ browser, baseURL }, testInfo) => {
    const api = await createAdminApi(baseURL);
    const f = await fixture(api, baseURL);
    const { context, page } = await newBrowserPageFromApi(browser, api);
    let offline;
    try {
        await page.goto(`${baseURL}/documents/${f.document.id}`);
        await expect(page.locator('#docTitle')).toHaveText(f.document.title);
        const archivePath = testInfo.outputPath('portable.zip');
        await downloadFromPage(page, 'zip', archivePath);
        const directory = testInfo.outputPath('unpacked');
        const entries = unpack(archivePath, directory);
        expect(entries).toContain('index.html');
        for (const attachment of [f.image, f.text, f.extra]) {
            expect(entries.some(name => name.startsWith(`attachments/${attachment.id}-`))).toBeTruthy();
        }
        offline = await openOffline(browser, path.join(directory, 'index.html'));
        await expect(offline.page.locator('img[alt="Схема процесса"]')).toHaveAttribute('src', /^attachments\//);
        await expect.poll(() => offline.page.locator('img[alt="Схема процесса"]').evaluate(image => image.complete && image.naturalWidth === 2)).toBeTruthy();
        const href = await offline.page.getByRole('link', { name: 'Инструкция', exact: true }).getAttribute('href');
        expect(href).toMatch(/^attachments\//);
        const fileUrl = new URL(href, offline.page.url());
        expect(fileURLToPath(fileUrl).startsWith(directory + path.sep)).toBeTruthy();
        expect(await fs.readFile(fileURLToPath(fileUrl), 'utf8')).toBe(INSTRUCTIONS);
        await offline.page.goto(fileUrl.href);
        await expect(offline.page.locator('body')).toContainText('Офлайн-инструкция');
        expect(offline.networkRequests).toEqual([]);
        expect(await offline.context.cookies()).toEqual([]);
    } finally {
        if (offline) await offline.context.close();
        await context.close();
        await api.dispose();
    }
});

test('EXPORT-02: HTML, PDF and DOCX still download and carry embedded local images', async ({ browser, baseURL }, testInfo) => {
    const api = await createAdminApi(baseURL);
    const f = await fixture(api, baseURL);
    const { context, page } = await newBrowserPageFromApi(browser, api);
    let offline;
    try {
        await page.goto(`${baseURL}/documents/${f.document.id}`);
        await expect(page.locator('#docTitle')).toHaveText(f.document.title);
        for (const format of ['html', 'pdf', 'docx']) {
            await downloadFromPage(page, format, testInfo.outputPath(`portable.${format}`));
        }
        offline = await openOffline(browser, testInfo.outputPath('portable.html'));
        const image = offline.page.locator('img[alt="Схема процесса"]');
        await expect(image).toHaveAttribute('src', /^data:image\/png;base64,/);
        await expect.poll(() => image.evaluate(node => node.complete && node.naturalWidth === 2)).toBeTruthy();
        const link = offline.page.getByRole('link', { name: 'Инструкция', exact: true });
        const href = await link.getAttribute('href');
        expect(href).toMatch(/^data:application\/octet-stream;base64,/);
        expect(Buffer.from(href.slice(href.indexOf(',') + 1), 'base64').toString('utf8')).toBe(INSTRUCTIONS);
        const attachmentDownload = offline.page.waitForEvent('download');
        await link.click();
        const downloadedAttachment = await attachmentDownload;
        const attachmentPath = testInfo.outputPath('html-attachment.txt');
        await downloadedAttachment.saveAs(attachmentPath);
        expect(await downloadedAttachment.failure()).toBeNull();
        expect(await fs.readFile(attachmentPath, 'utf8')).toBe(INSTRUCTIONS);
        expect(offline.networkRequests).toEqual([]);
        const pdf = await fs.readFile(testInfo.outputPath('portable.pdf'));
        expect(pdf.subarray(0, 5).toString()).toBe('%PDF-');
        expect(pdf.length).toBeGreaterThan(1000);
        const directory = testInfo.outputPath('docx');
        const entries = unpack(testInfo.outputPath('portable.docx'), directory);
        expect(entries.filter(name => name.startsWith('word/media/') && !name.endsWith('/'))).toHaveLength(1);
        const xml = await fs.readFile(path.join(directory, 'word/document.xml'), 'utf8');
        expect(xml).toContain('<w:drawing>');
        const relationships = await fs.readFile(path.join(directory, 'word/_rels/document.xml.rels'), 'utf8');
        expect(relationships).toContain('/image');
        expect(relationships).toContain('media/');
    } finally {
        if (offline) await offline.context.close();
        await context.close();
        await api.dispose();
    }
});

test('EXPORT-03: foreign attachment references cannot bypass access through portable exports', async ({ baseURL }, testInfo) => {
    const admin = await createAdminApi(baseURL);
    let reader;
    try {
        const f = await fixture(admin, baseURL);
        const tag = uniqueSuffix();
        const privateSpace = await createSpace(admin, baseURL, `Private export source ${tag}`);
        const privateResponse = await createDocument(admin, baseURL, { title: `Private ${tag}`, spaceId: privateSpace.id, content: 'Private source' });
        expect(privateResponse.status()).toBe(201);
        const privateDocument = await privateResponse.json();
        const secretImage = await upload(admin, baseURL, privateDocument.id, `secret-image-${tag}.png`, 'image/png', FOREIGN_IMAGE);
        const secretBytes = `PRIVATE_ATTACHMENT_BYTES_${tag}`;
        const secretFile = await upload(admin, baseURL, privateDocument.id, `secret-file-${tag}.txt`, 'text/plain', Buffer.from(secretBytes));
        expect((await admin.post(`${baseURL}/api/documents/${privateDocument.id}/publish`)).ok()).toBeTruthy();
        const content = f.content + `\n\n![Недоступная схема](/api/attachments/${secretImage.id}/download)`
            + `\n\n[Недоступный файл](/api/attachments/${secretFile.id}/download)`;
        expect((await admin.put(`${baseURL}/api/documents/${f.document.id}`, { data: { content } })).ok()).toBeTruthy();
        const user = await createUser(admin, baseURL, { login: `export_guest_${tag}`, email: `export_${tag}@test.local`, password: 'password123', role: 'GUEST' });
        await grantSpacePermission(admin, baseURL, f.space.id, user.id, 'READ');
        reader = await createUserApi(baseURL, user.login, 'password123');
        expect((await reader.get(`${baseURL}/api/attachments/${secretImage.id}/download`)).status()).toBe(403);
        expect((await reader.get(`${baseURL}/api/attachments/${secretFile.id}/download`)).status()).toBe(403);
        expect((await reader.get(`${baseURL}/api/documents/${privateDocument.id}/export/zip`)).status()).toBe(403);
        const htmlResponse = await reader.get(`${baseURL}/api/documents/${f.document.id}/export/html`);
        expect(htmlResponse.status()).toBe(200);
        const html = await htmlResponse.text();
        expect(html).not.toContain(secretBytes);
        expect(html).not.toContain(secretFile.filename);
        expect(html).not.toContain(FOREIGN_IMAGE.toString('base64'));
        expect(html).not.toContain(`/api/attachments/${secretImage.id}/download`);
        expect(html).not.toContain(`/api/attachments/${secretFile.id}/download`);
        for (const format of ['zip', 'pdf', 'docx']) {
            const response = await reader.get(`${baseURL}/api/documents/${f.document.id}/export/${format}`);
            expect(response.status()).toBe(200);
            const output = testInfo.outputPath(`restricted.${format}`);
            await fs.writeFile(output, await response.body());
            if (format === 'pdf') continue;
            const directory = testInfo.outputPath(`restricted-${format}`);
            const entries = unpack(output, directory);
            if (format === 'zip') {
                expect(entries.filter(name => name.startsWith('attachments/') && !name.endsWith('/'))).toHaveLength(3);
                expect(entries.some(name => name.includes('secret-'))).toBeFalsy();
                for (const entry of entries.filter(name => !name.endsWith('/'))) {
                    expect((await fs.readFile(path.join(directory, entry))).includes(Buffer.from(secretBytes))).toBeFalsy();
                }
            } else {
                expect(entries.filter(name => name.startsWith('word/media/') && !name.endsWith('/'))).toHaveLength(1);
            }
        }
    } finally {
        if (reader) await reader.dispose();
        await admin.dispose();
    }
});

test('EXPORT-04: shared table menu offers ZIP and displays a controlled export limit error', async ({ browser, baseURL }) => {
    const api = await createAdminApi(baseURL);
    const f = await fixture(api, baseURL);
    const { context, page } = await newBrowserPageFromApi(browser, api);
    try {
        await page.route(`**/api/documents/${f.document.id}/export/zip`, route => route.fulfill({
            status: 413, json: { code: 'EXPORT_LIMIT_EXCEEDED', message: 'Размер вложений превышает лимит экспорта.' }
        }));
        await page.goto(`${baseURL}/?spaceId=${f.space.id}`);
        const row = page.locator('#documentsTbody tr').filter({ hasText: f.document.title });
        await expect(row).toBeVisible();
        await row.getByRole('button', { name: 'Экспорт' }).click();
        await page.getByRole('link', { name: 'HTML с вложениями (ZIP)', exact: true }).click();
        await expect(page.locator('#toast')).toContainText('Размер вложений превышает лимит экспорта.');
        await expect(page.locator('#toast')).toHaveClass(/error/);
    } finally {
        await context.close();
        await api.dispose();
    }
});
