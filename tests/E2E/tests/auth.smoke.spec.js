const { test, expect } = require("@playwright/test");
const { createAuthenticatedPage } = require("./helpers/session");

test("A1: login page is available", async ({ page }) => {
  await page.goto("/login");
  await expect(page.locator("h1")).toContainText("Вход в Базу знаний");
  await expect(page.locator("input[name='username']")).toBeVisible();
  await expect(page.locator("input[name='password']")).toBeVisible();
});

test("A2: anonymous user sees login on protected route", async ({ page }) => {
  await page.goto("/");
  await expect(page.locator("h1")).toContainText("Вход в Базу знаний");
});

test("A3+A4: API login allows access to admin pages", async ({
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
  await expect(page.locator("h1")).toContainText("Пользователи");

  await page.goto("/admin/spaces");
  await expect(page.locator("h1")).toContainText("Пространства");

  await page.goto("/admin/settings");
  await expect(page.locator("h1")).toContainText("Настройки");

  await context.close();
});

