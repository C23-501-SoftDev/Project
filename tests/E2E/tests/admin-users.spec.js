const { test, expect } = require("@playwright/test");
const { createAuthenticatedPage } = require("./helpers/session");

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

  await page.goto("/admin/users");
  await page.getByRole("button", { name: /создать пользователя/i }).click();
  await page.locator("#userLogin").fill(login);
  await page.locator("#userEmail").fill(email);
  await page.locator("#userRole").selectOption("READER");
  await page.locator("#userPassword").fill("TempPass123!");
  await page.locator("#saveUserBtn").click();

  await expect(page.locator("#usersTbody")).toContainText(login);

  const createdRow = page
    .locator("#usersTbody tr")
    .filter({ hasText: login })
    .first();
  await createdRow.getByRole("button", { name: /редактировать/i }).click();

  await page.locator("#userLogin").fill(updatedLogin);
  await page.locator("#userEmail").fill(updatedEmail);
  await page.locator("#userRole").selectOption("EDITOR");
  await page.locator("#saveUserBtn").click();

  await expect(page.locator("#usersTbody")).toContainText(updatedLogin);

  const updatedRow = page
    .locator("#usersTbody tr")
    .filter({ hasText: updatedLogin })
    .first();
  await updatedRow.getByRole("button", { name: /удалить/i }).click();
  await page.locator("#confirmDeleteBtn").click();
  await expect(page.locator("#usersTbody")).not.toContainText(updatedLogin);

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

  await page.locator("th[data-sort='login']").click();
  await expect(page.locator("th[data-sort='login']")).toHaveClass(/sort-asc|sort-desc/);

  await page.locator("#searchInput").fill("admin");
  await page.locator("#applyFiltersBtn").click();
  await expect(page.locator("#usersTbody")).toBeVisible();

  await page.locator("#clearFiltersBtn").click();
  await expect(page.locator("#searchInput")).toHaveValue("");

  await context.close();
});

