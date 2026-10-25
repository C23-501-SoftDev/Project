const playwright = require("@playwright/test");

// Legitimate API clients follow the same server-issued cookie/header contract as the browser.
// Negative CSRF tests import the raw Playwright client directly.
function withCsrf(api, baseURL) {
  return new Proxy(api, {
    get(target, property) {
      const value = target[property];
      if (["post", "put", "patch", "delete", "fetch"].includes(property)) {
        return async (url, options = {}) => {
          const method = property === "fetch" ? (options.method || "GET").toUpperCase() : property.toUpperCase();
          if (["GET", "HEAD", "OPTIONS"].includes(method)) return value.call(target, url, options);
          let state = await target.storageState();
          let token = state.cookies.find(cookie => cookie.name === "XSRF-TOKEN")?.value;
          if (!token) {
            const bootstrap = await target.get(new URL("/login", new URL(url, baseURL)).toString());
            if (!bootstrap.ok()) throw new Error(`CSRF bootstrap failed: ${bootstrap.status()}`);
            state = await target.storageState();
            token = state.cookies.find(cookie => cookie.name === "XSRF-TOKEN")?.value;
          }
          if (!token) throw new Error("Server did not issue XSRF-TOKEN");
          return value.call(target, url, {
            ...options,
            headers: { "X-XSRF-TOKEN": decodeURIComponent(token), ...options.headers },
          });
        };
      }
      return typeof value === "function" ? value.bind(target) : value;
    },
  });
}

const test = playwright.test.extend({
  request: async ({ request, baseURL }, use) => { await use(withCsrf(request, baseURL)); },
});
const request = {
  newContext: async (options = {}) => withCsrf(await playwright.request.newContext(options), options.baseURL),
};
module.exports = { ...playwright, test, request };
