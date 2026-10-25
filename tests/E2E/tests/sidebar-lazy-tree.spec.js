const { test, expect } = require("@playwright/test");
const { createAdminApi, createSpace, createDocument, newBrowserPageFromApi } = require("./helpers/documents");

async function fixture(browser, baseURL) {
  const api = await createAdminApi(baseURL);
  const space = await createSpace(api, baseURL, `Lazy fixture ${Date.now()}`);
  const response = await createDocument(api, baseURL, { spaceId: space.id, title: `Lazy document ${Date.now()}`, content: "Content" });
  expect(response.status()).toBe(201);
  const document = await response.json();
  return { api, space, document, ...await newBrowserPageFromApi(browser, api) };
}

test("SIDEBAR-02: trees load only on demand and reuse a single in-flight request", async ({ browser, baseURL }) => {
  const f = await fixture(browser, baseURL);
  let requests = 0;
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  await f.page.route(`**/spaces/${f.space.id}/tree*`, async route => {
    requests++;
    await gate;
    await route.continue();
  });
  const response = await f.page.goto(`${baseURL}/`);
  expect(await response.text()).not.toContain('class="document-row"');
  const tree = f.page.locator(`#tree-${f.space.id}`);
  const button = f.page.locator(`.space-button[data-space-id="${f.space.id}"]`);
  expect(requests).toBe(0);
  await button.click();
  await expect.poll(() => requests).toBe(1);
  await button.click();
  await button.click();
  release();
  await expect(tree.locator(`[data-document-id="${f.document.id}"]`)).toBeVisible();
  await button.click();
  await button.click();
  await expect(tree.locator(`[data-document-id="${f.document.id}"]`)).toBeVisible();
  expect(requests).toBe(1);
  await f.context.close();
  await f.api.dispose();
});

test("SIDEBAR-03: failed tree load offers a working retry", async ({ browser, baseURL }) => {
  const f = await fixture(browser, baseURL);
  let requests = 0;
  await f.page.route(`**/spaces/${f.space.id}/tree*`, async route => {
    requests++;
    if (requests === 1) await route.fulfill({ status: 503, body: "Unavailable" });
    else await route.continue();
  });
  await f.page.goto(`${baseURL}/`);
  await f.page.locator(`.space-button[data-space-id="${f.space.id}"]`).click();
  const tree = f.page.locator(`#tree-${f.space.id}`);
  await tree.getByRole("button", { name: "Повторить" }).click();
  await expect(tree.locator(`[data-document-id="${f.document.id}"]`)).toBeVisible();
  expect(requests).toBe(2);
  await f.context.close();
  await f.api.dispose();
});

test("SIDEBAR-04: dropping into an unopened space appends after its existing documents", async ({ browser, baseURL }) => {
  const f = await fixture(browser, baseURL);
  const target = await createSpace(f.api, baseURL, `Drop target ${Date.now()}`);
  const existing = await (await createDocument(f.api, baseURL, {
    spaceId: target.id, title: `Existing target ${Date.now()}`,
  })).json();
  await f.page.goto(`${baseURL}/`);
  await f.page.locator(`.space-button[data-space-id="${f.space.id}"]`).click();
  const source = f.page.locator(`#tree-${f.space.id} [data-document-id="${f.document.id}"] > .document-row`);
  await expect(source).toBeVisible();
  const moved = f.page.waitForResponse(response =>
    response.url().endsWith(`/api/documents/${f.document.id}/move`) && response.request().method() === "POST");
  await source.dragTo(f.page.locator(`.space-button[data-space-id="${target.id}"]`));
  expect((await moved).ok()).toBeTruthy();
  const tree = await f.api.get(`${baseURL}/spaces/${target.id}/tree`);
  expect(tree.ok()).toBeTruthy();
  const html = await tree.text();
  const first = html.indexOf(`data-document-id="${existing.id}"`);
  const second = html.indexOf(`data-document-id="${f.document.id}"`);
  expect(first).toBeGreaterThanOrEqual(0);
  expect(second).toBeGreaterThan(first);
  await f.context.close();
  await f.api.dispose();
});
