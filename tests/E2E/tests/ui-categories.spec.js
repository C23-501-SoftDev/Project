const { test, expect, request: playwrightRequest } = require("@playwright/test");
const { createAuthenticatedPage } = require("./helpers/session");
const {
  createAdminApi,
  newBrowserPageFromApi,
  createSpace,
  createDocument,
  uniqueSuffix,
} = require("./helpers/documents");
const {
  selectDocSpace,
  waitForDocSpaceOptions,
  applyDocumentSpaceFilter,
  waitDocumentsLoaded,
  selectCustomOption,
  prepareUsersTableNewestFirst,
  selectFirstSpaceOwner,
} = require("./helpers/ui");

function userRow(page, login) {
  return page.locator("#usersTbody tr").filter({ hasText: login });
}

async function createUserViaAdminUi(page, { login, email, role, password }) {
  await page.getByRole("button", { name: /создать пользователя/i }).click();
  await expect(page.locator("#userModal")).toBeVisible();
  await page.locator("#userLogin").fill(login);
  await page.locator("#userEmail").fill(email);
  await selectCustomOption(page, "userRoleWrapper", role);
  await page.locator("#userPassword").fill(password);
  const createRes = page.waitForResponse(
    (r) => r.url().includes("/api/admin/users") && r.request().method() === "POST"
  );
  await page.locator("#saveUserBtn").click();
  expect((await createRes).status()).toBe(201);
}

test.describe("DOCS UI", () => {
  test("DOCS-UI-01: documents list page renders controls and table", async ({
    browser,
    request,
    baseURL,
  }) => {
    const { context, page } = await createAuthenticatedPage({
      browser,
      request,
      baseURL,
    });

    await page.goto("/");
    await expect(page.locator("h1")).toContainText("Документы");
    await expect(page.locator("#documentsTable")).toBeVisible();
    await expect(page.locator('.statusFilter[value="Draft"]')).toBeVisible();
    await expect(page.locator("#spaceFilterWrapper")).toBeVisible();
    await expect(page.locator("#createBtn")).toBeVisible();

    await context.close();
  });

  test("DOCS-UI-02: create document form saves and redirects to editor", async ({
    browser,
    baseURL,
  }) => {
    const adminApi = await createAdminApi(baseURL);
    const space = await createSpace(adminApi, baseURL, `UI docs ${uniqueSuffix()}`);
    const title = `UI doc ${uniqueSuffix()}`;
    const { context, page } = await newBrowserPageFromApi(browser, adminApi);

    await page.goto("/documents/new");
    await waitForDocSpaceOptions(page, 1);
    await page.locator("#docTitle").fill(title);
    await selectDocSpace(page, space.id);
    await page.locator("#docContent").fill("# UI category test");

    const createResponsePromise = page.waitForResponse(
      (r) => r.url().includes("/api/documents") && r.request().method() === "POST"
    );
    await page.locator("#submitBtn").click();
    expect((await createResponsePromise).status()).toBe(201);

    await expect(page).toHaveURL(/\/documents\/\d+\/edit$/);
    await expect(page.locator("#docTitleField")).toHaveValue(title);

    await context.close();
    await adminApi.dispose();
  });

  test("DOCS-UI-03: filtering by space hides other space documents", async ({
    browser,
    baseURL,
  }) => {
    const adminApi = await createAdminApi(baseURL);
    const spaceA = await createSpace(adminApi, baseURL, `UI filt A ${uniqueSuffix()}`);
    const spaceB = await createSpace(adminApi, baseURL, `UI filt B ${uniqueSuffix()}`);
    const visibleTitle = `UI visible ${uniqueSuffix()}`;
    const hiddenTitle = `UI hidden ${uniqueSuffix()}`;

    expect(
      (await createDocument(adminApi, baseURL, {
        title: visibleTitle,
        spaceId: spaceA.id,
      })).ok()
    ).toBeTruthy();
    expect(
      (await createDocument(adminApi, baseURL, {
        title: hiddenTitle,
        spaceId: spaceB.id,
      })).ok()
    ).toBeTruthy();

    const { context, page } = await newBrowserPageFromApi(browser, adminApi);
    await page.goto("/");
    await waitDocumentsLoaded(page);
    await applyDocumentSpaceFilter(page, spaceA.id, spaceA.name);
    await waitDocumentsLoaded(page);

    await expect(page.locator("#documentsTbody")).toContainText(visibleTitle);
    await expect(page.locator("#documentsTbody")).not.toContainText(hiddenTitle);

    await context.close();
    await adminApi.dispose();
  });
});

