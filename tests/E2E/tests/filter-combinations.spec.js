const { test, expect } = require("@playwright/test");
const { createAuthenticatedPage } = require("./helpers/session");
const { createAdminApi, newBrowserPageFromApi, uniqueSuffix } = require("./helpers/documents");
const {
  clearUserFiltersUi,
  clearSpacesFiltersUi,
  applyUserFiltersUi,
  applySpacesFiltersUi,
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
} = require("./helpers/filters");

const USER_FILTER_MATRIX = [
  {
    name: "search by login",
    apply: (page, data) => applyUserFiltersUi(page, { search: data.guestLogin }),
    visible: (d) => [d.guestLogin],
    hidden: (d) => [d.readerLogin, d.editorLogin],
  },
  {
    name: "search by email fragment",
    apply: (page, data) => applyUserFiltersUi(page, { search: `filt_reader_${data.tag}` }),
    visible: (d) => [d.readerLogin],
    hidden: (d) => [d.guestLogin, d.editorLogin],
  },
  {
    name: "role Guest only",
    apply: (page, data) =>
      applyUserFiltersUi(page, { roles: ["Guest"], search: data.tag }),
    visible: (d) => [d.guestLogin],
    hidden: (d) => [d.readerLogin, d.editorLogin],
  },
  {
    name: "role Reader only",
    apply: (page, data) =>
      applyUserFiltersUi(page, { roles: ["Reader"], search: data.tag }),
    visible: (d) => [d.readerLogin],
    hidden: (d) => [d.guestLogin, d.editorLogin],
  },
  {
    name: "role Editor only",
    apply: (page, data) =>
      applyUserFiltersUi(page, { roles: ["Editor"], search: data.tag }),
    visible: (d) => [d.editorLogin],
    hidden: (d) => [d.guestLogin, d.readerLogin],
  },
  {
    name: "roles Reader + Editor",
    apply: (page, data) =>
      applyUserFiltersUi(page, {
        roles: ["Reader", "Editor"],
        search: data.tag,
      }),
    visible: (d) => [d.readerLogin, d.editorLogin],
    hidden: (d) => [d.guestLogin],
  },
  {
    name: "roles Guest + Reader + Editor with search",
    apply: (page, data) =>
      applyUserFiltersUi(page, {
        roles: ["Guest", "Reader", "Editor"],
        search: data.tag,
      }),
    visible: (d) => [d.guestLogin, d.readerLogin, d.editorLogin],
    hidden: () => [],
  },
  {
    name: "admin true only",
    apply: (page, data) =>
      applyUserFiltersUi(page, { admin: ["true"], search: "admin" }),
    visible: () => ["admin"],
    hidden: (d) => [d.guestLogin, d.readerLogin, d.editorLogin],
  },
  {
    name: "admin false only with search",
    apply: (page, data) =>
      applyUserFiltersUi(page, { admin: ["false"], search: data.tag }),
    visible: (d) => [d.guestLogin, d.readerLogin, d.editorLogin],
    hidden: () => ["admin"],
  },
  {
    name: "admin true + false with search",
    apply: (page, data) =>
      applyUserFiltersUi(page, { admin: ["true", "false"], search: data.tag }),
    visible: (d) => [d.guestLogin, d.readerLogin, d.editorLogin],
    hidden: () => [],
  },
  {
    name: "search + role Reader + admin false",
    apply: (page, data) =>
      applyUserFiltersUi(page, {
        search: data.tag,
        roles: ["Reader"],
        admin: ["false"],
      }),
    visible: (d) => [d.readerLogin],
    hidden: (d) => [d.guestLogin, d.editorLogin],
  },
  {
    name: "search + role Editor + admin false",
    apply: (page, data) =>
      applyUserFiltersUi(page, {
        search: data.tag,
        roles: ["Editor"],
        admin: ["false"],
      }),
    visible: (d) => [d.editorLogin],
    hidden: (d) => [d.readerLogin, d.guestLogin],
  },
  {
    name: "status active hides deleted user",
    apply: (page, data) =>
      applyUserFiltersUi(page, { status: "active", search: data.deletedLogin }),
    visible: () => [],
    hidden: (d) => [d.deletedLogin],
  },
  {
    name: "status deleted shows deleted user",
    apply: (page, data) =>
      applyUserFiltersUi(page, { status: "deleted", search: data.deletedLogin }),
    visible: (d) => [d.deletedLogin],
    hidden: () => [],
  },
  {
    name: "status all shows deleted user",
    apply: (page, data) =>
      applyUserFiltersUi(page, { status: "all", search: data.deletedLogin }),
    visible: (d) => [d.deletedLogin],
    hidden: () => [],
  },
  {
    name: "status deleted + role Guest + search",
    apply: (page, data) =>
      applyUserFiltersUi(page, {
        status: "deleted",
        roles: ["Guest"],
        search: data.deletedLogin,
      }),
    visible: (d) => [d.deletedLogin],
    hidden: (d) => [d.guestLogin, d.readerLogin],
  },
];

