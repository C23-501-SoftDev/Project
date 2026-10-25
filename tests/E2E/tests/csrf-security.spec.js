// Raw client intentionally bypasses the legitimate-client helper in helpers/test.js.
const { test, expect, request } = require("@playwright/test");

test("SEC-CSRF-01: login and authenticated mutations reject missing or invalid tokens", async ({ baseURL }) => {
  const api = await request.newContext({ baseURL });
  const credentials = { login: "admin", password: "admin123" };
  expect((await api.post('/api/auth/login', { data: credentials })).status()).toBe(403);
  await api.get('/login');
  const cookie = (await api.storageState()).cookies.find(c => c.name === 'XSRF-TOKEN');
  expect(cookie).toBeTruthy();
  const headers = { 'X-XSRF-TOKEN': decodeURIComponent(cookie.value) };
  expect((await api.post('/api/auth/login', { headers, data: credentials })).ok()).toBeTruthy();
  const spaceResponse = await api.post('/api/admin/spaces', { headers, data: { name: `CSRF fixture ${Date.now()}` } });
  expect(spaceResponse.status()).toBe(201);
  const space = await spaceResponse.json();
  const created = await api.post('/api/documents', { headers, data: { spaceId: space.id, title: 'CSRF document', content: 'Original' } });
  expect(created.status()).toBe(201);
  const doc = await created.json();
  const endpoint = `/api/documents/${doc.id}`;
  expect((await api.put(endpoint, { data: { content: 'Unauthorized change' } })).status()).toBe(403);
  expect((await api.put(endpoint, { headers: { 'X-XSRF-TOKEN': 'invalid' }, data: { content: 'Unauthorized change' } })).status()).toBe(403);
  expect((await (await api.get(endpoint)).json()).content).toBe('Original');
  expect((await api.put(endpoint, { headers, data: { content: 'Authorized change' } })).ok()).toBeTruthy();
  expect((await (await api.get(endpoint)).json()).content).toBe('Authorized change');
  expect((await api.post('/logout', { maxRedirects: 0 })).status()).toBe(403);
  expect((await api.post('/logout', { headers, maxRedirects: 0 })).status()).toBe(302);
  await api.dispose();
});
