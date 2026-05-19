const { test, expect, request: playwrightRequest } = require("@playwright/test");

async function apiLogin(api, baseURL, login, password) {
  const response = await api.post(`${baseURL}/api/auth/login`, {
    data: { login, password },
  });
  return response;
}

async function newBrowserPageFromApiState(browser, api) {
  const storageState = await api.storageState();
  const context = await browser.newContext({ storageState });
  const page = await context.newPage();
  return { context, page };
}

test("@userfull U1: login form has required fields", async ({ page }) => {
  await page.goto("/login");
  await expect(page.locator("#username")).toBeVisible();
  await expect(page.locator("#password")).toBeVisible();
  await expect(page.getByRole("button", { name: "Войти" })).toBeVisible();
});

test("@userfull U2: invalid API login is rejected", async ({ baseURL }) => {
  const api = await playwrightRequest.newContext({ baseURL });
  const response = await apiLogin(api, baseURL, "admin", "wrong-password");
  expect(response.ok()).toBeFalsy();
  expect(response.status()).toBe(401);
  await api.dispose();
});

test("@userfull U3: valid API login and /api/auth/me returns current user", async ({
  baseURL,
}) => {
  const api = await playwrightRequest.newContext({ baseURL });
  const loginResponse = await apiLogin(api, baseURL, "admin", "admin123");
  expect(loginResponse.ok()).toBeTruthy();

  const meResponse = await api.get(`${baseURL}/api/auth/me`);
  expect(meResponse.ok()).toBeTruthy();
  const me = await meResponse.json();
  expect(me.login).toBe("admin");
  expect(me.role).toBe("EDITOR");
  expect(me.isAdmin).toBe(true);
  await api.dispose();
});

test("@userfull U4: anonymous API call to user spaces is unauthorized", async ({
  request,
  baseURL,
}) => {
  const response = await request.get(`${baseURL}/api/user/spaces`);
  expect(response.ok()).toBeFalsy();
  expect([401, 403]).toContain(response.status());
});

test("@userfull U5: admin can open all main user SSR routes", async ({
  browser,
  baseURL,
}) => {
  const api = await playwrightRequest.newContext({ baseURL });
  await apiLogin(api, baseURL, "admin", "admin123");
  const { context, page } = await newBrowserPageFromApiState(browser, api);

  const routes = [
    "/",
    "/documents/new",
    "/documents/1",
    "/documents/1/edit",
    "/documents/1/history",
    "/search?q=test",
    "/spaces/1",
  ];

  for (const route of routes) {
    const response = await page.goto(route);
    expect(response.status()).toBe(200);
  }

  await context.close();
  await api.dispose();
});

test("@userfull U6: UI logout invalidates session", async ({ page }) => {
  await page.goto("/login");
  await page.locator("#username").fill("admin");
  await page.locator("#password").fill("admin123");
  await page.getByRole("button", { name: "Войти" }).click();
  await expect(page).toHaveURL(/\/$/);

  await page.getByRole("button", { name: "Выйти" }).click();
  await expect(page).toHaveURL(/\/login/);
});

test("@userfull U7: create EDITOR user and verify ADMIN pages are restricted", async ({
  browser,
  baseURL,
}) => {
  const adminApi = await playwrightRequest.newContext({ baseURL });
  await apiLogin(adminApi, baseURL, "admin", "admin123");

  const suffix = Date.now();
  const editorLogin = `editor_e2e_${suffix}`;
  const editorPassword = "EditorPass123!";
  const createResponse = await adminApi.post(`${baseURL}/api/admin/users`, {
    data: {
      login: editorLogin,
      email: `${editorLogin}@local.test`,
      password: editorPassword,
      role: "EDITOR",
      isAdmin: false,
    },
  });
  expect(createResponse.ok()).toBeTruthy();

  const editorApi = await playwrightRequest.newContext({ baseURL });
  await apiLogin(editorApi, baseURL, editorLogin, editorPassword);
  const storageState = await editorApi.storageState();
  const context = await browser.newContext({ storageState });
  const page = await context.newPage();

  await page.goto("/admin/users");
  const bodyText = await page.locator("body").innerText();
  expect(bodyText.includes("Пользователи")).toBeFalsy();

  await context.close();
  await editorApi.dispose();
  await adminApi.dispose();
});

test("@userfull U8: create READER user and verify ADMIN pages are restricted", async ({
  browser,
  baseURL,
}) => {
  const adminApi = await playwrightRequest.newContext({ baseURL });
  await apiLogin(adminApi, baseURL, "admin", "admin123");

  const suffix = Date.now();
  const readerLogin = `reader_e2e_${suffix}`;
  const readerPassword = "ReaderPass123!";
  const createResponse = await adminApi.post(`${baseURL}/api/admin/users`, {
    data: {
      login: readerLogin,
      email: `${readerLogin}@local.test`,
      password: readerPassword,
      role: "READER",
      isAdmin: false,
    },
  });
  expect(createResponse.ok()).toBeTruthy();

  const readerApi = await playwrightRequest.newContext({ baseURL });
  await apiLogin(readerApi, baseURL, readerLogin, readerPassword);
  const storageState = await readerApi.storageState();
  const context = await browser.newContext({ storageState });
  const page = await context.newPage();

  await page.goto("/admin/spaces");
  const bodyText = await page.locator("body").innerText();
  expect(bodyText.includes("Пространства")).toBeFalsy();

  await context.close();
  await readerApi.dispose();
  await adminApi.dispose();
});

