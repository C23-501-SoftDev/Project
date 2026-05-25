const { test, expect } = require("@playwright/test");
const { createAuthenticatedPage } = require("./helpers/session");

const implementedRoutes = [
  { route: "/", texts: ["Документы"] },
  { route: "/documents/new", texts: ["Создать документ", "Название"] },
  { route: "/documents/1", texts: ["document-view-page"] },
  { route: "/documents/1/edit", texts: ["Сохранить", "Название"] },
];

const wipPlaceholderRoutes = [
  {
    route: "/documents/1/history",
    text: "История изменений будет отображаться здесь.",
  },
  { route: "/search?q=test", text: "Результаты поиска будут отображены здесь." },
];

test("D1: implemented content routes open with real UI", async ({
  browser,
  request,
  baseURL,
}) => {
  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  for (const { route, texts } of implementedRoutes) {
    await page.goto(route);
    const main = page.locator("main");
    for (const text of texts) {
      if (text.startsWith(".")) {
        await expect(main.locator(text)).toBeVisible();
      } else if (text.includes("-page")) {
        await expect(page.locator(`.${text}`)).toBeVisible();
      } else {
        await expect(main).toContainText(text);
      }
    }
  }

  await context.close();
});

test("D2: WIP content routes still show stage placeholders", async ({
  browser,
  request,
  baseURL,
}) => {
  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  for (const { route, text } of wipPlaceholderRoutes) {
    await page.goto(route);
    await expect(page.locator("main")).toContainText(text);
  }

  await context.close();
});
