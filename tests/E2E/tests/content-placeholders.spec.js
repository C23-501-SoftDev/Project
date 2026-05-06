const { test, expect } = require("@playwright/test");
const { createAuthenticatedPage } = require("./helpers/session");

const placeholderRoutes = [
  ["/", "Документы"],
  ["/documents/new", "Создание документа"],
  ["/documents/1", "Просмотр документа"],
  ["/documents/1/edit", "Редактирование документа"],
  ["/documents/1/history", "История версий"],
  ["/search?q=test", "Результаты поиска"],
  ["/spaces/1", "Пространство"],
];

test("D1: content routes open with current-stage placeholders", async ({
  browser,
  request,
  baseURL,
}) => {
  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  for (const [route, expectedText] of placeholderRoutes) {
    await page.goto(route);
    await expect(page.locator("main")).toContainText(expectedText);
  }

  await context.close();
});

