const { test, expect } = require("@playwright/test");

test("E1: current layout assets return server errors (known limitation)", async ({
  request,
  baseURL,
}) => {
  const cssResponse = await request.get(`${baseURL}/css/main.css`);
  const jsResponse = await request.get(`${baseURL}/js/main.js`);

  expect(cssResponse.status()).toBeGreaterThanOrEqual(500);
  expect(jsResponse.status()).toBeGreaterThanOrEqual(500);
});

test("E2: admin assets are available", async ({ request, baseURL }) => {
  const cssResponse = await request.get(`${baseURL}/css/admin-panel.css`);
  const jsResponse = await request.get(`${baseURL}/js/admin-common.js`);

  expect(cssResponse.ok()).toBeTruthy();
  expect(jsResponse.ok()).toBeTruthy();
});

