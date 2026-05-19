const { test, expect } = require("@playwright/test");

test("E1: main layout assets are available", async ({ request, baseURL }) => {
  const cssResponse = await request.get(`${baseURL}/css/main.css`);
  const jsResponse = await request.get(`${baseURL}/js/main.js`);

  expect(cssResponse.ok()).toBeTruthy();
  expect(jsResponse.ok()).toBeTruthy();
});

test("E2: admin assets are available", async ({ request, baseURL }) => {
  const cssResponse = await request.get(`${baseURL}/css/admin-panel.css`);
  const jsResponse = await request.get(`${baseURL}/js/admin-common.js`);

  expect(cssResponse.ok()).toBeTruthy();
  expect(jsResponse.ok()).toBeTruthy();
});
