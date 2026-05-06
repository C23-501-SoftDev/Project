async function createAuthenticatedPage({ browser, request, baseURL }) {
  const response = await request.post(`${baseURL}/api/auth/login`, {
    data: { login: "admin", password: "admin123" },
  });

  if (!response.ok()) {
    throw new Error(`API login failed with status ${response.status()}`);
  }

  const storageState = await request.storageState();

  const context = await browser.newContext({ storageState });
  const page = await context.newPage();
  return { context, page };
}

module.exports = { createAuthenticatedPage };

