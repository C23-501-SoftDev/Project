const { test, expect } = require("@playwright/test");
const { createAuthenticatedPage } = require("./helpers/session");

/** Таблица по умолчанию: id asc, 20 на странице — новый пользователь уходит на последнюю страницу. */
async function prepareUsersTableNewestFirst(page) {
  await page.goto("/admin/users");
  await expect(page.locator("#usersTbody")).not.toContainText("Загрузка");

  const idHeader = page.locator("th[data-sort='id']");
  await expect(idHeader).toBeVisible();

  const usersReload = page.waitForResponse(
    (response) =>
      response.url().includes("/api/admin/users") &&
      response.request().method() === "GET" &&
      response.ok()
  );
  await idHeader.click();
  await usersReload;

  await expect(idHeader).toHaveClass(/sort-desc/);
}

async function waitForUsersTableReload(page) {
  await page.waitForResponse(
    (response) =>
      response.url().includes("/api/admin/users") &&
      response.request().method() === "GET" &&
      response.ok()
  );
}

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
  await page.locator("#userRole").selectOption("READER");
  await page.locator("#userPassword").fill("TempPass123!");

  const createResponse = page.waitForResponse(
    (response) =>
      response.url().includes("/api/admin/users") &&
      response.request().method() === "POST"
  );
  const tableAfterCreate = waitForUsersTableReload(page);
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
  await page.locator("#userRole").selectOption("EDITOR");

  const updateResponse = page.waitForResponse(
    (response) =>
      response.url().includes(`/api/admin/users/`) &&
      response.request().method() === "PUT"
  );
  const tableAfterUpdate = waitForUsersTableReload(page);
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
  const tableAfterDelete = waitForUsersTableReload(page);
  await updatedRow.getByRole("button", { name: /удалить/i }).click();
  await page.locator("#confirmDeleteBtn").click();
  expect((await deleteResponse).status()).toBe(200);
  await tableAfterDelete;

  await expect(userRow(page, updatedLogin)).toHaveCount(0);

  await context.close();
});

test("B7+B8: users sorting and filtering UI works", async ({
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

  await page.locator("th[data-sort='login']").click();
  await expect(page.locator("th[data-sort='login']")).toHaveClass(/sort-asc|sort-desc/);

  await page.locator("#searchInput").fill("admin");
  await page.locator("#applyFiltersBtn").click();
  await expect(page.locator("#usersTbody")).toBeVisible();

  await page.locator("#clearFiltersBtn").click();
  await expect(page.locator("#searchInput")).toHaveValue("");

  await context.close();
});
