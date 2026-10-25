const { request: playwrightRequest } = require("./test");

async function apiLogin(api, baseURL, login, password) {
  const response = await api.post(`${baseURL}/api/auth/login`, {
    data: { login, password },
  });
  if (!response.ok()) {
    throw new Error(`API login failed: ${response.status()}`);
  }
  return api;
}

async function createAdminApi(baseURL) {
  const api = await playwrightRequest.newContext({ baseURL });
  await apiLogin(api, baseURL, "admin", "admin123");
  return api;
}

async function createUserApi(baseURL, login, password) {
  const api = await playwrightRequest.newContext({ baseURL });
  await apiLogin(api, baseURL, login, password);
  return api;
}

async function newBrowserPageFromApi(browser, api) {
  const storageState = await api.storageState();
  const context = await browser.newContext({ storageState });
  const page = await context.newPage();
  return { context, page };
}

async function createSpace(adminApi, baseURL, name, options = {}) {
  const response = await adminApi.post(`${baseURL}/api/admin/spaces`, {
    data: {
      name,
      description: options.description || "E2E documents test space",
      ...(options.ownerId ? { ownerId: options.ownerId } : {}),
    },
  });
  if (!response.ok()) {
    const body = await response.text().catch(() => "");
    throw new Error(`createSpace failed: ${response.status()} ${body}`);
  }
  return response.json();
}

async function grantSpacePermission(adminApi, baseURL, spaceId, userId, permissionType) {
  const response = await adminApi.post(
    `${baseURL}/api/admin/spaces/${spaceId}/permissions`,
    { data: { userId, permissionType } }
  );
  if (!response.ok()) {
    throw new Error(`grantPermission failed: ${response.status()}`);
  }
  return response.json();
}

async function createUser(adminApi, baseURL, { login, email, password, role, isAdmin = false }) {
  const response = await adminApi.post(`${baseURL}/api/admin/users`, {
    data: { login, email, password, role, isAdmin },
  });
  if (!response.ok()) {
    throw new Error(`createUser failed: ${response.status()}`);
  }
  return response.json();
}

async function createDocument(api, baseURL, payload = {}) {
  const { title, spaceId, content = "", parentId, templateId } = payload;
  const data = { title, spaceId, content };
  if (parentId !== undefined) {
    data.parentId = parentId;
  }
  if (templateId !== undefined) {
    data.templateId = templateId;
  }
  return api.post(`${baseURL}/api/documents`, { data });
}

async function createTestSpaceId(api, baseURL) {
  return (await createSpace(api, baseURL, `E2E isolated ${uniqueSuffix()}`)).id;
}

async function createContentFixture(api, baseURL) {
  const space = await createSpace(api, baseURL, `E2E content ${uniqueSuffix()}`);
  const title = `Fixture ${uniqueSuffix()}`;
  const response = await createDocument(api, baseURL, {
    title, spaceId: space.id, content: "# Fixture heading\n\nOriginal content",
  });
  if (response.status() !== 201) throw new Error(`Create fixture: ${response.status()}`);
  const document = await response.json();
  const published = await api.post(`${baseURL}/api/documents/${document.id}/publish`);
  if (!published.ok()) throw new Error(`Publish fixture: ${published.status()}`);
  for (const content of ["First saved version", "Second saved version"]) {
    const saved = await api.put(`${baseURL}/api/documents/${document.id}`, { data: { content } });
    if (!saved.ok()) throw new Error(`Save fixture: ${saved.status()}`);
  }
  return { space, document };
}

function uniqueSuffix() {
  return `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

module.exports = {
  apiLogin,
  createAdminApi,
  createUserApi,
  newBrowserPageFromApi,
  createSpace,
  grantSpacePermission,
  createUser,
  createDocument,
  createTestSpaceId,
  createContentFixture,
  uniqueSuffix,
};