test.describe("USERS UI", () => {
  test("USERS-UI-01: admin users page loads with table and filters", async ({
    browser,
    request,
    baseURL,
  }) => {
    const { context, page } = await createAuthenticatedPage({
      browser,
      request,
      baseURL,
    });

    await page.goto("/admin/users");
    await expect(page.locator("#usersTable")).toBeVisible();
    await expect(page.locator("#usersTbody")).toBeVisible();
    await expect(page.locator("#searchInput")).toBeVisible();
    await expect(page.getByRole("button", { name: /создать пользователя/i })).toBeVisible();

    await context.close();
  });

  test("USERS-UI-02: create user from modal shows row in table", async ({
    browser,
    request,
    baseURL,
  }) => {
    const { context, page } = await createAuthenticatedPage({
      browser,
      request,
      baseURL,
    });
    const suffix = uniqueSuffix();
    const login = `ui_user_${suffix}`;
    const email = `ui_user_${suffix}@local.test`;

    await prepareUsersTableNewestFirst(page);
    await page.getByRole("button", { name: /создать пользователя/i }).click();
    await expect(page.locator("#userModal")).toBeVisible();

    await page.locator("#userLogin").fill(login);
    await page.locator("#userEmail").fill(email);
    await selectCustomOption(page, "userRoleWrapper", "READER");
    await page.locator("#userPassword").fill("TempPass123!");

    const createRes = page.waitForResponse(
      (r) => r.url().includes("/api/admin/users") && r.request().method() === "POST"
    );
    await page.locator("#saveUserBtn").click();
    expect((await createRes).status()).toBe(201);

    await expect(userRow(page, login)).toHaveCount(1);
    await context.close();
  });

  test("USERS-UI-03: search and clear filters behave correctly", async ({
    browser,
    request,
    baseURL,
  }) => {
    const { context, page } = await createAuthenticatedPage({
      browser,
      request,
      baseURL,
    });

    await page.goto("/admin/users");
    await expect(page.locator("#usersTbody")).not.toContainText("Загрузка");
    await page.locator("#searchInput").fill("admin");
    await page.locator("#applyFiltersBtn").click();
    await expect(page.locator("#usersTbody")).toContainText("admin");

    await page.locator("#clearFiltersBtn").click();
    await expect(page.locator("#searchInput")).toHaveValue("");

    await context.close();
  });
});

test.describe("SPACES UI", () => {
  test("SPACES-UI-01: admin spaces page loads with table", async ({
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
    await expect(page.locator("#spacesTable")).toBeVisible();
    await expect(page.locator("#spacesTbody")).toBeVisible();
    await expect(
      page.getByRole("button", { name: /создать пространство/i })
    ).toBeVisible();

    await context.close();
  });

  test("SPACES-UI-02: create space via modal succeeds", async ({
    browser,
    request,
    baseURL,
  }) => {
    const { context, page } = await createAuthenticatedPage({
      browser,
      request,
      baseURL,
    });

    const spaceName = `UI Space ${uniqueSuffix()}`;
    await page.goto("/admin/spaces");
    await page.getByRole("button", { name: /создать пространство/i }).click();
    await page.locator("#spaceName").fill(spaceName);
    await page.locator("#spaceDesc").fill("UI categories test");
    await selectFirstSpaceOwner(page);

    const createRes = page.waitForResponse(
      (r) => r.url().includes("/api/admin/spaces") && r.request().method() === "POST"
    );
    await page.locator("#saveSpaceBtn").click();
    expect((await createRes).status()).toBe(201);
    await expect(page.locator("#toast")).toContainText(/успешно/i);

    await context.close();
  });

  test("SPACES-UI-03: deleted space disappears from active list after reload", async ({
    browser,
    baseURL,
  }) => {
    const adminApi = await createAdminApi(baseURL);
    const space = await createSpace(adminApi, baseURL, `UI del ${uniqueSuffix()}`);
    await adminApi.dispose();

    const adminApi2 = await createAdminApi(baseURL);
    const { context, page } = await newBrowserPageFromApi(browser, adminApi2);
    await page.goto("/admin/spaces");
    await expect(page.locator("#spacesTbody")).toContainText(space.name);

    const row = page.locator("#spacesTbody tr", { hasText: space.name });
    const deleteRes = page.waitForResponse(
      (r) =>
        r.url().includes(`/api/admin/spaces/${space.id}`) &&
        r.request().method() === "DELETE"
    );
    await row.getByRole("button", { name: /удалить/i }).click();
    expect((await deleteRes).status()).toBe(204);

    await page.reload();
    await expect(page.locator("#spacesTbody")).not.toContainText(space.name);

    await context.close();
    await adminApi2.dispose();
  });
});