const SPACES_FILTER_MATRIX = [
  {
    name: "status active",
    apply: (page, data) =>
      applySpacesFiltersUi(page, { status: "active", ownerId: data.adminOwnerId }),
    visible: (d) => [d.foreignSpaceName],
    hidden: (d) => [d.deletedSpaceName],
  },
  {
    name: "status inactive",
    apply: (page) => applySpacesFiltersUi(page, { status: "inactive" }),
    visible: (d) => [d.deletedSpaceName],
    hidden: (d) => [d.ownedSpaceName],
  },
  {
    name: "status all with search",
    apply: (page, data) => applySpacesFiltersUi(page, { status: "all" }),
    visible: (d) => [d.ownedSpaceName, d.foreignSpaceName, d.deletedSpaceName],
    hidden: () => [],
  },
  {
    name: "owner filter",
    apply: (page, data) => applySpacesFiltersUi(page, { ownerId: data.ownerId }),
    visible: (d) => [d.ownedSpaceName],
    hidden: (d) => [d.foreignSpaceName],
  },
  {
    name: "status active + owner",
    apply: (page, data) =>
      applySpacesFiltersUi(page, { status: "active", ownerId: data.ownerId }),
    visible: (d) => [d.ownedSpaceName],
    hidden: (d) => [d.foreignSpaceName, d.deletedSpaceName],
  },
  {
    name: "status inactive + owner",
    apply: (page, data) =>
      applySpacesFiltersUi(page, { status: "inactive", ownerId: data.adminOwnerId }),
    visible: (d) => [d.deletedSpaceName],
    hidden: (d) => [d.ownedSpaceName],
  },
];

const DOCS_FILTER_MATRIX = [
  {
    name: "search via URL query",
    run: async (page, data) => {
      await gotoDocumentsSearch(page, data.draftTitle);
      await expectTableShows(page, "documentsTable", [data.draftTitle]);
      await expectTableHides(page, "documentsTable", [
        data.publishedTitle,
        data.otherSpaceTitle,
      ]);
    },
  },
  {
    name: "draft status only",
    run: async (page, data) => {
      await gotoDocumentsSearch(page, data.publishedTitle);
      await expectTableShows(page, "documentsTable", [data.publishedTitle]);
      await expectDocumentStatusFilterResult(page, data, {
        allowedStatuses: ["DRAFT"],
        hiddenTitles: [data.publishedTitle],
      });
    },
  },
  {
    name: "published status only",
    run: async (page, data) => {
      await gotoDocumentsSearch(page, data.draftTitle);
      await expectTableShows(page, "documentsTable", [data.draftTitle]);
      await expectDocumentStatusFilterResult(page, data, {
        allowedStatuses: ["PUBLISHED"],
        hiddenTitles: [data.draftTitle],
      });
    },
  },
  {
    name: "draft + published statuses",
    run: async (page, data) => {
      await gotoDocumentsSearch(page, data.tag);
      await expectTableShows(page, "documentsTable", [
        data.draftTitle,
        data.publishedTitle,
      ]);
    },
  },
  {
    name: "space filter",
    run: async (page, data) => {
      await gotoDocumentsSearch(page, data.tag);
      const payload = await applyDocumentSpaceFilterUi(
        page,
        data.spaceA.id,
        data.spaceA.name
      );
      const tagged = documentsForTag(payload, data.tag);
      expect(tagged.some((doc) => doc.title === data.draftTitle)).toBeTruthy();
      expect(tagged.some((doc) => doc.title === data.publishedTitle)).toBeTruthy();
      expect(tagged.some((doc) => doc.title === data.otherSpaceTitle)).toBeFalsy();
      await expectTableShows(page, "documentsTable", [
        data.draftTitle,
        data.publishedTitle,
      ]);
      await expectTableHides(page, "documentsTable", [data.otherSpaceTitle]);
    },
  },
  {
    name: "draft status + search query",
    run: async (page, data) => {
      await gotoDocumentsSearch(page, data.tag);
      await expectTableShows(page, "documentsTable", [
        data.draftTitle,
        data.publishedTitle,
      ]);
      await expectDocumentStatusFilterResult(page, data, {
        allowedStatuses: ["DRAFT"],
        hiddenTitles: [data.publishedTitle],
      });
    },
  },
];

