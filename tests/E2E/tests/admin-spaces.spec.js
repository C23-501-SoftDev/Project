const { test, expect } = require("@playwright/test");
const { createAuthenticatedPage } = require("./helpers/session");

test("C1: spaces table loads", async ({ browser, request, baseURL }) => {
  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  await page.goto("/admin/spaces");
  await expect(page.locator("#spacesTable")).toBeVisible();
  await expect(page.locator("#spacesTbody")).toBeVisible();

  await context.close();
});

test("C2: create space", async ({ browser, request, baseURL }) => {
  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  const spaceName = `E2E Space ${Date.now()}`;

  await page.goto("/admin/spaces");
  await page.getByRole("button", { name: /создать пространство/i }).click();
  await page.locator("#spaceName").fill(spaceName);
  await page.locator("#spaceDesc").fill("Playwright E2E temp space");
  await page.locator("#saveSpaceBtn").click();

  await expect(page.locator("#spacesTbody")).toContainText(spaceName);
  await context.close();
});

test("C4: edit/delete/revoke controls show WIP behavior", async ({
  browser,
  request,
  baseURL,
}) => {
  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  await page.goto("/admin/spaces");
  const firstRow = page.locator("#spacesTbody tr").first();
  await firstRow.getByRole("button", { name: /редактировать/i }).click();
  await expect(page.locator("#toast")).toContainText(/в разработке/i);

  await firstRow.getByRole("button", { name: /удалить/i }).click();
  await expect(page.locator("#toast")).toContainText(/в разработке/i);

  await context.close();
});

