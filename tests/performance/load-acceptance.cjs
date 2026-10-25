/* US4.4.1 local acceptance. Requires explicit, verified isolation; never resets a database. */
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const { performance } = require('node:perf_hooks');

const PROFILE_NAMES = ['normal-distributed', 'normal-large-space', 'stress-large-space'];
const HTTP_ENDPOINTS = ['page', 'document', 'permissions', 'attachments', 'tree', 'headerPermissions'];
const FIXTURE_VERSION = 1;
const CONTENT_MARKER = 'Контрольная статья нагрузочной приёмки';
const CONTENT = '# ' + CONTENT_MARKER + '\n\n' + Array.from({length: 30}, (_, i) =>
  `## Раздел ${i + 1}\n\n` + 'Опубликованный документ содержит проверяемое описание процесса работы с базой знаний. '.repeat(5) +
  '\n\n- Первый шаг: прочитать требования.\n- Второй шаг: проверить результат.\n').join('\n');
const CONTENT_HASH = crypto.createHash('sha256').update(CONTENT).digest('hex');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const round = value => Math.round(value * 100) / 100;
const sqlString = value => "'" + String(value).replaceAll("'", "''") + "'";
async function withTimeout(promise, milliseconds, message) {
  let timer;
  try {
    return await Promise.race([promise, new Promise((_, reject) => {
      timer = setTimeout(() => reject(Error(message)), milliseconds);
    })]);
  } finally { clearTimeout(timer); }
}
function numberOption(value, fallback, min, max, label) {
  const number = value === undefined ? fallback : Number(value);
  if (!Number.isInteger(number) || number < min || number > max) throw Error(`Invalid ${label}: expected integer ${min}..${max}`);
  return number;
}
function configuration(env = process.env) {
  const baseURL = env.PERF_BASE_URL || 'http://127.0.0.1:18082';
  const url = new URL(baseURL);
  if (!['127.0.0.1', 'localhost'].includes(url.hostname) || url.protocol !== 'http:' || url.username || url.password || url.pathname !== '/' || url.search || url.hash)
    throw Error('PERF_BASE_URL must be an HTTP loopback origin');
  const database = env.PERF_DATABASE || 'knowledge_base_performance_test';
  if (!/^[a-zA-Z0-9_]+_test$/.test(database)) throw Error('PERF_DATABASE must be a simple name ending in _test');
  const normalUsers = numberOption(env.PERF_NORMAL_USERS, 25, 2, 100, 'normal users');
  const stressUsers = numberOption(env.PERF_STRESS_USERS, Math.max(50, normalUsers * 2), normalUsers, 200, 'stress users');
  return {
    baseURL: url.origin, database, normalUsers, stressUsers,
    durationSeconds: numberOption(env.PERF_DURATION_SECONDS, 60, 60, 3600, 'profile duration'),
    workloadVersion: 2, httpEndpoints: HTTP_ENDPOINTS,
    thinkTimeMs: 1000, browserThinkTimeMs: 0, requestTimeoutMs: 10000,
    browserNetwork: {latencyMs: 100, downloadMbps: 20, uploadMbps: 5},
    gates: {p95Ms: 2000, maxErrors: 0, minHttpSamples: 100, minBrowserNavigations: 20,
      minColdNavigations: 10, minRepeatNavigations: 10},
    databaseConnection: {host: '127.0.0.1', port: numberOption(env.PERF_DB_PORT, 55433, 1, 65535, 'DB port'),
      user: env.PERF_DB_USER || 'kb_user'},
  };
}
function quantile(values, percentile) {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.max(0, Math.ceil(percentile * sorted.length) - 1)];
}
function distribution(values) {
  return {count: values.length, minMs: values.length ? round(Math.min(...values)) : null,
    p50Ms: values.length ? round(quantile(values, .5)) : null,
    p95Ms: values.length ? round(quantile(values, .95)) : null,
    maxMs: values.length ? round(Math.max(...values)) : null};
}
function evaluateProfile(profile, config) {
  const violations = [];
  const http = profile.httpSamples;
  const browser = profile.browserSamples;
  if (profile.loadWindowMs < config.durationSeconds * 1000) violations.push('load_window_too_short');
  if (http.length < config.gates.minHttpSamples) violations.push('insufficient_http_samples');
  if (browser.length < config.gates.minBrowserNavigations) violations.push('insufficient_browser_samples');
  if (browser.filter(s => s.cache === 'cold').length < config.gates.minColdNavigations) violations.push('insufficient_cold_samples');
  if (browser.filter(s => s.cache === 'repeat').length < config.gates.minRepeatNavigations) violations.push('insufficient_repeat_samples');
  if (http.some(s => !s.ok)) violations.push('http_errors');
  if (browser.some(s => !s.ok)) violations.push('browser_errors');
  if (profile.workerFailures.length) violations.push('worker_failures');
  const byEndpoint = {};
  for (const endpoint of HTTP_ENDPOINTS) {
    byEndpoint[endpoint] = distribution(http.filter(s => s.endpoint === endpoint).map(s => s.elapsedMs));
    if (!byEndpoint[endpoint].count) violations.push(`missing_${endpoint}_samples`);
    else if (byEndpoint[endpoint].p95Ms > config.gates.p95Ms) violations.push(`p95_${endpoint}_over_2s`);
  }
  const content = distribution(browser.map(s => s.contentReadyMs ?? s.elapsedMs));
  if (content.p95Ms === null || content.p95Ms > config.gates.p95Ms) violations.push('p95_browser_content_over_2s');
  if (content.maxMs === null || content.maxMs > config.gates.p95Ms) violations.push('max_browser_content_over_2s');
  const cold = distribution(browser.filter(s => s.cache === 'cold').map(s => s.contentReadyMs ?? s.elapsedMs));
  const repeat = distribution(browser.filter(s => s.cache === 'repeat').map(s => s.contentReadyMs ?? s.elapsedMs));
  if (cold.p95Ms !== null && cold.p95Ms > config.gates.p95Ms) violations.push('p95_browser_cold_over_2s');
  if (repeat.p95Ms !== null && repeat.p95Ms > config.gates.p95Ms) violations.push('p95_browser_repeat_over_2s');
  const contentHttpChain = distribution(profile.cycles.map(s => s.contentChainMs));
  if (contentHttpChain.p95Ms === null || contentHttpChain.p95Ms > config.gates.p95Ms)
    violations.push('p95_http_content_chain_over_2s');
  return {gateApplies: !profile.name.startsWith('stress-'), passed: violations.length === 0, violations,
    httpErrors: http.filter(s => !s.ok).length, browserErrors: browser.filter(s => !s.ok).length,
    httpByEndpoint: byEndpoint, browserContentReady: content, browserColdContentReady: cold,
    browserRepeatContentReady: repeat, browserTreeReady: distribution(browser.filter(s => s.treeReadyMs != null).map(s => s.treeReadyMs)),
    contentHttpChain,
    completedCycles: profile.cycles.length, cyclesPerSecond: round(profile.cycles.length / (profile.actualDurationMs / 1000)),
    requestsPerSecond: round(http.length / (profile.actualDurationMs / 1000)),
    statusCounts: http.reduce((result, s) => {const key = String(s.status ?? 'NETWORK_ERROR'); result[key] = (result[key] || 0) + 1; return result;}, {})};
}
function argsFrom(argv) {
  const args = {resume: false, seedOnly: false, validateOnly: false, profiles: [...PROFILE_NAMES]};
  for (let i = 0; i < argv.length; i++) {
    const item = argv[i];
    if (item === '--resume') args.resume = true;
    else if (item === '--seed-only') args.seedOnly = true;
    else if (item === '--validate-only') args.validateOnly = true;
    else if (['--state', '--report', '--profiles'].includes(item)) {
      const value = argv[++i]; if (!value) throw Error(`Missing ${item} value`);
      if (item === '--profiles') args.profiles = value.split(','); else args[item.slice(2)] = path.resolve(value);
    } else throw Error(`Unknown argument ${item}`);
  }
  if (args.profiles.some(name => !PROFILE_NAMES.includes(name)) || !args.profiles.length || new Set(args.profiles).size !== args.profiles.length)
    throw Error('Unknown or repeated profile');
  if (!args.validateOnly && (!args.state || (!args.seedOnly && !args.report))) throw Error('Provide --state and --report (or --seed-only)');
  return args;
}
function writeAtomic(file, data) {
  const temporary = file + '.writing';
  fs.writeFileSync(temporary, JSON.stringify(data, null, 2) + '\n', {mode: 0o600});
  fs.renameSync(temporary, file);
}
function databaseTools(config, env) {
  const psql = env.PSQL || 'psql';
  const connection = config.databaseConnection;
  const base = ['-X', '-h', connection.host, '-p', String(connection.port), '-U', connection.user, '-d', config.database,
    '-v', 'ON_ERROR_STOP=1', '-A', '-t'];
  function sql(statement) {
    try { return execFileSync(psql, [...base, '-c', statement], {encoding: 'utf8', maxBuffer: 16 * 1024 * 1024, env}).trim(); }
    catch (error) { throw Error('PostgreSQL fixture/query failed; inspect connection/schema without logging credentials: ' + String(error.stderr || '').slice(0, 1200)); }
  }
  function json(statement) { return JSON.parse(sql(`SELECT COALESCE(json_agg(q), '[]'::json) FROM (${statement}) q`)); }
  return {sql, json};
}
async function authenticatedContext(request, config, login, password) {
  const api = await request.newContext({baseURL: config.baseURL, timeout: config.requestTimeoutMs});
  await api.get('/login');
  const csrf = (await api.storageState()).cookies.find(cookie => cookie.name === 'XSRF-TOKEN');
  if (!csrf) throw Error('CSRF bootstrap cookie missing');
  const headers = {'X-XSRF-TOKEN': decodeURIComponent(csrf.value)};
  const response = await api.post('/api/auth/login', {headers, data: {login, password}, maxRedirects: 0});
  if (response.status() !== 200) { await api.dispose(); throw Error(`Login failed status=${response.status()} for fixture user`); }
  await response.dispose();
  return {api, headers};
}
async function apiJson(api, method, url, options = {}, expected = 200) {
  const response = await api[method](url, {...options, maxRedirects: 0});
  try {
    if (response.status() !== expected) throw Error(`Fixture ${method.toUpperCase()} ${url} status=${response.status()}`);
    return await response.json();
  } finally { await response.dispose(); }
}
async function seedFixtures(args, config, request, env) {
  const db = databaseTools(config, env);
  if (db.sql('SELECT current_database()') !== config.database) throw Error('Unexpected database');
  const stateExists = fs.existsSync(args.state);
  if (stateExists && !args.resume) throw Error('State exists: use --resume deliberately or choose a new file');
  if (!stateExists && args.resume) throw Error('Resume state does not exist');
  const state = stateExists ? JSON.parse(fs.readFileSync(args.state, 'utf8')) : {
    fixtureVersion: FIXTURE_VERSION, baseURL: config.baseURL, database: config.database,
    prefix: 'LOAD-' + Date.now() + '-' + crypto.randomBytes(3).toString('hex'), createdAt: new Date().toISOString(),
    contentHash: CONTENT_HASH, spaces: [], targets: [], users: [], complete: false};
  if (state.fixtureVersion !== FIXTURE_VERSION || state.baseURL !== config.baseURL || state.database !== config.database || state.contentHash !== CONTENT_HASH)
    throw Error('Fixture state/configuration mismatch');
  if (!stateExists) fs.writeFileSync(args.state, JSON.stringify(state, null, 2) + '\n', {flag: 'wx', mode: 0o600});
  const save = () => writeAtomic(args.state, state);
  const {api, headers} = await authenticatedContext(request, config, env.PERF_LOGIN || 'admin', env.PERF_PASSWORD || 'admin123');
  try {
    const me = await apiJson(api, 'get', '/api/auth/me');
    if (!Number.isInteger(me.id) || !me.isAdmin) throw Error('Fixture administration requires a test administrator');
    const probeName = state.prefix + '-isolation-probe';
    // Write only to the explicitly permitted test DB, then read it THROUGH the app before API mutations.
    let probe = db.json(`SELECT id FROM spaces WHERE name=${sqlString(probeName)}`)[0];
    if (!probe) {
      db.sql(`INSERT INTO spaces(name,description,owner_id,created_at,updated_at) VALUES (${sqlString(probeName)}, 'Local performance isolation marker', ${me.id}, now(), now())`);
      probe = db.json(`SELECT id FROM spaces WHERE name=${sqlString(probeName)}`)[0];
    }
    const fromApp = await apiJson(api, 'get', `/api/admin/spaces/${probe.id}`);
    if (fromApp.name !== probeName) throw Error('Application and PERF_DATABASE are different: refusing fixture mutation/load');
    state.isolationProbeId = probe.id; save();
    for (let i = 0; i < 51; i++) {
      const name = state.prefix + (i === 50 ? '-large' : '-space-' + String(i + 1).padStart(2, '0'));
      let found = db.json(`SELECT id,name FROM spaces WHERE name=${sqlString(name)}`)[0];
      if (!found) found = await apiJson(api, 'post', '/api/admin/spaces', {headers, data: {name, description: 'US4.4.1 isolated load fixture'}}, 201);
      if (!state.spaces.some(space => space.id === found.id)) { state.spaces.push({id: found.id, name, large: i === 50}); save(); }
      const total = i === 50 ? 5000 : 100;
      // Fake content paths are ONLY tree metadata and are never navigated to by this harness.
      db.sql(`INSERT INTO documents(title,git_file_path,status,author_id,space_id,sort_order,created_at,updated_at)
        SELECT 'Metadata ' || g, 'performance/${state.prefix}/' || ${found.id} || '/' || g || '.md', 'Published', ${me.id}, ${found.id}, g, now(), now()
        FROM generate_series(1,${total}) g WHERE NOT EXISTS
        (SELECT 1 FROM documents d WHERE d.git_file_path='performance/${state.prefix}/' || ${found.id} || '/' || g || '.md')`);
      if (i < 10 || i === 50) {
        const title = state.prefix + '-real-' + i;
        let target = db.json(`SELECT id,space_id AS "spaceId",title FROM documents WHERE space_id=${found.id} AND title=${sqlString(title)}`)[0];
        if (!target) target = await apiJson(api, 'post', '/api/documents', {headers, data: {spaceId: found.id, title, content: CONTENT}}, 201);
        const current = await apiJson(api, 'get', `/api/documents/${target.id}`);
        if (crypto.createHash('sha256').update(current.content || '').digest('hex') !== CONTENT_HASH) throw Error('Existing fixture content changed');
        if (String(current.status).toUpperCase() !== 'PUBLISHED') await apiJson(api, 'post', `/api/documents/${target.id}/publish`, {headers});
        if (!state.targets.some(doc => doc.id === target.id)) {state.targets.push({id: target.id, spaceId: found.id, title, large: i === 50}); save();}
      }
      process.stdout.write(`Fixture space ${i + 1}/51 ready\n`);
    }
    for (let i = 0; i < config.stressUsers; i++) {
      const login = state.prefix.toLowerCase() + '-reader-' + (i + 1);
      let user = db.json(`SELECT id,login,role,is_admin AS "isAdmin" FROM users WHERE login=${sqlString(login)}`)[0];
      if (!user) user = await apiJson(api, 'post', '/api/admin/users', {headers, data: {login, email: login + '@performance.test',
        password: env.PERF_READER_PASSWORD || 'PerfOnly123!', role: 'READER', isAdmin: false}}, 201);
      if (String(user.role).toUpperCase() !== 'READER' || user.isAdmin) throw Error('Load accounts must remain non-admin READERs');
      if (!state.users.some(existing => existing.id === user.id)) {state.users.push({id: user.id, login}); save();}
    }
    state.counts = db.json(`SELECT count(*)::int AS documents, count(DISTINCT space_id)::int AS spaces FROM documents WHERE space_id IN (${state.spaces.map(s => s.id).join(',')})`)[0];
    const metadataCount = Number(db.sql(`SELECT count(*) FROM documents WHERE git_file_path LIKE ${sqlString('performance/' + state.prefix + '/%')}`));
    if (metadataCount !== 10000 || state.targets.length !== 11 || state.spaces.length !== 51) throw Error('Unexpected fixture cardinality');
    state.metadataDocuments = metadataCount;
    state.markdownCharacters = CONTENT.length;
    state.markdownWords = CONTENT.split(/\s+/).length;
    state.complete = true; save();
    return state;
  } finally { await api.dispose(); }
}
async function httpSample(api, profile, endpoint, url, worker, documentId, validator) {
  const start = performance.now();
  const sample = {worker, endpoint, documentId, url, startOffsetMs: round(start - profile.monotonicStart), status: null, bytes: 0, ok: false};
  let response;
  try {
    response = await api.get(url, {maxRedirects: 0});
    sample.status = response.status();
    const body = await response.body(); sample.bytes = body.length;
    sample.ok = sample.status === 200 && validator(body);
    if (!sample.ok) sample.error = sample.status === 200 ? 'unexpected_body' : 'http_status';
  } catch (error) { sample.error = error.name === 'TimeoutError' ? 'timeout' : 'network_error'; }
  finally {
    sample.elapsedMs = round(performance.now() - start);
    profile.httpSamples.push(sample);
    if (response) await response.dispose();
  }
  return sample;
}
async function loadWorker(api, profile, targets, worker, config, deadline) {
  let iteration = 0;
  while (performance.now() < deadline) {
    const doc = targets[(worker + iteration++) % targets.length];
    const started = performance.now();
    await httpSample(api, profile, 'page', `/documents/${doc.id}`, worker, doc.id,
      body => body.includes(Buffer.from('id="documentContent"')));
    const tree = httpSample(api, profile, 'tree', `/spaces/${doc.spaceId}/tree?documentId=${doc.id}`, worker, doc.id,
      body => body.includes(Buffer.from(`data-document-id="${doc.id}"`)));
    const headerPermissions = httpSample(api, profile, 'headerPermissions', '/api/spaces?requiredAccess=WRITE&size=1', worker, doc.id,
      body => {try {const data = JSON.parse(body); return Array.isArray(data) && data.length === 0;} catch {return false;}});
    await httpSample(api, profile, 'document', `/api/documents/${doc.id}`, worker, doc.id, body => {
      try {const data = JSON.parse(body); return data.id === doc.id && data.content === CONTENT && data.contentHtml.includes(CONTENT_MARKER);} catch {return false;}
    });
    await httpSample(api, profile, 'permissions', `/api/user/permissions?spaceId=${doc.spaceId}`, worker, doc.id,
      body => {try { return JSON.parse(body).canRead === true; } catch {return false;}});
    const contentChainMs = round(performance.now() - started);
    await Promise.all([tree, headerPermissions, httpSample(api, profile, 'attachments', `/api/documents/${doc.id}/attachments`, worker, doc.id,
      body => {try {return Array.isArray(JSON.parse(body));} catch {return false;}})]);
    profile.cycles.push({worker, documentId: doc.id, startOffsetMs: round(started - profile.monotonicStart), contentChainMs,
      elapsedMs: round(performance.now() - started)});
    await sleep(config.thinkTimeMs);
  }
}
async function browserWork(browser, storageState, profile, targets, config, deadline) {
  let pair = 0;
  while (performance.now() < deadline) {
    const context = await browser.newContext({storageState, viewport: {width: 1440, height: 900}, serviceWorkers: 'block'});
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    await cdp.send('Network.enable');
    await cdp.send('Network.emulateNetworkConditions', {offline: false, latency: config.browserNetwork.latencyMs,
      downloadThroughput: config.browserNetwork.downloadMbps * 1000000 / 8,
      uploadThroughput: config.browserNetwork.uploadMbps * 1000000 / 8, connectionType: 'wifi'});
    const doc = targets[pair++ % targets.length];
    try {
      for (const cache of ['cold', 'repeat']) {
        if (performance.now() >= deadline) break;
        const requests = [];
        const errors = [];
        const inFlight = new Set();
        let lastNetworkActivity = performance.now();
        const requestPath = url => {const value = new URL(url); return value.pathname + value.search;};
        const requested = request => {inFlight.add(request); lastNetworkActivity = performance.now();};
        const finished = request => {inFlight.delete(request); lastNetworkActivity = performance.now();};
        const responses = response => requests.push({url: requestPath(response.url()), status: response.status(), resourceType: response.request().resourceType()});
        const failed = request => {finished(request); errors.push({url: requestPath(request.url()), error: request.failure()?.errorText || 'request_failed'});};
        const scriptError = error => errors.push({error: 'page_error', message: String(error.message).slice(0, 200)});
        page.on('request', requested); page.on('requestfinished', finished);
        page.on('response', responses); page.on('requestfailed', failed); page.on('pageerror', scriptError);
        // Attach before navigation; consume failures as data so an early goto failure cannot leak a rejected Promise.
        const headerResponse = page.waitForResponse(response => {
          const url = new URL(response.url());
          return url.pathname === '/api/spaces' && url.searchParams.get('requiredAccess') === 'WRITE' && url.searchParams.get('size') === '1';
        }, {timeout: config.requestTimeoutMs}).then(async response => {
          try {
            const body = await withTimeout(response.json(), config.requestTimeoutMs, 'header_permissions_body_timeout');
            return {ok: response.status() === 200 && Array.isArray(body) && body.length === 0, status: response.status()};
          } catch (error) {
            return {ok: false, status: response.status(), error: error.message === 'header_permissions_body_timeout'
              ? error.message : 'invalid_header_permissions_body'};
          }
        }).catch(() => ({ok: false, error: 'header_permissions_timeout'}));
        const started = performance.now();
        const sample = {cache, documentId: doc.id, spaceId: doc.spaceId, startOffsetMs: round(started - profile.monotonicStart),
          contentReadyMs: null, treeReadyMs: null, status: null, ok: false, requests, errors};
        try {
          const response = await page.goto(config.baseURL + `/documents/${doc.id}`, {waitUntil: 'domcontentloaded', timeout: config.requestTimeoutMs});
          sample.status = response?.status() ?? null;
          await page.locator('#documentContent h1').filter({hasText: CONTENT_MARKER}).waitFor({state: 'visible', timeout: config.requestTimeoutMs});
          // Two frames ensure the inserted content reached a paint opportunity, not just the DOM.
          await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
          sample.contentReadyMs = round(performance.now() - started);
          sample.navigation = await page.evaluate(() => {
            const n = performance.getEntriesByType('navigation')[0];
            return {ttfbMs: n.responseStart - n.requestStart, domContentLoadedMs: n.domContentLoadedEventEnd,
              responseEndMs: n.responseEnd, transferSize: n.transferSize, encodedBodySize: n.encodedBodySize};
          });
          await page.waitForFunction(id => document.getElementById('tree-' + id)?.dataset.loaded === 'true', doc.spaceId,
            {timeout: config.requestTimeoutMs});
          sample.treeReadyMs = round(performance.now() - started);
          sample.sidebarDocumentNodes = await page.locator(`#tree-${doc.spaceId} .document-item`).count();
          await page.waitForFunction(() => !document.querySelector('#attachmentList')?.textContent.includes('Загрузка вложений'),
            undefined, {timeout: config.requestTimeoutMs});
        } catch (error) { sample.error = error.name === 'TimeoutError' ? 'timeout' : String(error.message).slice(0, 200); }
        sample.headerPermissions = await headerResponse;
        if (!sample.headerPermissions.ok) errors.push({error: sample.headerPermissions.error || 'invalid_header_permissions_response'});
        // Keep listeners on both success AND failure paths until in-flight navigation requests drain.
        const drainDeadline = performance.now() + config.requestTimeoutMs;
        while (inFlight.size > 0 || performance.now() - lastNetworkActivity < 100) {
          if (performance.now() >= drainDeadline) {
            errors.push({error: 'navigation_requests_not_drained', inFlight: inFlight.size});
            break;
          }
          await sleep(10);
        }
        sample.inFlightAtEnd = inFlight.size;
        sample.ok = !sample.error && sample.status === 200 && page.url() === config.baseURL + `/documents/${doc.id}`
          && errors.length === 0 && requests.every(r => r.status >= 200 && r.status < 400);
        if (!sample.ok && !sample.error) sample.error = 'browser_response_or_script_error';
        sample.elapsedMs = round(performance.now() - started);
        profile.browserSamples.push(sample);
        page.off('request', requested); page.off('requestfinished', finished);
        page.off('response', responses); page.off('requestfailed', failed); page.off('pageerror', scriptError);
      }
    } finally { await context.close(); }
  }
}
async function runProfile(name, config, state, browser, request, env, checkpoint) {
  const users = name.startsWith('stress-') ? config.stressUsers : config.normalUsers;
  const targets = state.targets.filter(doc => doc.large === name.endsWith('large-space'));
  const sessions = [];
  try {
    for (const user of state.users.slice(0, users)) {
      const session = await authenticatedContext(request, config, user.login, env.PERF_READER_PASSWORD || 'PerfOnly123!');
      const me = await apiJson(session.api, 'get', '/api/auth/me');
      if (me.id !== user.id || String(me.role).toUpperCase() !== 'READER' || me.isAdmin) throw Error('Unexpected load user identity');
      sessions.push(session);
    }
    const profile = {name, users, httpWorkers: users - 1, browserUsers: 1, requestedDurationSeconds: config.durationSeconds,
      startedAt: new Date().toISOString(), monotonicStart: performance.now(), httpSamples: [], browserSamples: [], cycles: [], workerFailures: []};
    checkpoint(profile);
    const deadline = profile.monotonicStart + config.durationSeconds * 1000;
    const pending = sessions.slice(0, -1).map((session, index) => loadWorker(session.api, profile, targets, index, config, deadline));
    pending.push(browserWork(browser, await sessions.at(-1).api.storageState(), profile, targets, config, deadline));
    const results = await Promise.allSettled(pending);
    results.forEach((result, index) => {if (result.status === 'rejected') profile.workerFailures.push({worker: index, error: String(result.reason?.message || result.reason).slice(0, 200)});});
    profile.loadWindowMs = Math.min(performance.now() - profile.monotonicStart, config.durationSeconds * 1000);
    profile.actualDurationMs = round(performance.now() - profile.monotonicStart);
    profile.finishedAt = new Date().toISOString();
    profile.summary = evaluateProfile(profile, config);
    delete profile.monotonicStart;
    return profile;
  } finally { await Promise.all(sessions.map(session => session.api.dispose())); }
}
async function main(argv = process.argv.slice(2), env = process.env) {
  const args = argsFrom(argv);
  const config = configuration(env);
  if (args.validateOnly) {console.log(JSON.stringify({valid: true, config, profiles: args.profiles}, null, 2)); return;}
  if (env.PERF_ALLOW_TEST_DB !== '1') throw Error('Set PERF_ALLOW_TEST_DB=1 only for an isolated local test database/app');
  let lock;
  let partial;
  if (args.report) {
    if (fs.existsSync(args.report) || fs.existsSync(args.report + '.partial.json')) throw Error('Report/partial file exists; choose a new output path');
    lock = args.report + '.lock'; partial = args.report + '.partial.json';
    fs.writeFileSync(lock, String(process.pid), {flag: 'wx', mode: 0o600});
  }
  const {chromium, request} = require('../E2E/node_modules/@playwright/test');
  const report = {schemaVersion: 1, requirement: 'US4.4.1', startedAt: new Date().toISOString(), config,
    profilesRequested: args.profiles, scope: 'Local acceptance with a declared normal-load profile; not a production SLA',
    environment: {platform: process.platform, architecture: process.arch, cpuModel: os.cpus()[0]?.model,
      cpuCount: os.cpus().length, totalMemoryBytes: os.totalmem(), node: process.version,
      gitCommit: execFileSync('git', ['rev-parse', 'HEAD'], {encoding: 'utf8'}).trim(),
      workingTreeChanged: execFileSync('git', ['status', '--porcelain'], {encoding: 'utf8'}).trim().length > 0,
      runnerSha256: crypto.createHash('sha256').update(fs.readFileSync(__filename)).digest('hex')},
    profiles: [], finished: false};
  let browser;
  let currentProfile;
  let checkpointTimer;
  const checkpoint = () => {if (partial) writeAtomic(partial, {...report, inProgressProfile: currentProfile});};
  try {
    const state = await seedFixtures(args, config, request, env);
    report.environment.postgresql = databaseTools(config, env).sql('SELECT version()');
    report.fixture = {prefix: state.prefix, counts: state.counts, metadataDocuments: state.metadataDocuments,
      realDocuments: state.targets.length, contentSha256: state.contentHash, markdownCharacters: state.markdownCharacters,
      markdownWords: state.markdownWords, targets: state.targets, readerCount: state.users.length, isolationProbeId: state.isolationProbeId};
    if (args.seedOnly) {console.log(JSON.stringify({seeded: true, fixture: report.fixture})); return;}
    browser = await chromium.launch(); report.environment.browser = browser.version();
    checkpointTimer = setInterval(checkpoint, 5000);
    for (const name of args.profiles) {
      process.stdout.write(`Starting ${name}, fixed ${config.durationSeconds}s load window\n`);
      const profile = await runProfile(name, config, state, browser, request, env, value => {currentProfile = value; checkpoint();});
      report.profiles.push(profile); currentProfile = undefined; checkpoint();
      console.log(JSON.stringify({profile: name, summary: profile.summary}));
      if (name !== args.profiles.at(-1)) await sleep(5000);
    }
    report.finished = true; report.finishedAt = new Date().toISOString();
    report.acceptancePassed = PROFILE_NAMES.slice(0, 2).every(name => report.profiles.some(p => p.name === name && p.summary.passed));
    report.acceptanceScopeComplete = PROFILE_NAMES.slice(0, 2).every(name => report.profiles.some(p => p.name === name));
    checkpoint(); fs.writeFileSync(args.report, JSON.stringify(report, null, 2) + '\n', {flag: 'wx', mode: 0o600});
    process.exitCode = report.acceptancePassed ? 0 : 1;
  } catch (error) {
    report.failure = String(error.message).slice(0, 2000); checkpoint(); throw error;
  } finally {
    if (checkpointTimer) clearInterval(checkpointTimer);
    if (browser) await browser.close();
    if (lock && fs.existsSync(lock)) fs.unlinkSync(lock);
  }
}
module.exports = {configuration, quantile, distribution, evaluateProfile, argsFrom, withTimeout};
if (require.main === module) main().catch(error => {console.error(error.message); process.exitCode = 1;});
