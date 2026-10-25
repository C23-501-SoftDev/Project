const { test, expect } = require("./helpers/test");
const { createAdminApi, createSpace, newBrowserPageFromApi } = require("./helpers/documents");

test("SIDEBAR-01: long tree scrolls independently and stays visible beside long content", async ({ browser, baseURL }) => {
  const api = await createAdminApi(baseURL);
  await createSpace(api, baseURL, `Scroll fixture ${Date.now()}`);
  const { context, page } = await newBrowserPageFromApi(browser, api);
  await page.setViewportSize({ width: 1440, height: 800 });
  await page.goto(`${baseURL}/`);
  await page.locator(".space-button").first().waitFor();
  // Enlarge the real layout without making this CSS regression depend on fixture volume.
  await page.evaluate(() => {
    const list = document.querySelector(".space-list");
    for (let i = 0; i < 100; i++) {
      const item = document.createElement("li");
      item.className = "space-item";
      const button = document.createElement("button");
      button.className = "space-button";
      button.textContent = `Дополнительное пространство ${i}`;
      item.append(button);
      list.append(item);
    }
    const content = document.createElement("div");
    content.style.height = "2000px";
    content.textContent = "Длинный основной документ";
    document.querySelector(".main-content").append(content);
  });
  const tree = page.locator("#spaceTreeContainer");
  const initial = await page.locator(".main-content").boundingBox();
  const bounds = await tree.boundingBox();
  expect(bounds.y + bounds.height).toBeLessThanOrEqual(800);
  await tree.hover();
  await page.mouse.wheel(0, 600);
  await expect.poll(() => tree.evaluate(e => e.scrollTop)).toBeGreaterThan(100);
  expect(await page.evaluate(() => window.scrollY)).toBe(0);
  expect((await page.locator(".main-content").boundingBox()).y).toBe(initial.y);
  await expect(page.locator("#spaceSearchInput")).toBeInViewport();
  const treeScroll = await tree.evaluate(e => e.scrollTop);
  await page.mouse.move(1200, 600);
  await page.mouse.wheel(0, 500);
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(100);
  expect((await page.locator("#spacesSidebar").boundingBox()).y).toBe(64);
  expect(await tree.evaluate(e => e.scrollTop)).toBe(treeScroll);
  await expect(page.locator("#spaceSearchInput")).toBeInViewport();
  await context.close();
  await api.dispose();
});
