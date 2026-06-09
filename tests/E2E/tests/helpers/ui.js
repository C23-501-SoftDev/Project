const { expect } = require("@playwright/test");

/** Открыть custom-select и выбрать опцию по data-value. */
async function selectCustomOption(page, wrapperId, value) {
  const wrapper = page.locator(`#${wrapperId}`);
  await wrapper.locator(".select-styled").click();
  await wrapper.locator(`.select-option[data-value="${value}"]`).click();
}

/** Выбрать опцию custom-select по видимому тексту. */
async function selectCustomOptionByText(page, wrapperId, text) {
  const wrapper = page.locator(`#${wrapperId}`);
  await wrapper.locator(".select-styled").click();
  await wrapper.locator(".select-option").filter({ hasText: text }).click();
}

/** Дождаться появления опций пространства в форме создания документа. */
async function waitForDocSpaceOptions(page, minCount = 1) {
  await expect
    .poll(async () => page.locator("#docSpaceWrapper .select-option").count())
    .toBeGreaterThanOrEqual(minCount);
}

/** Выбрать пространство на форме создания документа (id — строка или число). */
async function selectDocSpace(page, spaceId) {
  await waitForDocSpaceOptions(page, 1);
  await selectCustomOption(page, "docSpaceWrapper", String(spaceId));
}

/** Установить статус DRAFT в редакторе (custom-select вместо <select>). */
async function setDocStatusDraft(page) {
  await selectCustomOption(page, "docStatusWrapper", "DRAFT");
}

/** Последняя страница таблицы пользователей (новые id в конце при sort asc). */
async function goToLastUsersPage(page) {
  const info = await page.locator("#usersPageInfo").textContent();
  const match = info.match(/из (\d+)/);
  const totalPages = match ? parseInt(match[1], 10) : 1;
  const next = page.locator("#usersNextBtn");
  for (let i = 1; i < totalPages; i++) {
    if (await next.isEnabled()) {
      const reload = page.waitForResponse(
        (r) =>
          r.url().includes("/api/admin/users") &&
          r.request().method() === "GET" &&
          r.ok()
      );
      await next.click();
      await reload;
    }
  }
}

async function prepareUsersTableNewestFirst(page) {
  await page.goto("/admin/users");
  await expect(page.locator("#usersTbody")).not.toContainText("Загрузка");
  await goToLastUsersPage(page);
}

/** Последняя страница таблицы пространств. */
async function goToLastSpacesPage(page) {
  const info = await page.locator("#spacesPageInfo").textContent();
  const match = info.match(/из (\d+)/);
  const totalPages = match ? parseInt(match[1], 10) : 1;
  const next = page.locator("#spacesNextBtn");
  for (let i = 1; i < totalPages; i++) {
    if (await next.isEnabled()) {
      const reload = page.waitForResponse(
        (r) =>
          r.url().includes("/api/admin/spaces") &&
          r.request().method() === "GET" &&
          r.ok()
      );
      await next.click();
      await reload;
    }
  }
}

/** Выбрать первого доступного владельца при создании пространства. */
async function selectFirstSpaceOwner(page) {
  const wrapper = page.locator("#spaceOwnerWrapper");
  await wrapper.locator(".select-styled").click();
  await wrapper.locator(".select-option").first().click();
  await expect(page.locator("#spaceOwner")).not.toHaveValue("");
}

/** Дождаться загрузки опций пространства в фильтре списка документов. */
async function waitForSpaceFilterOptions(page, minCount = 2) {
  await expect
    .poll(async () => page.locator("#spaceFilterWrapper .select-option").count())
    .toBeGreaterThanOrEqual(minCount);
}

/** Установить чекбоксы фильтра статусов на странице списка документов. */
async function setDocumentStatusFilters(page, { draft, published, deleted }) {
  if (draft !== undefined) {
    await page.locator('.statusFilter[value="Draft"]').setChecked(draft);
  }
  if (published !== undefined) {
    await page.locator('.statusFilter[value="Published"]').setChecked(published);
  }
  if (deleted !== undefined) {
    await page.locator('.statusFilter[value="Deleted"]').setChecked(deleted);
  }
}

/** Фильтр списка документов по пространству (custom-select). */
async function applyDocumentSpaceFilter(page, spaceId, spaceName) {
  await waitForSpaceFilterOptions(page, 2);
  const wrapper = page.locator("#spaceFilterWrapper");
  await wrapper.locator(".select-styled").click();
  const option = wrapper.locator(`.select-option[data-value="${spaceId}"]`);
  if ((await option.count()) > 0) {
    await option.click();
  } else if (spaceName) {
    await wrapper.locator(".select-option").filter({ hasText: spaceName }).click();
  }
  await page.locator("#applyFiltersBtn").click();
}

async function waitDocumentsLoaded(page) {
  await expect(page.locator("#documentsTbody")).not.toContainText("Загрузка...", {
    timeout: 15_000,
  });
}

async function goToLastDocumentsPage(page) {
  const next = page.locator("#documentsNextBtn");
  while (await next.isEnabled()) {
    const reload = page.waitForResponse(
      (r) =>
        r.url().includes("/api/documents?") &&
        r.request().method() === "GET" &&
        r.ok()
    );
    await next.click();
    await reload;
    await waitDocumentsLoaded(page);
  }
}

module.exports = {
  selectCustomOption,
  selectCustomOptionByText,
  waitForDocSpaceOptions,
  selectDocSpace,
  setDocStatusDraft,
  goToLastUsersPage,
  prepareUsersTableNewestFirst,
  goToLastSpacesPage,
  selectFirstSpaceOwner,
  waitForSpaceFilterOptions,
  setDocumentStatusFilters,
  applyDocumentSpaceFilter,
  waitDocumentsLoaded,
  goToLastDocumentsPage,
};
