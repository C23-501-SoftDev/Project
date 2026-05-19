const { test, expect, request: playwrightRequest } = require("@playwright/test");

async function loginAsAdmin(baseURL) {
  const api = await playwrightRequest.newContext({ baseURL });
  const response = await api.post(`${baseURL}/api/auth/login`, {
    data: { login: "admin", password: "admin123" },
  });
  expect(response.ok()).toBeTruthy();
  return api;
}

test("@userfull D1: create user with invalid email is rejected", async ({
  baseURL,
}) => {
  const api = await loginAsAdmin(baseURL);
  const suffix = Date.now();
  const response = await api.post(`${baseURL}/api/admin/users`, {
    data: {
      login: `badmail_${suffix}`,
      email: "not-an-email",
      password: "TempPass123!",
      role: "READER",
    },
  });
  expect(response.ok()).toBeFalsy();
  expect([400, 422]).toContain(response.status());
  await api.dispose();
});

test("@userfull D2: duplicate user login is rejected with conflict", async ({
  baseURL,
}) => {
  const api = await loginAsAdmin(baseURL);
  const suffix = Date.now();
  const login = `dup_login_${suffix}`;

  const first = await api.post(`${baseURL}/api/admin/users`, {
    data: {
      login,
      email: `${login}@local.test`,
      password: "TempPass123!",
      role: "READER",
    },
  });
  expect(first.ok()).toBeTruthy();

  const second = await api.post(`${baseURL}/api/admin/users`, {
    data: {
      login,
      email: `another_${login}@local.test`,
      password: "TempPass123!",
      role: "READER",
    },
  });
  expect(second.ok()).toBeFalsy();
  expect(second.status()).toBe(409);
  await api.dispose();
});

test("@userfull D3: duplicate user email is rejected with conflict", async ({
  baseURL,
}) => {
  const api = await loginAsAdmin(baseURL);
  const suffix = Date.now();
  const email = `dup_email_${suffix}@local.test`;

  const first = await api.post(`${baseURL}/api/admin/users`, {
    data: {
      login: `dup_a_${suffix}`,
      email,
      password: "TempPass123!",
      role: "READER",
    },
  });
  expect(first.ok()).toBeTruthy();

  const second = await api.post(`${baseURL}/api/admin/users`, {
    data: {
      login: `dup_b_${suffix}`,
      email,
      password: "TempPass123!",
      role: "EDITOR",
    },
  });
  expect(second.ok()).toBeFalsy();
  expect(second.status()).toBe(409);
  await api.dispose();
});

test("@userfull D4: users pagination with large page returns empty or valid page", async ({
  baseURL,
}) => {
  const api = await loginAsAdmin(baseURL);
  const response = await api.get(
    `${baseURL}/api/admin/users?page=9999&size=20&sortBy=createdAt&sortDir=desc`
  );
  expect(response.ok()).toBeTruthy();
  const payload = await response.json();
  expect(payload).toHaveProperty("content");
  expect(Array.isArray(payload.content)).toBeTruthy();
  await api.dispose();
});

test("@userfull D5: users sort by unsupported field falls back to default sort", async ({
  baseURL,
}) => {
  const api = await loginAsAdmin(baseURL);
  const response = await api.get(
    `${baseURL}/api/admin/users?page=0&size=20&sortBy=not_existing_field&sortDir=desc`
  );
  expect(response.ok()).toBeTruthy();
  const payload = await response.json();
  expect(payload).toHaveProperty("content");
  expect(Array.isArray(payload.content)).toBeTruthy();
  await api.dispose();
});

test("@userfull D6: rapid duplicate create requests - exactly one succeeds", async ({
  baseURL,
}) => {
  const api = await loginAsAdmin(baseURL);
  const suffix = Date.now();
  const login = `race_user_${suffix}`;
  const payload = {
    login,
    email: `${login}@local.test`,
    password: "TempPass123!",
    role: "READER",
  };

  const [r1, r2] = await Promise.all([
    api.post(`${baseURL}/api/admin/users`, { data: payload }),
    api.post(`${baseURL}/api/admin/users`, { data: payload }),
  ]);

  const statuses = [r1.status(), r2.status()].sort();
  expect(statuses.filter((s) => s === 201).length).toBe(1);
  expect([409, 500]).toContain(statuses.find((s) => s !== 201));
  await api.dispose();
});

test("@userfull D7: create space with empty name is rejected", async ({
  baseURL,
}) => {
  const api = await loginAsAdmin(baseURL);
  const response = await api.post(`${baseURL}/api/admin/spaces`, {
    data: {
      name: "",
      description: "invalid space",
    },
  });
  expect(response.ok()).toBeFalsy();
  expect([400, 422]).toContain(response.status());
  await api.dispose();
});

test("@userfull D8: assign invalid permission value is rejected", async ({
  baseURL,
}) => {
  const api = await loginAsAdmin(baseURL);

  const createSpace = await api.post(`${baseURL}/api/admin/spaces`, {
    data: { name: `perm_space_${Date.now()}`, description: "tmp" },
  });
  expect(createSpace.ok()).toBeTruthy();
  const space = await createSpace.json();

  const invalidPermission = await api.post(
    `${baseURL}/api/admin/spaces/${space.id}/permissions`,
    {
      data: { userId: 1, permissionType: "ADMIN" },
    }
  );
  expect(invalidPermission.ok()).toBeFalsy();
  expect([400, 422, 500]).toContain(invalidPermission.status());
  await api.dispose();
});

test("@userfull D9: non-admin cannot call admin users API", async ({
  baseURL,
}) => {
  const adminApi = await loginAsAdmin(baseURL);
  const suffix = Date.now();
  const login = `nonadmin_${suffix}`;
  const password = "EditorPass123!";

  const create = await adminApi.post(`${baseURL}/api/admin/users`, {
    data: {
      login,
      email: `${login}@local.test`,
      password,
      role: "EDITOR",
    },
  });
  expect(create.ok()).toBeTruthy();
  await adminApi.dispose();

  const editorApi = await playwrightRequest.newContext({ baseURL });
  const loginResponse = await editorApi.post(`${baseURL}/api/auth/login`, {
    data: { login, password },
  });
  expect(loginResponse.ok()).toBeTruthy();

  const forbidden = await editorApi.get(`${baseURL}/api/admin/users?page=0&size=20`);
  expect(forbidden.ok()).toBeFalsy();
  expect([401, 403]).toContain(forbidden.status());
  await editorApi.dispose();
});

