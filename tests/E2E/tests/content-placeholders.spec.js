const { test, expect } = require("./helpers/test");
const { createAuthenticatedPage } = require("./helpers/session");
const { createContentFixture } = require("./helpers/documents");

test("D1: implemented content routes open with real UI", async ({ browser, request, baseURL }) => {
  const { context, page } = await createAuthenticatedPage({ browser, request, baseURL });
  const { document } = await createContentFixture(request, baseURL);
  for (const [route, selector] of [
    ["/", "#documentsTable"],
    ["/documents/new", "#createDocForm"],
    [`/documents/${document.id}`, "#documentContent"],
    [`/documents/${document.id}/edit`, "#editorTextarea"],
  ]) {
    expect((await page.goto(route)).status()).toBe(200);
    await expect(page.locator(selector)).toBeVisible();
  }
  await context.close();
});

test("D2: history and search display saved document data", async ({ browser, request, baseURL }) => {
  const { context, page } = await createAuthenticatedPage({ browser, request, baseURL });
  const { document } = await createContentFixture(request, baseURL);
  await page.goto(`/documents/${document.id}/history`);
  await expect(page.locator("#diffFrom option:not([value=\"\"])")).toHaveCount(2);
  await page.locator("#diffSelector button[type=submit]").click();
  await expect(page.locator("#diffResult")).toContainText("version");
  await page.goto(`/search?q=${encodeURIComponent(document.title)}`);
  await expect(page.locator(".search-result-item")).toContainText(document.title);
  await context.close();
});
