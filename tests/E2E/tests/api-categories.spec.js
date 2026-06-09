const { test, expect, request: playwrightRequest } = require("@playwright/test");

function uniqueSuffix() {
  return `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

async function login(baseURL, loginName, password) {
  const api = await playwrightRequest.newContext({ baseURL });
  const response = await api.post(`${baseURL}/api/auth/login`, {
    data: { login: loginName, password },
  });
  expect(response.ok()).toBeTruthy();
  return api;
}

async function loginAdmin(baseURL) {
  return login(baseURL, "admin", "admin123");
}

async function createUser(adminApi, baseURL, { loginName, email, password, role }) {
  const response = await adminApi.post(`${baseURL}/api/admin/users`, {
    data: { login: loginName, email, password, role, isAdmin: false },
  });
  expect(response.status()).toBe(201);
  return response.json();
}

async function createSpace(adminApi, baseURL, name) {
  const response = await adminApi.post(`${baseURL}/api/admin/spaces`, {
    data: { name, description: "E2E API categories space" },
  });
  expect(response.status()).toBe(201);
  return response.json();
}

async function createDocument(adminApi, baseURL, data) {
  return adminApi.post(`${baseURL}/api/documents`, { data });
}

test.describe("DOCS API", () => {
  test("DOCS-API-01: documents list returns paged payload", async ({ baseURL }) => {
    const adminApi = await loginAdmin(baseURL);
    const response = await adminApi.get(
      `${baseURL}/api/documents?includeDeleted=false&page=0&size=5`
    );

    expect(response.ok()).toBeTruthy();
    const payload = await response.json();
    expect(Array.isArray(payload.content)).toBeTruthy();
    expect(typeof payload.totalElements).toBe("number");
    expect(typeof payload.totalPages).toBe("number");
    expect(payload.number).toBe(0);

    await adminApi.dispose();
  });

  test("DOCS-API-02: cross-space parent assignment is rejected", async ({
    baseURL,
  }) => {
    const adminApi = await loginAdmin(baseURL);
    const spaceA = await createSpace(adminApi, baseURL, `DOCS A ${uniqueSuffix()}`);
    const spaceB = await createSpace(adminApi, baseURL, `DOCS B ${uniqueSuffix()}`);

    const parentRes = await createDocument(adminApi, baseURL, {
      title: `Parent ${uniqueSuffix()}`,
      spaceId: spaceA.id,
      content: "Parent",
    });
    expect(parentRes.status()).toBe(201);
    const parent = await parentRes.json();

    const invalidChild = await createDocument(adminApi, baseURL, {
      title: `Child bad ${uniqueSuffix()}`,
      spaceId: spaceB.id,
      parentId: parent.id,
      content: "Child",
    });

    expect(invalidChild.ok()).toBeFalsy();
    expect([400, 422]).toContain(invalidChild.status());

    await adminApi.dispose();
  });

  test("DOCS-API-03: deleting parent with active child is blocked", async ({
    baseURL,
  }) => {
    const adminApi = await loginAdmin(baseURL);
    const space = await createSpace(adminApi, baseURL, `DOCS tree ${uniqueSuffix()}`);

    const parentRes = await createDocument(adminApi, baseURL, {
      title: `Tree parent ${uniqueSuffix()}`,
      spaceId: space.id,
      content: "Parent",
    });
    expect(parentRes.status()).toBe(201);
    const parent = await parentRes.json();

    const childRes = await createDocument(adminApi, baseURL, {
      title: `Tree child ${uniqueSuffix()}`,
      spaceId: space.id,
      parentId: parent.id,
      content: "Child",
    });
    expect(childRes.status()).toBe(201);

    const deleteParent = await adminApi.delete(`${baseURL}/api/documents/${parent.id}`);
    expect(deleteParent.ok()).toBeFalsy();
    expect([400, 409, 422]).toContain(deleteParent.status());

    await adminApi.dispose();
  });

  test("DOCS-API-04: deleted document can be restored", async ({ baseURL }) => {
    const adminApi = await loginAdmin(baseURL);
    const space = await createSpace(adminApi, baseURL, `DOCS restore ${uniqueSuffix()}`);

    const createRes = await createDocument(adminApi, baseURL, {
      title: `Restore me ${uniqueSuffix()}`,
      spaceId: space.id,
      content: "v1",
    });
    expect(createRes.status()).toBe(201);
    const doc = await createRes.json();

    const deleteRes = await adminApi.delete(`${baseURL}/api/documents/${doc.id}`);
    expect(deleteRes.status()).toBe(204);

    const restoreRes = await adminApi.post(`${baseURL}/api/documents/${doc.id}/restore`);
    expect(restoreRes.status()).toBe(204);

    const getRes = await adminApi.get(`${baseURL}/api/documents/${doc.id}`);
    expect(getRes.ok()).toBeTruthy();
    const restored = await getRes.json();
    expect(restored.status).not.toBe("DELETED");

    await adminApi.dispose();
  });
});

test.describe("USERS API", () => {
  test("USERS-API-01: create, soft-delete, restore user lifecycle", async ({
    baseURL,
  }) => {
    const adminApi = await loginAdmin(baseURL);
    const suffix = uniqueSuffix();
    const createdUser = await createUser(adminApi, baseURL, {
      loginName: `usr_lifecycle_${suffix}`,
      email: `usr_lifecycle_${suffix}@local.test`,
      password: "UserPass123!",
      role: "READER",
    });

    const deleteRes = await adminApi.delete(`${baseURL}/api/admin/users/${createdUser.id}`);
    expect(deleteRes.status()).toBe(200);
    expect((await deleteRes.json()).isDeleted).toBe(true);

    const withDeleted = await adminApi.get(
      `${baseURL}/api/admin/users?page=0&size=200&status=deleted`
    );
    const withDeletedPayload = await withDeleted.json();
    expect(
      withDeletedPayload.content.some((u) => u.id === createdUser.id && u.isDeleted)
    ).toBeTruthy();

    const restoreRes = await adminApi.post(
      `${baseURL}/api/admin/users/${createdUser.id}/restore`
    );
    expect(restoreRes.status()).toBe(200);
    expect((await restoreRes.json()).isDeleted).toBe(false);

    await adminApi.dispose();
  });

  test("USERS-API-02: admin password reset takes effect", async ({ baseURL }) => {
    const adminApi = await loginAdmin(baseURL);
    const suffix = uniqueSuffix();
    const oldPassword = "OldPass123!";
    const newPassword = "NewPass123!";
    const loginName = `usr_pwd_${suffix}`;

    const created = await createUser(adminApi, baseURL, {
      loginName,
      email: `usr_pwd_${suffix}@local.test`,
      password: oldPassword,
      role: "GUEST",
    });

    const changeRes = await adminApi.put(
      `${baseURL}/api/admin/users/${created.id}/password`,
      {
        data: { newPassword },
      }
    );
    expect(changeRes.status()).toBe(204);

    const oldLoginApi = await playwrightRequest.newContext({ baseURL });
    const oldLoginRes = await oldLoginApi.post(`${baseURL}/api/auth/login`, {
      data: { login: loginName, password: oldPassword },
    });
    expect(oldLoginRes.ok()).toBeFalsy();
    await oldLoginApi.dispose();

    const newLoginApi = await playwrightRequest.newContext({ baseURL });
    const newLoginRes = await newLoginApi.post(`${baseURL}/api/auth/login`, {
      data: { login: loginName, password: newPassword },
    });
    expect(newLoginRes.ok()).toBeTruthy();
    await newLoginApi.dispose();

    await adminApi.dispose();
  });

  test("USERS-API-03: non-admin cannot access admin users endpoints", async ({
    baseURL,
  }) => {
    const adminApi = await loginAdmin(baseURL);
    const suffix = uniqueSuffix();
    const user = await createUser(adminApi, baseURL, {
      loginName: `usr_nonadmin_${suffix}`,
      email: `usr_nonadmin_${suffix}@local.test`,
      password: "ReaderPass123!",
      role: "READER",
    });
    await adminApi.dispose();

    const readerApi = await login(baseURL, user.login, "ReaderPass123!");
    const forbidden = await readerApi.get(`${baseURL}/api/admin/users?page=0&size=20`);
    expect([401, 403]).toContain(forbidden.status());
    await readerApi.dispose();
  });
});

test.describe("SPACES API", () => {
  test("SPACES-API-01: create space auto-grants OWNER to creator", async ({
    baseURL,
  }) => {
    const adminApi = await loginAdmin(baseURL);
    const space = await createSpace(adminApi, baseURL, `Space owner ${uniqueSuffix()}`);

    const permsRes = await adminApi.get(`${baseURL}/api/admin/spaces/${space.id}/permissions`);
    expect(permsRes.ok()).toBeTruthy();
    const perms = await permsRes.json();
    expect(perms.some((p) => p.permissionType === "OWNER")).toBeTruthy();

    await adminApi.dispose();
  });

  test("SPACES-API-02: duplicate space name is rejected", async ({ baseURL }) => {
    const adminApi = await loginAdmin(baseURL);
    const name = `Space dup ${uniqueSuffix()}`;
    await createSpace(adminApi, baseURL, name);

    const duplicate = await adminApi.post(`${baseURL}/api/admin/spaces`, {
      data: { name, description: "duplicate" },
    });
    expect(duplicate.status()).toBe(409);

    await adminApi.dispose();
  });

  test("SPACES-API-03: guest access follows granted and revoked permissions", async ({
    baseURL,
  }) => {
    const adminApi = await loginAdmin(baseURL);
    const suffix = uniqueSuffix();
    const space = await createSpace(adminApi, baseURL, `Space perms ${suffix}`);

    const guest = await createUser(adminApi, baseURL, {
      loginName: `guest_space_${suffix}`,
      email: `guest_space_${suffix}@local.test`,
      password: "GuestPass123!",
      role: "GUEST",
    });

    const deniedBeforeGrant = await login(baseURL, guest.login, "GuestPass123!");
    const deniedList = await deniedBeforeGrant.get(
      `${baseURL}/api/documents?spaceId=${space.id}&includeDeleted=false`
    );
    expect([401, 403]).toContain(deniedList.status());
    await deniedBeforeGrant.dispose();

    const grantRes = await adminApi.post(
      `${baseURL}/api/admin/spaces/${space.id}/permissions`,
      { data: { userId: guest.id, permissionType: "READ" } }
    );
    expect(grantRes.status()).toBe(201);
    const grantedPerm = await grantRes.json();

    const grantedApi = await login(baseURL, guest.login, "GuestPass123!");
    const allowedList = await grantedApi.get(
      `${baseURL}/api/documents?spaceId=${space.id}&includeDeleted=false`
    );
    expect(allowedList.ok()).toBeTruthy();
    await grantedApi.dispose();

    const revokeRes = await adminApi.delete(
      `${baseURL}/api/admin/permissions/${grantedPerm.id}`
    );
    expect(revokeRes.status()).toBe(204);

    const deniedAfterRevoke = await login(baseURL, guest.login, "GuestPass123!");
    const deniedAgain = await deniedAfterRevoke.get(
      `${baseURL}/api/documents?spaceId=${space.id}&includeDeleted=false`
    );
    expect([401, 403]).toContain(deniedAgain.status());
    await deniedAfterRevoke.dispose();

    await adminApi.dispose();
  });

  test("SPACES-API-04: space can be soft-deleted and restored", async ({ baseURL }) => {
    const adminApi = await loginAdmin(baseURL);
    const space = await createSpace(adminApi, baseURL, `Space restore ${uniqueSuffix()}`);

    const deleteRes = await adminApi.delete(`${baseURL}/api/admin/spaces/${space.id}`);
    expect(deleteRes.status()).toBe(204);

    const deletedListRes = await adminApi.get(`${baseURL}/api/admin/spaces?status=deleted`);
    expect(deletedListRes.ok()).toBeTruthy();
    const deletedList = await deletedListRes.json();
    expect(deletedList.content.some((s) => s.id === space.id)).toBeTruthy();

    const restoreRes = await adminApi.post(
      `${baseURL}/api/admin/spaces/${space.id}/restore`
    );
    expect(restoreRes.status()).toBe(204);

    const activeListRes = await adminApi.get(`${baseURL}/api/admin/spaces?status=active`);
    expect(activeListRes.ok()).toBeTruthy();
    const activeList = await activeListRes.json();
    expect(activeList.content.some((s) => s.id === space.id)).toBeTruthy();

    await adminApi.dispose();
  });

  test("SPACES-API-05: full permission matrix for guest/reader/editor", async ({
    baseURL,
  }) => {
    const adminApi = await loginAdmin(baseURL);
    const suffix = uniqueSuffix();
    const space = await createSpace(adminApi, baseURL, `Space matrix ${suffix}`);

    const guest = await createUser(adminApi, baseURL, {
      loginName: `guest_matrix_${suffix}`,
      email: `guest_matrix_${suffix}@local.test`,
      password: "GuestPass123!",
      role: "GUEST",
    });
    const reader = await createUser(adminApi, baseURL, {
      loginName: `reader_matrix_${suffix}`,
      email: `reader_matrix_${suffix}@local.test`,
      password: "ReaderPass123!",
      role: "READER",
    });
    const editor = await createUser(adminApi, baseURL, {
      loginName: `editor_matrix_${suffix}`,
      email: `editor_matrix_${suffix}@local.test`,
      password: "EditorPass123!",
      role: "EDITOR",
    });

    const seedDocRes = await createDocument(adminApi, baseURL, {
      title: `Matrix seed ${suffix}`,
      spaceId: space.id,
      content: "seed",
    });
    expect(seedDocRes.status()).toBe(201);
    const seedDoc = await seedDocRes.json();

    // Baseline: no explicit rights for guest -> no access
    const guestNoPermApi = await login(baseURL, guest.login, "GuestPass123!");
    const guestNoPermRead = await guestNoPermApi.get(`${baseURL}/api/documents/${seedDoc.id}`);
    expect([401, 403]).toContain(guestNoPermRead.status());
    await guestNoPermApi.dispose();

    // GUEST + READ
    const grantGuestRead = await adminApi.post(
      `${baseURL}/api/admin/spaces/${space.id}/permissions`,
      { data: { userId: guest.id, permissionType: "READ" } }
    );
    expect(grantGuestRead.status()).toBe(201);
    const guestReadPerm = await grantGuestRead.json();

    const guestReadApi = await login(baseURL, guest.login, "GuestPass123!");
    const guestReadDoc = await guestReadApi.get(`${baseURL}/api/documents/${seedDoc.id}`);
    expect(guestReadDoc.ok()).toBeTruthy();
    const guestReadCreate = await guestReadApi.post(`${baseURL}/api/documents`, {
      data: { title: `Guest fail create ${suffix}`, spaceId: space.id, content: "x" },
    });
    expect([401, 403]).toContain(guestReadCreate.status());
    await guestReadApi.dispose();

    await adminApi.delete(`${baseURL}/api/admin/permissions/${guestReadPerm.id}`);

    // GUEST + WRITE: explicit space WRITE grants create/update (US4.2.2)
    const grantGuestWrite = await adminApi.post(
      `${baseURL}/api/admin/spaces/${space.id}/permissions`,
      { data: { userId: guest.id, permissionType: "WRITE" } }
    );
    expect(grantGuestWrite.status()).toBe(201);

    const guestWriteApi = await login(baseURL, guest.login, "GuestPass123!");
    const guestCreateDoc = await guestWriteApi.post(`${baseURL}/api/documents`, {
      data: {
        title: `Guest write doc ${suffix}`,
        spaceId: space.id,
        content: "created by guest write",
      },
    });
    expect(guestCreateDoc.status()).toBe(201);
    const guestCreated = await guestCreateDoc.json();

    const guestUpdateDoc = await guestWriteApi.put(
      `${baseURL}/api/documents/${guestCreated.id}`,
      {
        data: {
          title: `Guest write doc ${suffix} updated`,
          content: "updated",
        },
      }
    );
    expect(guestUpdateDoc.ok()).toBeTruthy();
    await guestWriteApi.dispose();

    // READER implicit READ, explicit WRITE allowed
    const readerApi = await login(baseURL, reader.login, "ReaderPass123!");
    const readerReadDoc = await readerApi.get(`${baseURL}/api/documents/${seedDoc.id}`);
    expect(readerReadDoc.ok()).toBeTruthy();

    const readerWriteBeforeGrant = await readerApi.post(`${baseURL}/api/documents`, {
      data: { title: `Reader no write ${suffix}`, spaceId: space.id, content: "x" },
    });
    expect([401, 403]).toContain(readerWriteBeforeGrant.status());
    await readerApi.dispose();

    const grantReaderWrite = await adminApi.post(
      `${baseURL}/api/admin/spaces/${space.id}/permissions`,
      { data: { userId: reader.id, permissionType: "WRITE" } }
    );
    expect(grantReaderWrite.status()).toBe(201);

    const readerWriteApi = await login(baseURL, reader.login, "ReaderPass123!");
    const readerCreate = await readerWriteApi.post(`${baseURL}/api/documents`, {
      data: { title: `Reader write ${suffix}`, spaceId: space.id, content: "ok" },
    });
    expect(readerCreate.status()).toBe(201);
    await readerWriteApi.dispose();

    // EDITOR has implicit write globally; explicit WRITE should conflict
    const editorApi = await login(baseURL, editor.login, "EditorPass123!");
    const editorCreate = await editorApi.post(`${baseURL}/api/documents`, {
      data: { title: `Editor implicit ${suffix}`, spaceId: space.id, content: "ok" },
    });
    expect(editorCreate.status()).toBe(201);
    await editorApi.dispose();

    const grantEditorWrite = await adminApi.post(
      `${baseURL}/api/admin/spaces/${space.id}/permissions`,
      { data: { userId: editor.id, permissionType: "WRITE" } }
    );
    expect(grantEditorWrite.status()).toBe(409);

    await adminApi.dispose();
  });
});
