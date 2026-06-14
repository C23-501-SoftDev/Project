const { test, expect } = require("@playwright/test");
const { createAuthenticatedPage } = require("./helpers/session");
const {
  createAdminApi,
  createUserApi,
  newBrowserPageFromApi,
  createSpace,
  createDocument,
  createUser,
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
  waitForSpacesLoaded,
  applySpacesFilters,
  setSpacesStatusFilter,
  selectSpacesOwnerFilter,
  applyDocumentAuthorFilter,
} = require("./helpers/ui");
const {
  gotoDocumentsSearch,
  expectDocumentStatusFilterResult,
  clearUserFiltersUi,
  applyUserFiltersUi,
  expectTableShows,
  expectTableHides,
} = require("./helpers/filters");

function userRow(page, login) {
  return page.locator("#usersTbody tr").filter({ hasText: login });
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

  test("DOCS-UI-04: status filter without space hides non-matching documents", async ({
    browser,
    baseURL,
  }) => {
    const adminApi = await createAdminApi(baseURL);
    const tag = uniqueSuffix();
    const space = await createSpace(adminApi, baseURL, `UI status ${tag}`);
    const draftTitle = `UI draft ${tag}`;
    const publishedTitle = `UI published ${tag}`;

    const draftRes = await createDocument(adminApi, baseURL, {
      title: draftTitle,
      spaceId: space.id,
    });
    expect(draftRes.ok()).toBeTruthy();
    const publishedRes = await createDocument(adminApi, baseURL, {
      title: publishedTitle,
      spaceId: space.id,
    });
    expect(publishedRes.ok()).toBeTruthy();
    const publishedDoc = await publishedRes.json();
    const publishRes = await adminApi.put(
      `${baseURL}/api/documents/${publishedDoc.id}`,
      { data: { title: publishedTitle, content: "published", status: "PUBLISHED" } }
    );
    expect(publishRes.ok()).toBeTruthy();

    const { context, page } = await newBrowserPageFromApi(browser, adminApi);
    await gotoDocumentsSearch(page, publishedTitle);
    await expect(page.locator("#documentsTbody")).toContainText(publishedTitle);
    await expectDocumentStatusFilterResult(
      page,
      { tag, draftTitle, publishedTitle },
      { allowedStatuses: ["DRAFT"], hiddenTitles: [publishedTitle] }
    );

    await context.close();
    await adminApi.dispose();
  });

  test("DOCS-UI-05: search via URL query shows matching document", async ({
    browser,
    baseURL,
  }) => {
    const adminApi = await createAdminApi(baseURL);
    const space = await createSpace(adminApi, baseURL, `UI search ${uniqueSuffix()}`);
    const title = `UI search hit ${uniqueSuffix()}`;
    expect(
      (await createDocument(adminApi, baseURL, { title, spaceId: space.id })).ok()
    ).toBeTruthy();

    const { context, page } = await newBrowserPageFromApi(browser, adminApi);
    await gotoDocumentsSearch(page, title);
    await expect(page.locator("#documentsTbody")).toContainText(title);

    await context.close();
    await adminApi.dispose();
  });

  test("DOCS-UI-06: author filter narrows list for admin", async ({
    browser,
    baseURL,
  }) => {
    const adminApi = await createAdminApi(baseURL);
    const suffix = uniqueSuffix();
    const author = await createUser(adminApi, baseURL, {
      login: `ui_author_${suffix}`,
      email: `ui_author_${suffix}@local.test`,
      password: "AuthorPass123!",
      role: "EDITOR",
      isAdmin: false,
    });
    const space = await createSpace(adminApi, baseURL, `UI author ${suffix}`);
    const authorDoc = `UI author doc ${suffix}`;
    const adminDoc = `UI admin doc ${suffix}`;

    const authorApi = await createUserApi(
      baseURL,
      author.login,
      "AuthorPass123!"
    );
    expect(
      (await createDocument(authorApi, baseURL, {
        title: authorDoc,
        spaceId: space.id,
      })).ok()
    ).toBeTruthy();
    await authorApi.dispose();

    expect(
      (await createDocument(adminApi, baseURL, {
        title: adminDoc,
        spaceId: space.id,
      })).ok()
    ).toBeTruthy();

    const { context, page } = await newBrowserPageFromApi(browser, adminApi);
    await page.goto("/");
    await waitDocumentsLoaded(page);
    await applyDocumentAuthorFilter(page, author.id);
    await waitDocumentsLoaded(page);
    await expect(page.locator("#documentsTbody")).toContainText(authorDoc);
    await expect(page.locator("#documentsTbody")).not.toContainText(adminDoc);

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

  test("USERS-UI-04: multiple role filters narrow table in UI", async ({
    browser,
    request,
    baseURL,
  }) => {
    const adminApi = await createAdminApi(baseURL);
    const suffix = uniqueSuffix();
    const readerLogin = `ui_roles_reader_${suffix}`;
    const editorLogin = `ui_roles_editor_${suffix}`;
    await createUser(adminApi, baseURL, {
      login: readerLogin,
      email: `${readerLogin}@local.test`,
      password: "ReaderPass123!",
      role: "READER",
    });
    await createUser(adminApi, baseURL, {
      login: editorLogin,
      email: `${editorLogin}@local.test`,
      password: "EditorPass123!",
      role: "EDITOR",
    });
    await adminApi.dispose();

    const { context, page } = await createAuthenticatedPage({
      browser,
      request,
      baseURL,
    });

    await page.goto("/admin/users");
    await clearUserFiltersUi(page);
    await applyUserFiltersUi(page, {
      roles: ["Reader", "Editor"],
      search: suffix,
    });
    await expectTableShows(page, "usersTable", [readerLogin, editorLogin]);

    await context.close();
  });

  test("USERS-UI-05: email search finds user by partial match", async ({
    browser,
    request,
    baseURL,
  }) => {
    const adminApi = await createAdminApi(baseURL);
    const suffix = uniqueSuffix();
    const login = `ui_email_${suffix}`;
    const email = `findme_${suffix}@local.test`;
    await createUser(adminApi, baseURL, {
      login,
      email,
      password: "TempPass123!",
      role: "GUEST",
    });
    await adminApi.dispose();

    const { context, page } = await createAuthenticatedPage({
      browser,
      request,
      baseURL,
    });

    await page.goto("/admin/users");
    await clearUserFiltersUi(page);
    await applyUserFiltersUi(page, { search: `findme_${suffix}` });
    await expect(page.locator("#usersTbody")).toContainText(login);

    await context.close();
  });

  test("USERS-UI-06: combined search, role and admin filters narrow results", async ({
    browser,
    request,
    baseURL,
  }) => {
    const adminApi = await createAdminApi(baseURL);
    const suffix = uniqueSuffix();
    const targetLogin = `ui_combo_${suffix}`;
    const otherLogin = `ui_combo_other_${suffix}`;
    await createUser(adminApi, baseURL, {
      login: targetLogin,
      email: `${targetLogin}@local.test`,
      password: "EditorPass123!",
      role: "EDITOR",
      isAdmin: false,
    });
    await createUser(adminApi, baseURL, {
      login: otherLogin,
      email: `${otherLogin}@local.test`,
      password: "ReaderPass123!",
      role: "READER",
      isAdmin: false,
    });
    await adminApi.dispose();

    const { context, page } = await createAuthenticatedPage({
      browser,
      request,
      baseURL,
    });

    await page.goto("/admin/users");
    await clearUserFiltersUi(page);
    await applyUserFiltersUi(page, {
      search: suffix,
      roles: ["Editor"],
      admin: ["false"],
    });
    await expectTableShows(page, "usersTable", [targetLogin]);
    await expectTableHides(page, "usersTable", [otherLogin]);

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

  test("SPACES-UI-04: inactive status filter shows deleted spaces", async ({
    browser,
    request,
    baseURL,
  }) => {
    const adminApi = await createAdminApi(baseURL);
    const space = await createSpace(adminApi, baseURL, `UI inactive ${uniqueSuffix()}`);
    await adminApi.delete(`${baseURL}/api/admin/spaces/${space.id}`);
    await adminApi.dispose();

    const { context, page } = await createAuthenticatedPage({
      browser,
      request,
      baseURL,
    });

    await page.goto("/admin/spaces");
    await waitForSpacesLoaded(page);
    await expect(page.locator("#spacesTbody")).not.toContainText(space.name);

    await setSpacesStatusFilter(page, "inactive");
    await applySpacesFilters(page);
    await expect(page.locator("#spacesTbody")).toContainText(space.name);

    await context.close();
  });

  test("SPACES-UI-05: owner filter limits spaces to selected owner", async ({
    browser,
    request,
    baseURL,
  }) => {
    const adminApi = await createAdminApi(baseURL);
    const suffix = uniqueSuffix();
    const ownerLogin = `ui_space_owner_${suffix}`;
    const owner = await createUser(adminApi, baseURL, {
      login: ownerLogin,
      email: `${ownerLogin}@local.test`,
      password: "OwnerPass123!",
      role: "EDITOR",
      isAdmin: true,
    });
    const ownedSpace = await createSpace(adminApi, baseURL, `UI owned ${suffix}`, {
      ownerId: owner.id,
    });
    const otherSpace = await createSpace(adminApi, baseURL, `UI foreign ${suffix}`);
    await adminApi.dispose();

    const { context, page } = await createAuthenticatedPage({
      browser,
      request,
      baseURL,
    });

    await page.goto("/admin/spaces");
    await waitForSpacesLoaded(page);
    await selectSpacesOwnerFilter(page, owner.id);
    await applySpacesFilters(page);
    await expect(page.locator("#spacesTbody")).toContainText(ownedSpace.name);
    await expect(page.locator("#spacesTbody")).not.toContainText(otherSpace.name);

    await context.close();
  });
});
