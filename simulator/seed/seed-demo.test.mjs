import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { readConfig, scheduledTime, assertNoReservePrice, ensureUser, runSeed } from './seed-demo.mjs';
import { ApiError } from './api-client.mjs';

function environment() {
  const values = {};
  for (const key of ['ADMIN', 'SELLER_1', 'SELLER_2', 'BIDDER_1', 'BIDDER_2']) {
    values[`DEMO_${key}_EMAIL`] = `${key.toLowerCase()}@example.test`;
    values[`DEMO_${key}_PASSWORD`] = randomBytes(20).toString('hex');
  }
  return values;
}
test('CLI defaults create the approved demo shape without credentials in arguments', () => {
  const config = readConfig([], environment());
  assert.deepEqual([config.lanes, config.lotsPerLane, config.startsInMinutes], [2, 2, 30]);
  assert.equal(config.accounts.length, 5);
  assert.deepEqual(readConfig(['--help'], {}), { help: true });
});
test('invalid dimensions and unknown or positional arguments fail before HTTP', () => {
  for (const args of [['--lanes', '0'], ['--lanes', '5'], ['--lots-per-lane', '1.5'],
    ['--lots-per-lane', '9007199254740991'], ['--starts-in-minutes', '0'],
    ['--starts-in-minutes', '9007199254740991'], ['--password', 'not-supported'], ['unexpected']]) {
    assert.throws(() => readConfig(args, environment()));
  }
  const config = readConfig(['--lanes', '4', '--lots-per-lane', '3', '--starts-in-minutes', '7'], environment());
  assert.deepEqual([config.lanes, config.lotsPerLane, config.startsInMinutes], [4, 3, 7]);
});
test('missing secrets, UTF-8 password overflow and duplicate identities fail without echoing values', () => {
  const env = environment();
  delete env.DEMO_ADMIN_PASSWORD;
  assert.throws(() => readConfig([], env), /INVALID_ACCOUNT_PASSWORD/);
  env.DEMO_ADMIN_PASSWORD = '한'.repeat(25);
  assert.throws(() => readConfig([], env), /INVALID_ACCOUNT_PASSWORD/);
  env.DEMO_ADMIN_PASSWORD = randomBytes(20).toString('hex');
  env.DEMO_SELLER_1_EMAIL = env.DEMO_ADMIN_EMAIL.toUpperCase();
  assert.throws(() => readConfig([], env), /DUPLICATE_ACCOUNT_EMAIL/);
});
test('base URLs cannot carry credentials, query strings, routes or unsupported protocols', () => {
  for (const value of ['https://user:secret@example.test', 'https://example.test/?token=secret',
    'https://example.test/api', 'file:///tmp/file', 'https://example.test/#secret']) {
    const env = environment(); env.DEMO_API_URL = value;
    assert.throws(() => readConfig([], env), /INVALID_API_URL/);
  }
});
test('schedule derives exactly from the supplied server time with UTC milliseconds', () => {
  assert.equal(scheduledTime('2026-10-29T01:02:03.456Z', 30), '2026-10-29T01:32:03.456Z');
  assert.throws(() => scheduledTime('2026-10-29T01:02:03', 30), /INVALID_SERVER_TIME/);
  assert.throws(() => scheduledTime('9999-12-31T23:59:00.000Z', 30), /INVALID_SCHEDULED_TIME/);
});
test('public reserve field detection descends into arrays and nested objects', () => {
  assertNoReservePrice({ lanes: [{ reserveNotMet: false, startPrice: 50000 }] });
  assert.throws(() => assertNoReservePrice({ lanes: [{ lot: { reserve_price: 70000 } }] }), /PUBLIC_RESERVE_PRICE_LEAK/);
});
test('only EMAIL_ALREADY_EXISTS is reusable and login still has to succeed', async () => {
  const account = readConfig([], environment()).accounts[1];
  let logins = 0;
  const client = {
    request: async () => { throw new ApiError(409, 'EMAIL_ALREADY_EXISTS'); },
    login: async () => { logins++; return { memberId: 42, role: 'USER' }; },
  };
  assert.deepEqual(await ensureUser(client, account), {
    key: 'SELLER_1', email: account.email, memberId: 42, role: 'USER', created: false,
  });
  client.login = async () => { throw new ApiError(401, 'INVALID_CREDENTIALS'); };
  await assert.rejects(ensureUser(client, account), error => error.code === 'INVALID_CREDENTIALS');
  client.request = async () => { throw new ApiError(500, 'INTERNAL_ERROR'); };
  await assert.rejects(ensureUser(client, account), error => error.status === 500);
  assert.equal(logins, 1);
});
test('partial failure preserves confirmed IDs, stops writes, cleans sessions and never reports PASS', async () => {
  const env = environment(); const config = readConfig([], env);
  const writes = []; const checkpoints = [];
  const clientFactory = () => ({
    login: async (account, role) => ({ memberId: config.accounts.indexOf(account) + 1, role }),
    logout: async () => ({ status: 204 }),
    request: async (method, path) => {
      writes.push(`${method} ${path}`);
      if (path === '/api/auth/signup') return { status: 201, data: {} };
      if (path === '/api/items') return { status: 201, data: { itemId: 123 } };
      throw new ApiError(500, 'INTERNAL_ERROR');
    },
  });
  const report = await runSeed(config, { clientFactory,
    checkpoint: async value => checkpoints.push(JSON.stringify(value)) });
  assert.equal(report.status, 'FAIL');
  assert.equal(report.failure.phase, 'item:1:approve');
  assert.equal(report.failure.completedResourcesRemain, true);
  assert.deepEqual(report.items.map(item => item.itemId), [123]);
  assert.equal(writes.filter(value => value === 'POST /api/items').length, 1);
  assert.equal(writes.filter(value => value.endsWith('/approve')).length, 1);
  assert.equal(report.session, null);
  assert.equal(report.cleanup.length, 5);
  for (const secret of Object.entries(env).filter(([key]) => key.endsWith('PASSWORD')).map(([, value]) => value)) {
    assert.ok(checkpoints.every(value => !value.includes(secret)));
  }
});
