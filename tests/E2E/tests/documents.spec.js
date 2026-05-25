const { test, expect } = require("@playwright/test");
const { createAuthenticatedPage } = require("./helpers/session");
const {
  createAdminApi,
  createUserApi,
  newBrowserPageFromApi,
  createSpace,
  grantSpacePermission,
  createUser,
  createDocument,
  getFirstSpaceId,
  uniqueSuffix,
} = require("./helpers/documents");
const {
  selectDocSpace,
  waitForDocSpaceOptions,
  setDocStatusDraft,
  selectCustomOptionByText,
  applyDocumentSpaceFilter,
} = require("./helpers/ui");

// --- UI: список документов ---

test("@documents DOC01: list page loads table, filters and create button", async ({
  browser,
  request,
  baseURL,
}) => {
  const { context, page } = await createAuthenticatedPage({
    browser,
    request,
    baseURL,
  });

  await page.goto("/");
  await expect(page.locator("h1")).toContainText("Документы");
  await expect(page.locator("#documentsTable")).toBeVisible();
  await expect(page.locator("#statusFilter-wrapper")).toBeVisible();
  await expect(page.locator("#spaceFilter-wrapper")).toBeVisible();
  await expect(page.locator("#applyFiltersBtn")).toBeVisible();
  await expect(page.locator("#createBtn")).toBeVisible();

  await context.close();
});

