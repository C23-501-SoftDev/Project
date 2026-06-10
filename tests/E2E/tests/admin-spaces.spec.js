const { test, expect } = require("@playwright/test");
const { createAuthenticatedPage } = require("./helpers/session");
const {
  filterCustomSelect,
  selectFirstSpaceOwner,
  selectSpaceOwnerBySearch,
} = require("./helpers/ui");

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

test("C2a: owner select filters preloaded options", async ({
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
  await page.getByRole("button", { name: /создать пространство/i }).click();
  await page.evaluate(() => {
    window.clearCustomSelectOptions("spaceOwnerWrapper");
    window.populateCustomSelect(
      "spaceOwnerWrapper",
      "101",
      "alice (alice@test.com)"
    );
    window.populateCustomSelect(
      "spaceOwnerWrapper",
      "102",
      "bob (bob@test.com)"
    );
  });

  await filterCustomSelect(page, "spaceOwnerWrapper", "bob");
  const wrapper = page.locator("#spaceOwnerWrapper");
  await expect(
    wrapper.locator('.select-option[data-value="102"]')
  ).toBeVisible();
  await expect(
    wrapper.locator('.select-option[data-value="101"]')
  ).not.toBeVisible();
  await wrapper.locator('.select-option[data-value="102"]').click();
  await expect(page.locator("#spaceOwner")).toHaveValue("102");

  await context.close();
});

test("C2b: owner select shows users without typing", async ({
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
  await page.getByRole("button", { name: /создать пространство/i }).click();
  const listResponse = page.waitForResponse(
    (r) =>
      r.url().includes("/api/admin/users") &&
      !r.url().match(/\/api\/admin\/users\/\d+/) &&
      r.request().method() === "GET" &&
      r.ok()
  );
  const wrapper = page.locator("#spaceOwnerWrapper");
  await wrapper.locator(".select-styled").click();
  await listResponse;
  await expect(
    wrapper.locator(".select-option:not(.select-message)")
  ).not.toHaveCount(0);

  await context.close();
});

test("C2c: owner select server search", async ({ browser, request, baseURL }) => {
  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  await page.goto("/admin/spaces");
  await page.getByRole("button", { name: /создать пространство/i }).click();
  await selectSpaceOwnerBySearch(page, "admin");
  await expect(page.locator("#spaceOwner")).not.toHaveValue("");

  await context.close();
});

test("C2d: owner server search shows empty for unknown query", async ({
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
  await page.getByRole("button", { name: /создать пространство/i }).click();

  const searchResponse = page.waitForResponse(
    (r) =>
      r.url().includes("/api/admin/users/search") &&
      r.url().includes("q=") &&
      r.request().method() === "GET" &&
      r.ok()
  );
  await filterCustomSelect(page, "spaceOwnerWrapper", "zzznomatch999");
  await searchResponse;
  await expect(
    page.locator("#spaceOwnerWrapper .select-message")
  ).toContainText(/ничего не найдено/i);

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
  await selectFirstSpaceOwner(page);

  const createResponse = page.waitForResponse(
    (r) =>
      r.url().includes("/api/admin/spaces") && r.request().method() === "POST"
  );
  await page.locator("#saveSpaceBtn").click();
  const response = await createResponse;
  expect(response.status()).toBe(201);
  const created = await response.json();
  expect(created.name).toBe(spaceName);

  await expect(page.locator("#spaceModal")).not.toBeVisible();
  await expect(page.locator("#toast")).toContainText(/успешно/i);
  await context.close();
});

test("C4: edit and delete controls open modal / perform action", async ({
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
  await expect(page.locator("#spacesTbody tr").first()).toBeVisible();
  const firstRow = page.locator("#spacesTbody tr").first();

  await firstRow.getByRole("button", { name: /редактировать/i }).click();
  await expect(page.locator("#spaceModal")).toBeVisible();
  await expect(page.locator("#spaceModalTitle")).toContainText(/редактировать/i);
  await page.locator("#closeSpaceModalBtn").click();
  await expect(page.locator("#spaceModal")).not.toBeVisible();

  const deleteResponse = page.waitForResponse(
    (r) =>
      r.url().includes("/api/admin/spaces/") &&
      r.request().method() === "DELETE"
  );
  await firstRow.getByRole("button", { name: /удалить/i }).click();
  expect([200, 204]).toContain((await deleteResponse).status());
  await expect(page.locator("#toast")).toContainText(/удалено/i);

  await context.close();
});

