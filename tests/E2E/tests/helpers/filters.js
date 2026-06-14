const { expect } = require("@playwright/test");
const {
  createUser,
  createSpace,
  createDocument,
} = require("./documents");
const {
  waitForUsersLoaded,
  waitForSpacesLoaded,
  waitDocumentsLoaded,
  applyUserFilters,
  applySpacesFilters,
  setUserRoleFilters,
  setUserAdminFilters,
  setUserStatusFilter,
  setSpacesStatusFilter,
  selectSpacesOwnerFilter,
  setDocumentStatusFilters,
  applyDocumentSpaceFilter,
  applyDocumentAuthorFilter,
  selectCustomOption,
} = require("./ui");

async function clearUserFiltersUi(page) {
  const reload = page.waitForResponse(
    (r) =>
      r.url().includes("/api/admin/users") &&
      r.request().method() === "GET" &&
      r.ok()
  );
  await page.locator("#clearFiltersBtn").click();
  await reload;
  await waitForUsersLoaded(page);
}

async function clearSpacesFiltersUi(page) {
  const reload = page.waitForResponse(
    (r) =>
      r.url().includes("/api/admin/spaces") &&
      r.request().method() === "GET" &&
      r.ok()
  );
  await page.locator("#clearFiltersBtn").click();
  await reload;
  await waitForSpacesLoaded(page);
}

async function clearDocumentFiltersUi(page) {
  const reload = page.waitForResponse(
    (r) =>
      r.url().includes("/api/documents?") &&
      r.request().method() === "GET" &&
      r.ok()
  );
  await page.locator("#clearFiltersBtn").click();
  await reload;
  await waitDocumentsLoaded(page);
}

async function gotoDocumentsSearch(page, query) {
  const reload = page.waitForResponse(
    (r) =>
      r.url().includes("/api/documents?") &&
      r.request().method() === "GET" &&
      r.ok()
  );
  await page.goto(`/?q=${encodeURIComponent(query)}`);
  await reload;
  await waitDocumentsLoaded(page);
}

async function applyDocumentStatusFilterUi(page, { draft, published, deleted }) {
  await setDocumentStatusFilters(page, { draft, published, deleted });
  const reload = page.waitForResponse(
    (r) =>
      r.url().includes("/api/documents?") &&
      r.request().method() === "GET" &&
      r.ok()
  );
  await page.locator("#applyFiltersBtn").click();
  const response = await reload;
  await waitDocumentsLoaded(page);
  return response.json();
}

function documentsForTag(payload, tag) {
  return (payload.content || []).filter((doc) => doc.title && doc.title.includes(tag));
}

async function expectDocumentStatusFilterResult(page, data, { allowedStatuses, hiddenTitles }) {
  const payload = await applyDocumentStatusFilterUi(page, {
    draft: allowedStatuses.includes("DRAFT"),
    published: allowedStatuses.includes("PUBLISHED"),
    deleted: allowedStatuses.includes("DELETED"),
  });
  const tagged = documentsForTag(payload, data.tag);

  expect(tagged.every((doc) => allowedStatuses.includes(doc.status))).toBeTruthy();
  for (const title of hiddenTitles) {
    expect(tagged.some((doc) => doc.title === title)).toBeFalsy();
    await expect(page.locator("#documentsTbody")).not.toContainText(title);
  }
}

async function applyDocumentSpaceFilterUi(page, spaceId, spaceName) {
  const reload = page.waitForResponse(
    (r) =>
      r.url().includes("/api/documents?") &&
      r.url().includes(`spaceId=${spaceId}`) &&
      r.request().method() === "GET" &&
      r.ok()
  );
  await applyDocumentSpaceFilter(page, spaceId, spaceName);
  const response = await reload;
  await waitDocumentsLoaded(page);
  return response.json();
}

async function applyUserFiltersUi(page, { search, roles, admin, status } = {}) {
  if (status) {
    await setUserStatusFilter(page, status);
  }
  if (roles !== undefined) {
    await setUserRoleFilters(page, roles);
  }
  if (admin !== undefined) {
    await setUserAdminFilters(page, admin);
  }
  if (search !== undefined) {
    await page.locator("#searchInput").fill(search);
  }
  await applyUserFilters(page);
  await waitForUsersLoaded(page);
}

async function applySpacesFiltersUi(page, { status, ownerId } = {}) {
  if (status) {
    await setSpacesStatusFilter(page, status);
  }
  if (ownerId) {
    await selectSpacesOwnerFilter(page, ownerId);
  }
  await applySpacesFilters(page);
  await waitForSpacesLoaded(page);
}

async function applyDocumentFiltersUi(
  page,
  { draft, published, deleted, spaceId, spaceName, authorId } = {}
) {
  if (draft !== undefined || published !== undefined || deleted !== undefined) {
    await setDocumentStatusFilters(page, { draft, published, deleted });
  }
  if (spaceId) {
    await applyDocumentSpaceFilter(page, spaceId, spaceName);
    await waitDocumentsLoaded(page);
    return;
  }
  if (authorId) {
    await applyDocumentAuthorFilter(page, authorId);
    await waitDocumentsLoaded(page);
    return;
  }
  const reload = page.waitForResponse(
    (r) =>
      r.url().includes("/api/documents?") &&
      r.request().method() === "GET" &&
      r.ok()
  );
  await page.locator("#applyFiltersBtn").click();
  await reload;
  await waitDocumentsLoaded(page);
}

function tbodyText(page, tableId) {
  return page.locator(`#${tableId} tbody`).innerText();
}

async function expectTableShows(page, tableId, texts) {
  for (const text of texts) {
    await expect(page.locator(`#${tableId} tbody`)).toContainText(text);
  }
}

