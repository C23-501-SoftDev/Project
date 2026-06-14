const { test, expect } = require("@playwright/test");
const { createAuthenticatedPage } = require("./helpers/session");
const {
  prepareUsersTableNewestFirst,
  selectCustomOption,
} = require("./helpers/ui");
const {
  createAdminApi,
  createUser,
  uniqueSuffix,
} = require("./helpers/documents");
const {
  applyUserFiltersUi,
  clearUserFiltersUi,
  expectTableShows,
  expectTableHides,
} = require("./helpers/filters");

function userRow(page, login) {
  return page.locator("#usersTbody tr").filter({ hasText: login });
}

test("B1: users table loads", async ({ browser, request, baseURL }) => {
  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  await page.goto("/admin/users");
  await expect(page.locator("#usersTable")).toBeVisible();
  await expect(page.locator("#usersTbody")).toBeVisible();

  await context.close();
});

test("B2+B3+B5: create, edit and delete user", async ({
  browser,
  request,
  baseURL,
}) => {
  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  const idSuffix = Date.now();
  const login = `e2e_user_${idSuffix}`;
  const email = `e2e_${idSuffix}@test.local`;
  const updatedLogin = `${login}_u`;
  const updatedEmail = `e2e_u_${idSuffix}@test.local`;

  await prepareUsersTableNewestFirst(page);

  await page.getByRole("button", { name: /создать пользователя/i }).click();
  await expect(page.locator("#userModal")).toBeVisible();

  await page.locator("#userLogin").fill(login);
  await page.locator("#userEmail").fill(email);
  await selectCustomOption(page, "userRoleWrapper", "READER");
  await page.locator("#userPassword").fill("TempPass123!");

  const createResponse = page.waitForResponse(
    (response) =>
      response.url().includes("/api/admin/users") &&
      response.request().method() === "POST"
  );
  const tableAfterCreate = page.waitForResponse(
    (response) =>
      response.url().includes("/api/admin/users") &&
      response.request().method() === "GET" &&
      response.ok()
  );
  await page.locator("#saveUserBtn").click();
  expect((await createResponse).status()).toBe(201);
  await tableAfterCreate;
  await expect(page.locator("#userModal")).not.toBeVisible();

  const createdRow = userRow(page, login);
  await expect(createdRow).toHaveCount(1);
  await createdRow.getByRole("button", { name: /редактировать/i }).click();
  await expect(page.locator("#userModal")).toBeVisible();

  await page.locator("#userLogin").fill(updatedLogin);
  await page.locator("#userEmail").fill(updatedEmail);
  await selectCustomOption(page, "userRoleWrapper", "EDITOR");

  const updateResponse = page.waitForResponse(
    (response) =>
      response.url().includes(`/api/admin/users/`) &&
      response.request().method() === "PUT"
  );
  const tableAfterUpdate = page.waitForResponse(
    (response) =>
      response.url().includes("/api/admin/users") &&
      response.request().method() === "GET" &&
      response.ok()
  );
  await page.locator("#saveUserBtn").click();
  expect((await updateResponse).status()).toBe(200);
  await tableAfterUpdate;
  await expect(page.locator("#userModal")).not.toBeVisible();

  const updatedRow = userRow(page, updatedLogin);
  await expect(updatedRow).toHaveCount(1);

  const deleteResponse = page.waitForResponse(
    (response) =>
      response.url().includes("/api/admin/users/") &&
      response.request().method() === "DELETE"
  );
  const tableAfterDelete = page.waitForResponse(
    (response) =>
      response.url().includes("/api/admin/users") &&
      response.request().method() === "GET" &&
      response.ok()
  );
  await updatedRow.getByRole("button", { name: /удалить/i }).click();
  await page.locator("#confirmDeleteBtn").click();
  expect((await deleteResponse).status()).toBe(200);
  await tableAfterDelete;

  await expect(userRow(page, updatedLogin)).toHaveCount(0);

  await context.close();
});

test("B7+B8: users search, role, admin and combined filters work", async ({
  browser,
  request,
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const suffix = uniqueSuffix();
  const readerLogin = `e2e_filt_reader_${suffix}`;
  const editorLogin = `e2e_filt_editor_${suffix}`;

  await createUser(adminApi, baseURL, {
    login: readerLogin,
    email: `${readerLogin}@local.test`,
    password: "ReaderPass123!",
    role: "READER",
    isAdmin: false,
  });
  await createUser(adminApi, baseURL, {
    login: editorLogin,
    email: `${editorLogin}@local.test`,
    password: "EditorPass123!",
    role: "EDITOR",
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

  await applyUserFiltersUi(page, { search: readerLogin });
  await expectTableShows(page, "usersTable", [readerLogin]);
  await expectTableHides(page, "usersTable", [editorLogin]);

  await clearUserFiltersUi(page);
  await applyUserFiltersUi(page, { roles: ["Reader"], search: suffix });
  await expectTableShows(page, "usersTable", [readerLogin]);
  await expectTableHides(page, "usersTable", [editorLogin]);

  await clearUserFiltersUi(page);
  await applyUserFiltersUi(page, { admin: ["true"], search: "admin" });
  await expectTableShows(page, "usersTable", ["admin"]);
  await expectTableHides(page, "usersTable", [readerLogin]);

  await clearUserFiltersUi(page);
  await applyUserFiltersUi(page, {
    search: suffix,
    roles: ["Editor"],
    admin: ["false"],
  });
  await expectTableShows(page, "usersTable", [editorLogin]);
  await expectTableHides(page, "usersTable", [readerLogin]);

  await context.close();
});

test("B9: deleted users appear only with deleted status filter", async ({
  browser,
  request,
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const suffix = uniqueSuffix();
  const login = `e2e_filt_del_${suffix}`;
  const created = await createUser(adminApi, baseURL, {
    login,
    email: `${login}@local.test`,
    password: "TempPass123!",
    role: "GUEST",
    isAdmin: false,
  });
  const deleteRes = await adminApi.delete(`${baseURL}/api/admin/users/${created.id}`);
  expect(deleteRes.ok()).toBeTruthy();
  await adminApi.dispose();

  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  await page.goto("/admin/users");
  await clearUserFiltersUi(page);
  await applyUserFiltersUi(page, { status: "active", search: login });
  await expect(page.locator("#usersTbody")).toContainText(/не найдены|не найден/i);

  await clearUserFiltersUi(page);
  await applyUserFiltersUi(page, { status: "deleted", search: login });
  await expectTableShows(page, "usersTable", [login]);

  await clearUserFiltersUi(page);
  await applyUserFiltersUi(page, { status: "active", search: login });
  await expect(page.locator("#usersTbody")).toContainText(/не найдены|не найден/i);

  await context.close();
});