test.describe("Users admin filter combinations (UI)", () => {
  for (const filterCase of USER_FILTER_MATRIX) {
    test(`USERS-FILT: ${filterCase.name}`, async ({ browser, request, baseURL }) => {
      const adminApi = await createAdminApi(baseURL);
      const tag = uniqueSuffix();
      const data = await seedUsersFilterDataset(adminApi, baseURL, tag);
      await adminApi.dispose();

      const { context, page } = await createAuthenticatedPage({
        browser,
        request,
        baseURL,
      });
      await page.goto("/admin/users");
      await clearUserFiltersUi(page);
      await filterCase.apply(page, data);

      const visible = filterCase.visible(data);
      const hidden = filterCase.hidden(data);
      if (visible.length > 0) {
        await expectTableShows(page, "usersTable", visible);
      } else {
        await expect(page.locator("#usersTbody")).toContainText(
          /не найдены|не найден/i
        );
      }
      if (hidden.length > 0) {
        await expectTableHides(page, "usersTable", hidden);
      }

      await clearUserFiltersUi(page);
      await expect(page.locator("#searchInput")).toHaveValue("");
      await context.close();
    });
  }
});

test.describe("Spaces admin filter combinations (UI)", () => {
  for (const filterCase of SPACES_FILTER_MATRIX) {
    test(`SPACES-FILT: ${filterCase.name}`, async ({ browser, request, baseURL }) => {
      const adminApi = await createAdminApi(baseURL);
      const tag = uniqueSuffix();
      const data = await seedSpacesFilterDataset(adminApi, baseURL, tag);
      await adminApi.dispose();

      const { context, page } = await createAuthenticatedPage({
        browser,
        request,
        baseURL,
      });
      await page.goto("/admin/spaces");
      await clearSpacesFiltersUi(page);
      await filterCase.apply(page, data);

      await expectTableShows(page, "spacesTable", filterCase.visible(data));
      if (filterCase.hidden(data).length > 0) {
        await expectTableHides(page, "spacesTable", filterCase.hidden(data));
      }

      await clearSpacesFiltersUi(page);
      await context.close();
    });
  }
});

test.describe("Documents list filter combinations (UI)", () => {
  test.describe.configure({ timeout: 60_000 });

  for (const filterCase of DOCS_FILTER_MATRIX) {
    test(`DOCS-FILT: ${filterCase.name}`, async ({ browser, baseURL }) => {
      const adminApi = await createAdminApi(baseURL);
      const tag = uniqueSuffix();
      const data = await seedDocumentsFilterDataset(adminApi, baseURL, tag);
      const { context, page } = await newBrowserPageFromApi(browser, adminApi);
      await adminApi.dispose();

      await filterCase.run(page, data);
      await context.close();
    });
  }
});
