const { test, expect } = require("@playwright/test");
const { createAuthenticatedPage } = require("./helpers/session");

test("@strict layout assets must be available (no 5xx)", async ({
  request,
  baseURL,
}) => {
  const cssResponse = await request.get(`${baseURL}/css/main.css`);
  const jsResponse = await request.get(`${baseURL}/js/main.js`);

  expect(cssResponse.ok()).toBeTruthy();
  expect(jsResponse.ok()).toBeTruthy();
});

test("@strict login through UI form must redirect to home", async ({ page }) => {
  await page.goto("/login");
  await page.locator("#username").fill("admin");
  await page.locator("#password").fill("admin123");
  await page.getByRole("button", { name: "Войти" }).click();
  await expect(page).toHaveURL(/\/$/);
});

test("@strict home page must show real document list (not placeholder text)", async ({
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
  await expect(page.locator("main")).not.toContainText(
    "Список доступных документов будет отображаться здесь."
  );
  await expect(page.locator("main")).not.toContainText("будет отображаться здесь");

  await context.close();
});

test("@strict document create page must contain working form fields", async ({
  browser,
  request,
  baseURL,
}) => {
  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  await page.goto("/documents/new");
  await expect(page.locator("#createDocForm")).toBeVisible();
  await expect(page.locator("#docTitle")).toBeVisible();
  await expect(page.locator("#docSpaceWrapper")).toBeVisible();

  await context.close();
});

test("@strict document view page must render content blocks, not placeholder stub", async ({
  browser,
  request,
  baseURL,
}) => {
  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  await page.goto("/documents/1");
  await expect(page.locator("main")).not.toContainText(
    "Содержимое документа будет отображаться здесь."
  );

  await context.close();
});