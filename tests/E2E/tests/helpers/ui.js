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

/** Открыть searchable custom-select и ввести текст в поле фильтра. */
async function filterCustomSelect(page, wrapperId, query) {
  const wrapper = page.locator(`#${wrapperId}`);
  await wrapper.locator(".select-styled").click();
  const filter = wrapper.locator(".select-filter");
  await expect(filter).toBeVisible();
  await filter.fill(query);
}

/** Дождаться результатов серверного поиска в custom-select. */
async function waitForCustomSelectOptions(page, wrapperId, minCount = 1) {
  await expect
    .poll(async () =>
      page
        .locator(`#${wrapperId} .select-option:not(.select-message)`)
        .count()
    )
    .toBeGreaterThanOrEqual(minCount);
}

function isAdminUsersResponse(url, method) {
  return (
    method === "GET" &&
    url.includes("/api/admin/users") &&
    !url.match(/\/api\/admin\/users\/\d+(\?|$)/)
  );
}

/** Выбрать владельца через поиск по части логина (серверный поиск). */
async function selectSpaceOwnerBySearch(page, loginPart) {
  const searchResponse = page.waitForResponse(
    (r) => isAdminUsersResponse(r.url(), r.request().method()) && r.ok()
  );
  await filterCustomSelect(page, "spaceOwnerWrapper", loginPart);
  await searchResponse;
  await waitForCustomSelectOptions(page, "spaceOwnerWrapper", 1);
  const wrapper = page.locator("#spaceOwnerWrapper");
  await wrapper.locator(".select-option:not(.select-message)").first().click();
  await expect(page.locator("#spaceOwner")).not.toHaveValue("");
}

/** Выбрать первого доступного владельца при создании пространства. */
async function selectFirstSpaceOwner(page) {
  const listResponse = page.waitForResponse(
    (r) => isAdminUsersResponse(r.url(), r.request().method()) && r.ok()
  );
  const wrapper = page.locator("#spaceOwnerWrapper");
  await wrapper.locator(".select-styled").click();
  await listResponse;
  await waitForCustomSelectOptions(page, "spaceOwnerWrapper", 1);
  await wrapper.locator(".select-option:not(.select-message)").first().click();
  await expect(page.locator("#spaceOwner")).not.toHaveValue("");
}

/** Фильтр списка документов по пространству (custom-select). */
async function applyDocumentSpaceFilter(page, spaceId, spaceName) {
  await expect
    .poll(async () => page.locator("#spaceFilter-wrapper .select-option").count())
    .toBeGreaterThan(1);
  const wrapper = page.locator("#spaceFilter-wrapper");
  await wrapper.locator(".select-styled").click();
  const option = wrapper.locator(`.select-option[data-value="${spaceId}"]`);
  if ((await option.count()) > 0) {
    await option.click();
  } else if (spaceName) {
    await wrapper.locator(".select-option").filter({ hasText: spaceName }).click();
  }
  await page.locator("#applyFiltersBtn").click();
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
  filterCustomSelect,
  waitForCustomSelectOptions,
  selectSpaceOwnerBySearch,
  selectFirstSpaceOwner,
  applyDocumentSpaceFilter,
};
