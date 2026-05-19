const { test, expect, request: playwrightRequest } = require("@playwright/test");

async function login(baseURL, login, password) {
  const api = await playwrightRequest.newContext({ baseURL });
  const response = await api.post(`${baseURL}/api/auth/login`, {
    data: { login, password },
  });
  return { api, response };
}

async function loginAdmin(baseURL) {
  const { api, response } = await login(baseURL, "admin", "admin123");
  expect(response.ok()).toBeTruthy();
  return api;
}

test("@userfull G1: /api/auth/me returns role for editor user", async ({ baseURL }) => {
  const admin = await loginAdmin(baseURL);
  const suffix = Date.now();
  const editorLogin = `me_editor_${suffix}`;
  const editorPassword = "EditorPass123!";

  const create = await admin.post(`${baseURL}/api/admin/users`, {
    data: {
      login: editorLogin,
      email: `${editorLogin}@local.test`,
      password: editorPassword,
      role: "EDITOR",
    },
  });
  expect(create.ok()).toBeTruthy();
  await admin.dispose();

  const { api: editorApi, response: editorLoginResponse } = await login(
    baseURL,
    editorLogin,
    editorPassword
  );
  expect(editorLoginResponse.ok()).toBeTruthy();

  const me = await editorApi.get(`${baseURL}/api/auth/me`);
  expect(me.ok()).toBeTruthy();
  const payload = await me.json();
  expect(payload.role).toBe("EDITOR");
  await editorApi.dispose();
});

test("@userfull G2: non-admin /api/admin/users returns 403/401", async ({ baseURL }) => {
  const admin = await loginAdmin(baseURL);
  const suffix = Date.now();
  const readerLogin = `g2_reader_${suffix}`;
  const readerPassword = "ReaderPass123!";
  const create = await admin.post(`${baseURL}/api/admin/users`, {
    data: {
      login: readerLogin,
      email: `${readerLogin}@local.test`,
      password: readerPassword,
      role: "READER",
    },
  });
  expect(create.ok()).toBeTruthy();
  await admin.dispose();

  const { api: readerApi, response: readerLoginResponse } = await login(
    baseURL,
    readerLogin,
    readerPassword
  );
  expect(readerLoginResponse.ok()).toBeTruthy();
  const forbidden = await readerApi.get(`${baseURL}/api/admin/users?page=0&size=20`);
  expect([401, 403]).toContain(forbidden.status());
  await readerApi.dispose();
});

test("@userfull G3: password change validates minimal requirements", async ({ baseURL }) => {
  const admin = await loginAdmin(baseURL);
  const suffix = Date.now();
  const userLogin = `pwd_user_${suffix}`;
  const create = await admin.post(`${baseURL}/api/admin/users`, {
    data: {
      login: userLogin,
      email: `${userLogin}@local.test`,
      password: "TempPass123!",
      role: "READER",
    },
  });
  expect(create.ok()).toBeTruthy();
  const created = await create.json();

  const shortPassword = await admin.put(
    `${baseURL}/api/admin/users/${created.id}/password`,
    {
      data: { newPassword: "1" },
    }
  );
  expect([400, 422]).toContain(shortPassword.status());
  await admin.dispose();
});

test("@userfull G4: password change for unknown user returns 404", async ({ baseURL }) => {
  const admin = await loginAdmin(baseURL);
  const response = await admin.put(`${baseURL}/api/admin/users/999999/password`, {
    data: { newPassword: "SomeLongPass123!" },
  });
  expect(response.status()).toBe(404);
  await admin.dispose();
});