async function expectTableHides(page, tableId, texts) {
  for (const text of texts) {
    await expect(page.locator(`#${tableId} tbody`)).not.toContainText(text);
  }
}

/** Уникальный набор пользователей для матрицы фильтров. */
async function seedUsersFilterDataset(adminApi, baseURL, tag) {
  const guest = await adminApi.post(`${baseURL}/api/admin/users`, {
    data: {
      login: `filt_guest_${tag}`,
      email: `filt_guest_${tag}@local.test`,
      password: "TempPass123!",
      role: "GUEST",
      isAdmin: false,
    },
  });
  expect(guest.status()).toBe(201);
  const guestUser = await guest.json();

  const reader = await adminApi.post(`${baseURL}/api/admin/users`, {
    data: {
      login: `filt_reader_${tag}`,
      email: `filt_reader_${tag}@local.test`,
      password: "TempPass123!",
      role: "READER",
      isAdmin: false,
    },
  });
  expect(reader.status()).toBe(201);
  const readerUser = await reader.json();

  const editor = await adminApi.post(`${baseURL}/api/admin/users`, {
    data: {
      login: `filt_editor_${tag}`,
      email: `filt_editor_${tag}@local.test`,
      password: "TempPass123!",
      role: "EDITOR",
      isAdmin: false,
    },
  });
  expect(editor.status()).toBe(201);
  const editorUser = await editor.json();

  const deleted = await adminApi.post(`${baseURL}/api/admin/users`, {
    data: {
      login: `filt_deleted_${tag}`,
      email: `filt_deleted_${tag}@local.test`,
      password: "TempPass123!",
      role: "GUEST",
      isAdmin: false,
    },
  });
  expect(deleted.status()).toBe(201);
  const deletedUser = await deleted.json();
  const deleteRes = await adminApi.delete(
    `${baseURL}/api/admin/users/${deletedUser.id}`
  );
  expect(deleteRes.ok()).toBeTruthy();

  return {
    tag,
    guestLogin: guestUser.login,
    readerLogin: readerUser.login,
    editorLogin: editorUser.login,
    deletedLogin: deletedUser.login,
  };
}

async function seedSpacesFilterDataset(adminApi, baseURL, tag) {
  const meRes = await adminApi.get(`${baseURL}/api/auth/me`);
  const adminUser = await meRes.json();

  const owner = await createUser(adminApi, baseURL, {
    login: `filt_owner_${tag}`,
    email: `filt_owner_${tag}@local.test`,
    password: "OwnerPass123!",
    role: "EDITOR",
    isAdmin: true,
  });

  const ownedSpace = await createSpace(adminApi, baseURL, `Filt owned ${tag}`, {
    ownerId: owner.id,
    description: "filter combo",
  });

  // Omit ownerId — authenticated admin becomes owner (same as explicit adminUser.id).
  const foreignSpace = await createSpace(adminApi, baseURL, `Filt foreign ${tag}`, {
    description: "filter combo",
  });

  const deletedSpace = await createSpace(adminApi, baseURL, `Filt deleted ${tag}`, {
    description: "filter combo",
  });
  await adminApi.delete(`${baseURL}/api/admin/spaces/${deletedSpace.id}`);

  return {
    tag,
    ownerId: owner.id,
    adminOwnerId: adminUser.id,
    ownedSpaceName: ownedSpace.name,
    foreignSpaceName: foreignSpace.name,
    deletedSpaceName: deletedSpace.name,
  };
}

async function seedDocumentsFilterDataset(adminApi, baseURL, tag) {
  const spaceA = await createSpace(adminApi, baseURL, `Filt docs A ${tag}`, {
    description: "docs filters",
  });
  const spaceB = await createSpace(adminApi, baseURL, `Filt docs B ${tag}`, {
    description: "docs filters",
  });

  const draftTitle = `FiltDraft_${tag}`;
  const publishedTitle = `FiltPub_${tag}`;
  const otherSpaceTitle = `FiltOtherSpace_${tag}`;

  const draftRes = await createDocument(adminApi, baseURL, {
    title: draftTitle,
    spaceId: spaceA.id,
    content: "draft",
  });
  expect(draftRes.status()).toBe(201);

  const pubCreate = await createDocument(adminApi, baseURL, {
    title: publishedTitle,
    spaceId: spaceA.id,
    content: "pub",
  });
  expect(pubCreate.status()).toBe(201);
  const pubDoc = await pubCreate.json();
  const publishRes = await adminApi.put(`${baseURL}/api/documents/${pubDoc.id}`, {
    data: { title: publishedTitle, content: "pub", status: "PUBLISHED" },
  });
  expect(publishRes.ok()).toBeTruthy();

  const otherRes = await createDocument(adminApi, baseURL, {
    title: otherSpaceTitle,
    spaceId: spaceB.id,
    content: "other",
  });
  expect(otherRes.status()).toBe(201);

  return {
    tag,
    spaceA,
    spaceB,
    draftTitle,
    publishedTitle,
    otherSpaceTitle,
  };
}

module.exports = {
  clearUserFiltersUi,
  clearSpacesFiltersUi,
  clearDocumentFiltersUi,
  applyUserFiltersUi,
  applySpacesFiltersUi,
  applyDocumentFiltersUi,
  tbodyText,
  expectTableShows,
  expectTableHides,
  seedUsersFilterDataset,
  seedSpacesFilterDataset,
  seedDocumentsFilterDataset,
  gotoDocumentsSearch,
  applyDocumentStatusFilterUi,
  applyDocumentSpaceFilterUi,
  documentsForTag,
  expectDocumentStatusFilterResult,
};
