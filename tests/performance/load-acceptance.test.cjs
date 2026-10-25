const test = require('node:test');
const assert = require('node:assert/strict');
const {configuration, quantile, evaluateProfile, argsFrom, withTimeout} = require('./load-acceptance.cjs');

function passingProfile() {
  return {name: 'normal-distributed', loadWindowMs: 60000, actualDurationMs: 61000, workerFailures: [],
    httpSamples: Array.from({length: 100}, (_, i) => ({endpoint: ['page', 'document', 'permissions', 'attachments', 'tree', 'headerPermissions'][i % 6],
      elapsedMs: 100, status: 200, ok: true})),
    browserSamples: Array.from({length: 20}, (_, i) => ({cache: i % 2 ? 'repeat' : 'cold', contentReadyMs: 100,
      elapsedMs: 200, treeReadyMs: 200, ok: true})),
    cycles: Array.from({length: 20}, () => ({contentChainMs: 300}))};
}
test('normal means the user-selected 25 accounts and stress remains separate', () => {
  const config = configuration({});
  assert.equal(config.normalUsers, 25); assert.equal(config.stressUsers, 50);
  assert.equal(config.durationSeconds, 60); assert.equal(config.gates.p95Ms, 2000);
  assert.equal(config.browserThinkTimeMs, 0); assert.equal(config.httpEndpoints.length, 6);
  assert.equal(config.browserNetwork.latencyMs, 100);
  assert.equal(evaluateProfile(passingProfile(), config).passed, true);
  const stress = passingProfile(); stress.name = 'stress-large-space';
  stress.browserSamples[0].contentReadyMs = 5000;
  assert.equal(evaluateProfile(stress, config).gateApplies, false);
  assert.equal(evaluateProfile(stress, config).passed, false);
});
test('one slow normal browser navigation fails even when p95 still passes', () => {
  const profile = passingProfile(); profile.browserSamples[0].contentReadyMs = 2001;
  const result = evaluateProfile(profile, configuration({}));
  assert.equal(result.browserContentReady.p95Ms, 100);
  assert.equal(result.browserContentReady.maxMs, 2001);
  assert.ok(result.violations.includes('max_browser_content_over_2s'));
  assert.equal(result.passed, false);
});
test('failed HTTP response and browser timeout cannot be hidden by fast successful samples', () => {
  const profile = passingProfile(); profile.httpSamples[0] = {...profile.httpSamples[0], status: 500, ok: false};
  profile.browserSamples[0] = {...profile.browserSamples[0], contentReadyMs: null, elapsedMs: 10000, ok: false};
  const result = evaluateProfile(profile, configuration({}));
  assert.equal(result.httpErrors, 1); assert.equal(result.browserErrors, 1);
  assert.equal(result.statusCounts['500'], 1);
  assert.ok(result.violations.includes('max_browser_content_over_2s'));
});
test('a slow content HTTP chain fails even when every individual endpoint passes', () => {
  const profile = passingProfile(); profile.cycles.forEach(cycle => {cycle.contentChainMs = 2100;});
  const result = evaluateProfile(profile, configuration({}));
  assert.equal(result.httpByEndpoint.page.p95Ms, 100);
  assert.equal(result.contentHttpChain.p95Ms, 2100);
  assert.ok(result.violations.includes('p95_http_content_chain_over_2s'));
  assert.equal(result.passed, false);
});
test('header permission requests are required and keep the same endpoint latency gate', () => {
  const profile = passingProfile();
  profile.httpSamples.filter(sample => sample.endpoint === 'headerPermissions').forEach(sample => {sample.elapsedMs = 2001;});
  const result = evaluateProfile(profile, configuration({}));
  assert.ok(result.violations.includes('p95_headerPermissions_over_2s'));
  profile.httpSamples = profile.httpSamples.filter(sample => sample.endpoint !== 'headerPermissions');
  assert.ok(evaluateProfile(profile, configuration({})).violations.includes('missing_headerPermissions_samples'));
});
test('partial or insufficient profiles never satisfy acceptance', () => {
  const profile = passingProfile(); profile.loadWindowMs = 59000; profile.browserSamples.pop(); profile.httpSamples.splice(0, 5);
  const result = evaluateProfile(profile, configuration({}));
  assert.ok(result.violations.includes('load_window_too_short'));
  assert.ok(result.violations.includes('insufficient_http_samples'));
  assert.ok(result.violations.includes('insufficient_repeat_samples'));
  assert.equal(result.passed, false);
});
test('configuration rejects remote origins, non-test databases and shorter acceptance windows', () => {
  for (const env of [{PERF_BASE_URL: 'https://example.com'}, {PERF_BASE_URL: 'http://127.0.0.1:18082/path'},
    {PERF_DATABASE: 'knowledge_base'}, {PERF_DURATION_SECONDS: '5'}, {PERF_NORMAL_USERS: '1'}])
    assert.throws(() => configuration(env));
  assert.throws(() => argsFrom(['--validate-only', '--profiles', 'unknown']));
  assert.throws(() => argsFrom(['--validate-only', '--profiles', 'normal-distributed,normal-distributed']));
});
test('nearest-rank quantiles retain slow tail samples', () => {
  assert.equal(quantile([], .95), null);
  assert.equal(quantile([1, 2, 3, 4, 100], .95), 100);
  assert.equal(quantile(Array.from({length: 20}, (_, i) => i + 1), .95), 19);
});
test('an unfinished response body times out instead of hanging the profile', async () => {
  assert.equal(await withTimeout(Promise.resolve('body'), 100, 'body_timeout'), 'body');
  await assert.rejects(withTimeout(new Promise(() => {}), 5, 'body_timeout'), /body_timeout/);
  await assert.rejects(withTimeout(Promise.reject(Error('read_failed')), 100, 'body_timeout'), /read_failed/);
});
