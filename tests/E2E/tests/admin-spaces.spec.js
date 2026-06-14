const { test, expect } = require("@playwright/test");
const { createAuthenticatedPage } = require("./helpers/session");
const {
  selectFirstSpaceOwner,
  waitForSpacesLoaded,
  applySpacesFilters,
  setSpacesStatusFilter,
  selectSpacesOwnerFilter,
} = require("./helpers/ui");
const {
  createAdminApi,
  createSpace,
  createUser,
  uniqueSuffix,
} = require("./helpers/documents");

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

test("C5: spaces owner and status filters narrow the table", async ({
  browser,
  request,
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const suffix = uniqueSuffix();
  const ownerLogin = `e2e_owner_${suffix}`;
  const owner = await createUser(adminApi, baseURL, {
    login: ownerLogin,
    email: `${ownerLogin}@local.test`,
    password: "OwnerPass123!",
    role: "EDITOR",
    isAdmin: true,
  });
  const ownedSpace = await createSpace(adminApi, baseURL, `E2E owner space ${suffix}`, {
    ownerId: owner.id,
  });
  const otherSpace = await createSpace(adminApi, baseURL, `E2E other space ${suffix}`);
  await adminApi.delete(`${baseURL}/api/admin/spaces/${otherSpace.id}`);
  await adminApi.dispose();

  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  await page.goto("/admin/spaces");
  await waitForSpacesLoaded(page);
  await expect(page.locator("#spacesTbody")).toContainText(ownedSpace.name);
  await expect(page.locator("#spacesTbody")).not.toContainText(otherSpace.name);

  await selectSpacesOwnerFilter(page, owner.id);
  await applySpacesFilters(page);
  await expect(page.locator("#spacesTbody")).toContainText(ownedSpace.name);

  await page.locator("#clearFiltersBtn").click();
  await waitForSpacesLoaded(page);

  await setSpacesStatusFilter(page, "inactive");
  await applySpacesFilters(page);
  await expect(page.locator("#spacesTbody")).toContainText(otherSpace.name);
  await expect(page.locator("#spacesTbody")).not.toContainText(ownedSpace.name);

  await page.locator("#clearFiltersBtn").click();
  await waitForSpacesLoaded(page);
  await expect(page.locator("#spacesTbody")).toContainText(ownedSpace.name);

  await context.close();
});

test("C6: combined owner and active status filters are sent together", async ({
  browser,
  request,
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const suffix = uniqueSuffix();
  const me = await adminApi.get(`${baseURL}/api/auth/me`);
  const adminUser = await me.json();
  const space = await createSpace(adminApi, baseURL, `E2E combo ${suffix}`);
  await adminApi.dispose();

  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  await page.goto("/admin/spaces");
  await waitForSpacesLoaded(page);

  await selectSpacesOwnerFilter(page, adminUser.id);
  await setSpacesStatusFilter(page, "active");
  await applySpacesFilters(page);
  await expect(page.locator("#spacesTbody")).toContainText(space.name);

  await context.close();
});
