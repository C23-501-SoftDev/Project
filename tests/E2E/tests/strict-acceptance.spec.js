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
  await expect(page.locator("#docSpace")).toBeVisible();

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

test.fixme(
  "@strict admin settings page must be implemented (no WIP message)",
  async ({ browser, request, baseURL }) => {
    const { context, page } = await createAuthenticatedPage({
      browser,
      request,
      baseURL,
    });

    await page.goto("/admin/settings");
    await expect(page.locator("body")).not.toContainText("Функция в разработке");
    await expect(page.locator("body")).not.toContainText(
      "Страница настроек будет доступна в будущем"
    );

    await context.close();
  }
);

test.fixme("@strict search results must not be a placeholder", async ({
  browser,
  request,
  baseURL,
}) => {
  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  await page.goto("/search?q=test");
  await expect(page.locator("main")).not.toContainText(
    "Результаты поиска будут отображены здесь."
  );

  await context.close();
});

test.fixme("@strict document history must show versions, not placeholder", async ({
  browser,
  request,
  baseURL,
}) => {
  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  await page.goto("/documents/1/history");
  await expect(page.locator("main")).not.toContainText(
    "История изменений будет отображаться здесь."
  );

  await context.close();
});

test.fixme("@strict space page must show documents list, not placeholder", async ({
  browser,
  request,
  baseURL,
}) => {
  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  await page.goto("/spaces/1");
  await expect(page.locator("main")).not.toContainText(
    "Документы пространства будут отображаться здесь."
  );

  await context.close();
});
