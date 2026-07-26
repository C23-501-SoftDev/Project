const { request: playwrightRequest } = require("@playwright/test");

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

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Retries transient 5xx responses (Git lock/contention during parallel seed). */
async function postWithRetry(api, url, options, { attempts = 6, delayMs = 400 } = {}) {
  let response;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    response = await api.post(url, options);
    if (response.status() < 500) {
      return response;
    }
    if (attempt < attempts) {
      await sleep(delayMs * attempt);
    }
  }
  return response;
}

async function createSpace(adminApi, baseURL, name, options = {}) {
  const response = await postWithRetry(adminApi, `${baseURL}/api/admin/spaces`, {
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
  return postWithRetry(api, `${baseURL}/api/documents`, { data });
}

async function getFirstSpaceId(api, baseURL) {
  const response = await api.get(`${baseURL}/api/spaces`);
  if (!response.ok()) {
    throw new Error(`get spaces failed: ${response.status()}`);
  }
  const spaces = await response.json();
  if (!spaces.length) {
    throw new Error("No spaces available for tests");
  }
  return spaces[0].id;
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
  getFirstSpaceId,
  uniqueSuffix,
};
