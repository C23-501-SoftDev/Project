const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

for (const [file, helper] of [['main.js', 'apiFetch'], ['admin-common.js', 'adminFetch']]) {
  test(`${helper} preserves CSRF and custom headers`, async () => {
    let sent;
    const context = {
      document: { cookie: 'XSRF-TOKEN=server%2Dtoken', readyState: 'loading', addEventListener() {}, querySelectorAll: () => [] },
      window: {}, console, FormData,
      MutationObserver: class { observe() {} },
      fetch: async (url, options) => {
        sent = options;
        return { ok: true, headers: { get: () => 'application/json' }, json: async () => ({}) };
      },
    };
    vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js', file), 'utf8'), context);
    await context[helper]('/api/documents', { method: 'POST', headers: { Accept: 'application/json' }, body: '{}' });
    assert.equal(sent.headers['X-XSRF-TOKEN'], 'server-token');
    assert.equal(sent.headers.Accept, 'application/json');
    assert.equal(sent.credentials, 'same-origin');
    if (helper === 'apiFetch') {
      await context[helper]('/api/documents/1/attachments', { method: 'POST', headers: { Accept: 'application/json' }, body: new FormData() });
      assert.equal(sent.headers['X-XSRF-TOKEN'], 'server-token');
      assert.equal(sent.headers['Content-Type'], undefined, 'browser must supply multipart boundary');
    }
  });
}