test("@documents DOC02: list shows created document after load", async ({
  browser,
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const spaceId = await getFirstSpaceId(adminApi, baseURL);
  const title = `E2E List ${uniqueSuffix()}`;
  const createRes = await createDocument(adminApi, baseURL, {
    title,
    spaceId,
    content: "# list test",
  });
  expect(createRes.ok()).toBeTruthy();

  const { context, page } = await newBrowserPageFromApi(browser, adminApi);
  await page.goto("/");
  await expect(page.locator("#documentsTbody")).not.toContainText("Загрузка...", {
    timeout: 15_000,
  });
  await expect(page.locator("#documentsTbody")).toContainText(title);

  await context.close();
  await adminApi.dispose();
});

test("@documents DOC03: space filter narrows list to one space", async ({
  browser,
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const spaceA = await createSpace(adminApi, baseURL, `E2E filt A ${uniqueSuffix()}`);
  const spaceB = await createSpace(adminApi, baseURL, `E2E filt B ${uniqueSuffix()}`);
  const visible = `E2E filt doc A ${uniqueSuffix()}`;
  const hidden = `E2E filt doc B ${uniqueSuffix()}`;

  expect(
    (await createDocument(adminApi, baseURL, {
      title: visible,
      spaceId: spaceA.id,
    })).ok()
  ).toBeTruthy();
  expect(
    (await createDocument(adminApi, baseURL, {
      title: hidden,
      spaceId: spaceB.id,
    })).ok()
  ).toBeTruthy();

  const { context, page } = await newBrowserPageFromApi(browser, adminApi);
  await page.goto("/");
  await expect(page.locator("#documentsTbody")).toContainText(visible, {
    timeout: 15_000,
  });

  await applyDocumentSpaceFilter(page, spaceA.id, spaceA.name);
  await expect(page.locator("#documentsTbody")).toContainText(visible);
  await expect(page.locator("#documentsTbody")).not.toContainText(hidden);

  await context.close();
  await adminApi.dispose();
});

test("@documents DOC04: clear filters restores full list for space", async ({
  browser,
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const spaceId = await getFirstSpaceId(adminApi, baseURL);
  const title = `E2Eclr_${uniqueSuffix()}`;
  expect(
    (await createDocument(adminApi, baseURL, { title, spaceId })).ok()
  ).toBeTruthy();

  const { context, page } = await newBrowserPageFromApi(browser, adminApi);
  await page.goto("/");
  await expect(page.locator("#documentsTbody")).toContainText(title, {
    timeout: 15_000,
  });

  await selectCustomOptionByText(page, "statusFilter-wrapper", "Опубликовано");
  await page.locator("#applyFiltersBtn").click();
  await expect(page.locator("#documentsTbody")).toContainText("Документы не найдены");

  await page.locator("#clearFiltersBtn").click();
  await expect(page.locator("#documentsTbody")).toContainText(title);

  await context.close();
  await adminApi.dispose();
});

test("@documents DOC05: create document via UI redirects to editor", async ({
  browser,
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const space = await createSpace(
    adminApi,
    baseURL,
    `E2E UI Create ${uniqueSuffix()}`
  );
  const title = `E2E UI doc ${uniqueSuffix()}`;

  const { context, page } = await newBrowserPageFromApi(browser, adminApi);
  await page.goto("/documents/new");
  await waitForDocSpaceOptions(page, 1);

  await page.locator("#docTitle").fill(title);
  await selectDocSpace(page, space.id);
  await page.locator("#docContent").fill("# Created from E2E\n\nParagraph.");

  const createResponsePromise = page.waitForResponse(
    (r) =>
      r.url().includes("/api/documents") && r.request().method() === "POST"
  );
  await page.locator("#submitBtn").click();
  const createResponse = await createResponsePromise;
  expect(createResponse.status()).toBe(201);

  await expect(page.locator("#toast")).toContainText(/успешно создан/i, {
    timeout: 5_000,
  });
  await expect(page).toHaveURL(/\/$/, { timeout: 15_000 });
  await expect(page.locator("#documentsTbody")).toContainText(title, {
    timeout: 15_000,
  });

  await context.close();
  await adminApi.dispose();
});

test("@documents DOC06: view page renders title and markdown body", async ({
  browser,
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const spaceId = await getFirstSpaceId(adminApi, baseURL);
  const title = `E2E View ${uniqueSuffix()}`;
  const content = "# Heading\n\n**bold** text";
  const createRes = await createDocument(adminApi, baseURL, {
    title,
    spaceId,
    content,
  });
  expect(createRes.ok()).toBeTruthy();
  const doc = await createRes.json();

  const { context, page } = await newBrowserPageFromApi(browser, adminApi);
  await page.goto(`/documents/${doc.id}`);
  await expect(page.locator("#docTitle")).toContainText(title, {
    timeout: 10_000,
  });
  await expect(page.locator("#documentContent")).toContainText("bold");
  await expect(page.locator("#documentContent")).toContainText("Heading");
  await expect(page.locator("main")).not.toContainText(
    "Содержимое документа будет отображаться здесь."
  );

  await context.close();
  await adminApi.dispose();
});

test("@documents DOC07: edit page save persists title and content", async ({
  browser,
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const spaceId = await getFirstSpaceId(adminApi, baseURL);
  const createRes = await createDocument(adminApi, baseURL, {
    title: `E2E Edit ${uniqueSuffix()}`,
    spaceId,
    content: "initial",
  });
  const doc = await createRes.json();
  const newTitle = `E2E Saved ${uniqueSuffix()}`;
  const newContent = "# Updated\n\nNew paragraph.";

  const { context, page } = await newBrowserPageFromApi(browser, adminApi);
  await page.goto(`/documents/${doc.id}/edit`);
  await expect(page.locator("#editorTextarea")).toHaveValue("initial", {
    timeout: 10_000,
  });

  await page.locator("#docTitleField").fill(newTitle);
  await page.locator("#editorTextarea").fill(newContent);
  // UI select uses "Draft" but API expects enum DRAFT — подставляем значение API
  await setDocStatusDraft(page);
  await page.locator("#saveBtn").click();
  await expect(page.locator("#toast")).toContainText("Сохранено", {
    timeout: 5_000,
  });

  await page.reload();
  await expect(page.locator("#docTitleField")).toHaveValue(newTitle, {
    timeout: 10_000,
  });
  await expect(page.locator("#editorTextarea")).toHaveValue(newContent);
  await expect(page.locator("#previewContent")).toContainText("Updated");

  await context.close();
  await adminApi.dispose();
});

test("@documents DOC08: Ctrl+S saves from editor", async ({ browser, baseURL }) => {
  const adminApi = await createAdminApi(baseURL);
  const spaceId = await getFirstSpaceId(adminApi, baseURL);
  const createRes = await createDocument(adminApi, baseURL, {
    title: `E2E CtrlS ${uniqueSuffix()}`,
    spaceId,
    content: "before",
  });
  const doc = await createRes.json();

  const { context, page } = await newBrowserPageFromApi(browser, adminApi);
  await page.goto(`/documents/${doc.id}/edit`);
  await expect(page.locator("#editorTextarea")).toBeVisible({ timeout: 10_000 });
  await page.locator("#editorTextarea").fill("after ctrl+s");
  await setDocStatusDraft(page);
  await page.keyboard.press("Control+s");
  await expect(page.locator("#toast")).toContainText("Сохранено", {
    timeout: 5_000,
  });

  await context.close();
  await adminApi.dispose();
});

test("@documents DOC09: delete from list removes document row", async ({
  browser,
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const spaceId = await getFirstSpaceId(adminApi, baseURL);
  const title = `E2E Del ${uniqueSuffix()}`;
  const createRes = await createDocument(adminApi, baseURL, { title, spaceId });
  const doc = await createRes.json();

  const { context, page } = await newBrowserPageFromApi(browser, adminApi);
  page.on("dialog", (dialog) => dialog.accept());
  await page.goto("/");
  await expect(page.locator("#documentsTbody")).toContainText(title, {
    timeout: 15_000,
  });

  const row = page.locator("#documentsTbody tr", { hasText: title });
  await row.locator("button.button-danger").click();
  await expect(page.locator("#toast")).toContainText("Документ удален", {
    timeout: 5_000,
  });
  // Список грузится с includeDeleted=true — строка остаётся со статусом DELETED
  const deletedRow = page.locator("#documentsTbody tr", { hasText: title });
  await expect(deletedRow.locator(".badge")).toContainText("DELETED", {
    timeout: 10_000,
  });

  const getRes = await adminApi.get(`${baseURL}/api/documents/${doc.id}`);
  expect(getRes.ok()).toBeTruthy();
  const deleted = await getRes.json();
  expect(deleted.status).toBe("DELETED");

  await context.close();
  await adminApi.dispose();
});

test("@documents DOC10: create form rejects whitespace-only title", async ({
  browser,
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const { context, page } = await newBrowserPageFromApi(browser, adminApi);

  await page.goto("/documents/new");
  await waitForDocSpaceOptions(page, 1);
  await page.locator("#docTitle").fill("   ");
  await page.locator("#docSpaceWrapper .select-styled").click();
  await page.locator("#docSpaceWrapper .select-option").first().click();
  await page.locator("#submitBtn").click();
  await expect(page.locator("#toast")).toContainText("обязательны", {
    timeout: 5_000,
  });
  await expect(page).toHaveURL(/\/documents\/new/);

  await context.close();
  await adminApi.dispose();
});

test("@documents DOC11: non-existent document shows load error on view", async ({
  browser,
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const { context, page } = await newBrowserPageFromApi(browser, adminApi);

  await page.goto("/documents/999999999");
  await expect(page.locator("body")).toContainText(/не найден|Ошибка при загрузке/i, {
    timeout: 10_000,
  });

  await context.close();
  await adminApi.dispose();
});

test("@documents DOC12: cancel on new form returns to list", async ({
  browser,
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const { context, page } = await newBrowserPageFromApi(browser, adminApi);

  await page.goto("/documents/new");
  await page.getByRole("button", { name: "Отмена" }).click();
  await expect(page).toHaveURL(/\/$/);

  await context.close();
  await adminApi.dispose();
});

test("@documents DOC13: markdown with raw HTML is rendered as-is (marked.js)", async ({
  browser,
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const spaceId = await getFirstSpaceId(adminApi, baseURL);
  const payload = '<script>alert("xss")</script>';
  const createRes = await createDocument(adminApi, baseURL, {
    title: `E2E HTML ${uniqueSuffix()}`,
    spaceId,
    content: payload,
  });
  const doc = await createRes.json();

  const { context, page } = await newBrowserPageFromApi(browser, adminApi);
  await page.goto(`/documents/${doc.id}`);
  await expect(page.locator("#documentContent")).toBeVisible({ timeout: 10_000 });
  const html = await page.locator("#documentContent").innerHTML();
  // marked.js пропускает inline HTML — тест фиксирует текущее поведение
  expect(html.toLowerCase()).toContain("script");

  await context.close();
  await adminApi.dispose();
});

// --- API: валидация и CRUD ---

test("@documents DOC14: anonymous document API is rejected", async ({
  request,
  baseURL,
}) => {
  const response = await request.get(`${baseURL}/api/documents?spaceId=1`);
  expect(response.ok()).toBeFalsy();
  expect([401, 403]).toContain(response.status());
});

test("@documents DOC15: list without spaceId returns accessible documents", async ({
  baseURL,
}) => {
  const api = await createAdminApi(baseURL);
  const response = await api.get(`${baseURL}/api/documents?includeDeleted=false`);
  expect(response.ok()).toBeTruthy();
  const list = await response.json();
  expect(Array.isArray(list)).toBeTruthy();
  await api.dispose();
});

test("@documents DOC16: create without title returns 400", async ({ baseURL }) => {
  const api = await createAdminApi(baseURL);
  const spaceId = await getFirstSpaceId(api, baseURL);
  const response = await api.post(`${baseURL}/api/documents`, {
    data: { title: "", spaceId, content: "x" },
  });
  expect(response.ok()).toBeFalsy();
  expect([400, 422]).toContain(response.status());
  await api.dispose();
});

test("@documents DOC17: create with missing spaceId returns 400", async ({
  baseURL,
}) => {
  const api = await createAdminApi(baseURL);
  const response = await api.post(`${baseURL}/api/documents`, {
    data: { title: "No space", content: "x" },
  });
  expect(response.ok()).toBeFalsy();
  expect([400, 422]).toContain(response.status());
  await api.dispose();
});

test("@documents DOC18: title longer than 500 chars is rejected", async ({
  baseURL,
}) => {
  const api = await createAdminApi(baseURL);
  const spaceId = await getFirstSpaceId(api, baseURL);
  const response = await api.post(`${baseURL}/api/documents`, {
    data: { title: "A".repeat(501), spaceId, content: "" },
  });
  expect(response.ok()).toBeFalsy();
  expect([400, 422]).toContain(response.status());
  await api.dispose();
});

test("@documents DOC19: create in non-existent space returns 404", async ({
  baseURL,
}) => {
  const api = await createAdminApi(baseURL);
  const response = await api.post(`${baseURL}/api/documents`, {
    data: { title: "Ghost space", spaceId: 999999999, content: "" },
  });
  expect(response.status()).toBe(404);
  await api.dispose();
});

test("@documents DOC20: get non-existent document is rejected", async ({
  baseURL,
}) => {
  const api = await createAdminApi(baseURL);
  const response = await api.get(`${baseURL}/api/documents/999999999`);
  expect(response.ok()).toBeFalsy();
  expect([404, 500]).toContain(response.status());
  await api.dispose();
});

test("@documents DOC21: full API lifecycle create-read-update-delete", async ({
  baseURL,
}) => {
  const api = await createAdminApi(baseURL);
  const spaceId = await getFirstSpaceId(api, baseURL);
  const title = `E2E CRUD ${uniqueSuffix()}`;

  const createRes = await createDocument(api, baseURL, {
    title,
    spaceId,
    content: "v1",
  });
  expect(createRes.status()).toBe(201);
  const created = await createRes.json();
  expect(created.title).toBe(title);
  expect(created.status).toBe("DRAFT");

  const getRes = await api.get(`${baseURL}/api/documents/${created.id}`);
  expect(getRes.ok()).toBeTruthy();
  const fetched = await getRes.json();
  expect(fetched.content).toBe("v1");

  const updateRes = await api.put(`${baseURL}/api/documents/${created.id}`, {
    data: {
      title: title + " updated",
      content: "v2",
      status: "PUBLISHED",
    },
  });
  expect(updateRes.ok()).toBeTruthy();
  const updated = await updateRes.json();
  expect(updated.status).toBe("PUBLISHED");
  expect(updated.content).toBe("v2");

  const deleteRes = await api.delete(`${baseURL}/api/documents/${created.id}`);
  expect(deleteRes.status()).toBe(204);

  const afterDelete = await api.get(`${baseURL}/api/documents/${created.id}`);
  expect(afterDelete.ok()).toBeTruthy();
  expect((await afterDelete.json()).status).toBe("DELETED");

  await api.dispose();
});

test("@documents DOC22: includeDeleted flag controls deleted visibility in list", async ({
  baseURL,
}) => {
  const api = await createAdminApi(baseURL);
  const spaceId = await getFirstSpaceId(api, baseURL);
  const title = `E2E incDel ${uniqueSuffix()}`;
  const createRes = await createDocument(api, baseURL, { title, spaceId });
  const doc = await createRes.json();
  await api.delete(`${baseURL}/api/documents/${doc.id}`);

  const without = await api.get(
    `${baseURL}/api/documents?spaceId=${spaceId}&includeDeleted=false`
  );
  const listWithout = await without.json();
  expect(listWithout.find((d) => d.id === doc.id)).toBeFalsy();

  const withDeleted = await api.get(
    `${baseURL}/api/documents?spaceId=${spaceId}&includeDeleted=true`
  );
  const listWith = await withDeleted.json();
  expect(listWith.find((d) => d.id === doc.id)?.status).toBe("DELETED");

  await api.dispose();
});

test("@documents DOC23: second delete on same document is idempotent (204)", async ({
  baseURL,
}) => {
  const api = await createAdminApi(baseURL);
  const spaceId = await getFirstSpaceId(api, baseURL);
  const createRes = await createDocument(api, baseURL, {
    title: `E2E idem ${uniqueSuffix()}`,
    spaceId,
  });
  const doc = await createRes.json();

  const first = await api.delete(`${baseURL}/api/documents/${doc.id}`);
  const second = await api.delete(`${baseURL}/api/documents/${doc.id}`);
  expect(first.status()).toBe(204);
  expect(second.status()).toBe(204);

  await api.dispose();
});

test("@documents DOC24: unicode title and large content round-trip", async ({
  baseURL,
}) => {
  const api = await createAdminApi(baseURL);
  const spaceId = await getFirstSpaceId(api, baseURL);
  const title = `Документ 文档 🚀 ${uniqueSuffix()}`;
  const content = "# Big\n\n" + "line\n".repeat(500);

  const createRes = await createDocument(api, baseURL, { title, spaceId, content });
  expect(createRes.ok()).toBeTruthy();
  const doc = await createRes.json();

  const getRes = await api.get(`${baseURL}/api/documents/${doc.id}`);
  const fetched = await getRes.json();
  expect(fetched.title).toBe(title);
  expect(fetched.content.length).toBeGreaterThan(1000);

  await api.dispose();
});

// --- RBAC ---

test("@documents DOC25: GUEST without space permission cannot read document", async ({
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const space = await createSpace(adminApi, baseURL, `E2E iso ${uniqueSuffix()}`);
  const suffix = uniqueSuffix();
  await createUser(adminApi, baseURL, {
    login: `guest_doc_${suffix}`,
    email: `guest_doc_${suffix}@local.test`,
    password: "GuestPass123!",
    role: "GUEST",
  });

  const createRes = await createDocument(adminApi, baseURL, {
    title: `E2E private ${suffix}`,
    spaceId: space.id,
  });
  const doc = await createRes.json();

  const guestApi = await createUserApi(
    baseURL,
    `guest_doc_${suffix}`,
    "GuestPass123!"
  );
  const forbidden = await guestApi.get(`${baseURL}/api/documents/${doc.id}`);
  expect(forbidden.ok()).toBeFalsy();
  expect([401, 403]).toContain(forbidden.status());

  await guestApi.dispose();
  await adminApi.dispose();
});

test("@documents DOC26: READER with READ can get but not update or delete", async ({
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const space = await createSpace(adminApi, baseURL, `E2E read ${uniqueSuffix()}`);
  const suffix = uniqueSuffix();
  const reader = await createUser(adminApi, baseURL, {
    login: `reader_r_${suffix}`,
    email: `reader_r_${suffix}@local.test`,
    password: "ReaderPass123!",
    role: "READER",
  });
  await grantSpacePermission(adminApi, baseURL, space.id, reader.id, "READ");

  const createRes = await createDocument(adminApi, baseURL, {
    title: `E2E read doc ${suffix}`,
    spaceId: space.id,
    content: "readable",
  });
  const doc = await createRes.json();

  const readerApi = await createUserApi(
    baseURL,
    `reader_r_${suffix}`,
    "ReaderPass123!"
  );
  const getRes = await readerApi.get(`${baseURL}/api/documents/${doc.id}`);
  expect(getRes.ok()).toBeTruthy();

  const putRes = await readerApi.put(`${baseURL}/api/documents/${doc.id}`, {
    data: { title: "hack", content: "hack" },
  });
  expect(putRes.ok()).toBeFalsy();
  expect([401, 403]).toContain(putRes.status());

  const delRes = await readerApi.delete(`${baseURL}/api/documents/${doc.id}`);
  expect(delRes.ok()).toBeFalsy();
  expect([401, 403]).toContain(delRes.status());

  await readerApi.dispose();
  await adminApi.dispose();
});

test("@documents DOC27: READER without space permission cannot create document", async ({
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const space = await createSpace(adminApi, baseURL, `E2E nowrite ${uniqueSuffix()}`);
  const suffix = uniqueSuffix();
  await createUser(adminApi, baseURL, {
    login: `reader_np_${suffix}`,
    email: `reader_np_${suffix}@local.test`,
    password: "ReaderPass123!",
    role: "READER",
  });

  const readerApi = await createUserApi(
    baseURL,
    `reader_np_${suffix}`,
    "ReaderPass123!"
  );
  const response = await createDocument(readerApi, baseURL, {
    title: "Should fail",
    spaceId: space.id,
  });
  expect(response.ok()).toBeFalsy();
  expect([401, 403]).toContain(response.status());

  await readerApi.dispose();
  await adminApi.dispose();
});

test("@documents DOC28: EDITOR with WRITE can create and update document", async ({
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const space = await createSpace(adminApi, baseURL, `E2E write ${uniqueSuffix()}`);
  const suffix = uniqueSuffix();
  const editor = await createUser(adminApi, baseURL, {
    login: `editor_w_${suffix}`,
    email: `editor_w_${suffix}@local.test`,
    password: "EditorPass123!",
    role: "EDITOR",
  });
  await grantSpacePermission(adminApi, baseURL, space.id, editor.id, "WRITE");

  const editorApi = await createUserApi(
    baseURL,
    `editor_w_${suffix}`,
    "EditorPass123!"
  );
  const createRes = await createDocument(editorApi, baseURL, {
    title: `E2E editor doc ${suffix}`,
    spaceId: space.id,
    content: "by editor",
  });
  expect(createRes.status()).toBe(201);
  const doc = await createRes.json();

  const updateRes = await editorApi.put(`${baseURL}/api/documents/${doc.id}`, {
    data: { title: `E2E editor doc ${suffix} v2`, content: "updated" },
  });
  expect(updateRes.ok()).toBeTruthy();

  await editorApi.dispose();
  await adminApi.dispose();
});

test("@documents DOC29: READER cannot create even with READ permission on space", async ({
  baseURL,
}) => {
  const adminApi = await createAdminApi(baseURL);
  const space = await createSpace(adminApi, baseURL, `E2E ronly ${uniqueSuffix()}`);
  const suffix = uniqueSuffix();
  const reader = await createUser(adminApi, baseURL, {
    login: `reader_c_${suffix}`,
    email: `reader_c_${suffix}@local.test`,
    password: "ReaderPass123!",
    role: "READER",
  });
  await grantSpacePermission(adminApi, baseURL, space.id, reader.id, "READ");

  const readerApi = await createUserApi(
    baseURL,
    `reader_c_${suffix}`,
    "ReaderPass123!"
  );
  const response = await createDocument(readerApi, baseURL, {
    title: "Reader create attempt",
    spaceId: space.id,
  });
  expect(response.ok()).toBeFalsy();
  expect([401, 403]).toContain(response.status());

  await readerApi.dispose();
  await adminApi.dispose();
});

test("@documents DOC30: PUT with whitespace-only title keeps previous title", async ({
  baseURL,
}) => {
  const api = await createAdminApi(baseURL);
  const spaceId = await getFirstSpaceId(api, baseURL);
  const originalTitle = `E2E blank ${uniqueSuffix()}`;
  const createRes = await createDocument(api, baseURL, {
    title: originalTitle,
    spaceId,
  });
  const doc = await createRes.json();

  const updateRes = await api.put(`${baseURL}/api/documents/${doc.id}`, {
    data: { title: "   ", content: "x" },
  });
  expect(updateRes.ok()).toBeTruthy();
  const updated = await updateRes.json();
  // Пустой/пробельный title трактуется как «без изменений» — заголовок не затирается
  expect(updated.title).toBe(originalTitle);
  expect(updated.content).toBe("x");

  await api.dispose();
});