test("@userfull U9: user spaces API returns JSON array for authenticated user", async ({
  baseURL,
}) => {
  const api = await playwrightRequest.newContext({ baseURL });
  await apiLogin(api, baseURL, "admin", "admin123");
  const response = await api.get(`${baseURL}/api/user/spaces`);
  expect(response.ok()).toBeTruthy();
  const payload = await response.json();
  expect(Array.isArray(payload)).toBeTruthy();
  await api.dispose();
});

test("@userfull U10: permissions API returns access flags for a space", async ({
  baseURL,
}) => {
  const api = await playwrightRequest.newContext({ baseURL });
  await apiLogin(api, baseURL, "admin", "admin123");
  const response = await api.get(`${baseURL}/api/user/permissions?spaceId=1`);
  expect(response.ok()).toBeTruthy();
  const payload = await response.json();
  expect(payload).toHaveProperty("canRead");
  expect(payload).toHaveProperty("canEdit");
  expect(payload).toHaveProperty("canCreate");
  await api.dispose();
});

test("@userfull U11: home page must show implemented list, not placeholder", async ({
  browser,
  baseURL,
}) => {
  const api = await playwrightRequest.newContext({ baseURL });
  await apiLogin(api, baseURL, "admin", "admin123");
  const { context, page } = await newBrowserPageFromApiState(browser, api);
  await page.goto("/");
  await expect(page.locator("main")).not.toContainText(
    "Список доступных документов будет отображаться здесь."
  );
  await context.close();
  await api.dispose();
});

test("@userfull U12: search results page loads with query (WIP: results list)", async ({
  browser,
  baseURL,
}) => {
  const api = await playwrightRequest.newContext({ baseURL });
  await apiLogin(api, baseURL, "admin", "admin123");
  const { context, page } = await newBrowserPageFromApiState(browser, api);
  await page.goto("/search?q=test");
  await expect(page.locator("main")).toContainText("Результаты поиска: test");
  await expect(page.locator("main")).toContainText(
    "Результаты поиска будут отображены здесь."
  );
  await context.close();
  await api.dispose();
});

test("@userfull U13: document view page must render document body, not placeholder", async ({
  browser,
  baseURL,
}) => {
  const api = await playwrightRequest.newContext({ baseURL });
  await apiLogin(api, baseURL, "admin", "admin123");
  const { context, page } = await newBrowserPageFromApiState(browser, api);
  await page.goto("/documents/1");
  await expect(page.locator("main")).not.toContainText(
    "Содержимое документа будет отображаться здесь."
  );
  await context.close();
  await api.dispose();
});

test("@userfull U14: document history page loads (WIP: version list)", async ({
  browser,
  baseURL,
}) => {
  const api = await playwrightRequest.newContext({ baseURL });
  await apiLogin(api, baseURL, "admin", "admin123");
  const { context, page } = await newBrowserPageFromApiState(browser, api);
  await page.goto("/documents/1/history");
  await expect(page.locator("main")).toContainText("История версий документа");
  await expect(page.locator("main")).toContainText(
    "История изменений будет отображаться здесь."
  );
  await context.close();
  await api.dispose();
});

test("@userfull U15: space page loads (WIP: documents tree/list)", async ({
  browser,
  baseURL,
}) => {
  const api = await playwrightRequest.newContext({ baseURL });
  await apiLogin(api, baseURL, "admin", "admin123");
  const { context, page } = await newBrowserPageFromApiState(browser, api);
  await page.goto("/spaces/1");
  await expect(page.locator("main")).toContainText("Пространство");
  await expect(page.locator("main")).toContainText(
    "Документы пространства будут отображаться здесь."
  );
  await context.close();
  await api.dispose();
});

test("@userfull U16: create GUEST user via API and read role from /api/auth/me", async ({
  baseURL,
}) => {
  const adminApi = await playwrightRequest.newContext({ baseURL });
  await apiLogin(adminApi, baseURL, "admin", "admin123");

  const suffix = Date.now();
  const guestLogin = `guest_e2e_${suffix}`;
  const guestPassword = "GuestPass123!";
  const createResponse = await adminApi.post(`${baseURL}/api/admin/users`, {
    data: {
      login: guestLogin,
      email: `${guestLogin}@local.test`,
      password: guestPassword,
      role: "GUEST",
      isAdmin: false,
    },
  });
  expect(createResponse.ok()).toBeTruthy();

  const guestApi = await playwrightRequest.newContext({ baseURL });
  await apiLogin(guestApi, baseURL, guestLogin, guestPassword);
  const meResponse = await guestApi.get(`${baseURL}/api/auth/me`);
  expect(meResponse.ok()).toBeTruthy();
  const me = await meResponse.json();
  expect(me.role).toBe("GUEST");
  expect(me.isAdmin).toBe(false);

  await guestApi.dispose();
  await adminApi.dispose();
});

test("@userfull U17: EDITOR with isAdmin=false cannot open admin users API", async ({
  baseURL,
}) => {
  const adminApi = await playwrightRequest.newContext({ baseURL });
  await apiLogin(adminApi, baseURL, "admin", "admin123");

  const suffix = Date.now();
  const login = `editor_no_admin_${suffix}`;
  const password = "EditorPass123!";
  const createResponse = await adminApi.post(`${baseURL}/api/admin/users`, {
    data: {
      login,
      email: `${login}@local.test`,
      password,
      role: "EDITOR",
      isAdmin: false,
    },
  });
  expect(createResponse.ok()).toBeTruthy();
  await adminApi.dispose();

  const editorApi = await playwrightRequest.newContext({ baseURL });
  await apiLogin(editorApi, baseURL, login, password);
  const forbidden = await editorApi.get(`${baseURL}/api/admin/users?page=0&size=20`);
  expect(forbidden.ok()).toBeFalsy();
  expect([401, 403]).toContain(forbidden.status());
  await editorApi.dispose();
});