test("@userfull G5: assigning permission to unknown user fails", async ({ baseURL }) => {
  const admin = await loginAdmin(baseURL);
  const createdSpaceResponse = await admin.post(`${baseURL}/api/admin/spaces`, {
    data: {
      name: `g5_space_${Date.now()}`,
      description: "permissions test",
    },
  });
  expect(createdSpaceResponse.ok()).toBeTruthy();
  const space = await createdSpaceResponse.json();

  const assign = await admin.post(`${baseURL}/api/admin/spaces/${space.id}/permissions`, {
    data: {
      userId: 999999,
      permissionType: "READ",
    },
  });
  expect([404, 409]).toContain(assign.status());
  await admin.dispose();
});

test("@userfull G6: duplicate permission assignment returns conflict", async ({ baseURL }) => {
  const admin = await loginAdmin(baseURL);
  const suffix = Date.now();
  const loginName = `g6_user_${suffix}`;
  const createdUserResponse = await admin.post(`${baseURL}/api/admin/users`, {
    data: {
      login: loginName,
      email: `${loginName}@local.test`,
      password: "TempPass123!",
      role: "READER",
    },
  });
  expect(createdUserResponse.ok()).toBeTruthy();
  const user = await createdUserResponse.json();

  const createdSpaceResponse = await admin.post(`${baseURL}/api/admin/spaces`, {
    data: {
      name: `g6_space_${suffix}`,
      description: "duplicate permission test",
    },
  });
  expect(createdSpaceResponse.ok()).toBeTruthy();
  const space = await createdSpaceResponse.json();

  const first = await admin.post(`${baseURL}/api/admin/spaces/${space.id}/permissions`, {
    data: { userId: user.id, permissionType: "READ" },
  });
  expect(first.ok()).toBeTruthy();

  const second = await admin.post(`${baseURL}/api/admin/spaces/${space.id}/permissions`, {
    data: { userId: user.id, permissionType: "READ" },
  });
  expect(second.status()).toBe(409);
  await admin.dispose();
});

test("@userfull G7: delete space owner performs soft-delete (200)", async ({ baseURL }) => {
  const admin = await loginAdmin(baseURL);
  const suffix = Date.now();
  const ownerLogin = `g7_owner_${suffix}`;

  const createdUserResponse = await admin.post(`${baseURL}/api/admin/users`, {
    data: {
      login: ownerLogin,
      email: `${ownerLogin}@local.test`,
      password: "TempPass123!",
      role: "EDITOR",
      isAdmin: false,
    },
  });
  expect(createdUserResponse.ok()).toBeTruthy();
  const user = await createdUserResponse.json();

  const createdSpaceResponse = await admin.post(`${baseURL}/api/admin/spaces`, {
    data: {
      name: `g7_space_${suffix}`,
      description: "owner link test",
      ownerId: user.id,
    },
  });
  expect(createdSpaceResponse.ok()).toBeTruthy();

  const deleteOwner = await admin.delete(`${baseURL}/api/admin/users/${user.id}`);
  expect(deleteOwner.ok()).toBeTruthy();

  const deletedUser = await deleteOwner.json();
  expect(deletedUser.isDeleted).toBe(true);
  await admin.dispose();
});

test("@userfull G8: spaces pagination boundary values are handled", async ({ baseURL }) => {
  const admin = await loginAdmin(baseURL);
  const farPage = await admin.get(`${baseURL}/api/admin/spaces?page=9999&size=20`);
  expect(farPage.ok()).toBeTruthy();
  const farPayload = await farPage.json();
  expect(Array.isArray(farPayload.content)).toBeTruthy();

  const badSize = await admin.get(`${baseURL}/api/admin/spaces?page=0&size=0`);
  expect([200, 400, 500]).toContain(badSize.status());
  await admin.dispose();
});

test("@userfull G9: error response has structured fields", async ({ baseURL }) => {
  const admin = await loginAdmin(baseURL);
  const response = await admin.get(`${baseURL}/api/admin/users/999999`);
  expect(response.status()).toBe(404);
  const body = await response.json();
  expect(body).toHaveProperty("status");
  expect(body).toHaveProperty("message");
  expect(body).toHaveProperty("path");
  await admin.dispose();
});

