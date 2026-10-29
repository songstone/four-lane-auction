import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { randomBytes } from 'node:crypto';
import { inspect } from 'node:util';
import { ApiClient, ApiError } from './api-client.mjs';

const member = { memberId: 7, email: 'seller@example.test', nickname: '시연판매자',
  role: 'USER', bidSuspended: false, warningCount: 0 };
const freshSecret = () => randomBytes(32).toString('base64url');
const freshJwt = () => `${freshSecret()}.${freshSecret()}.${freshSecret()}`;

function authResponse(token, cookie, extra = {}) {
  return Response.json({ accessToken: token, accessTokenExpiresAt: '2099-01-01T00:00:00.000Z',
    member: { ...member, ...extra } }, { headers: { 'set-cookie': `refreshToken=${cookie}; Path=/api/auth; HttpOnly; SameSite=Strict` } });
}

function apiFailure(status, code, message = 'ignored server message') {
  return Response.json({ error: { code, message } }, { status });
}

async function serverFor(t, handler) {
  const failures = [];
  const server = createServer(async (request, response) => {
    try { await handler(request, response); }
    catch (error) { failures.push(error); response.writeHead(500).end(); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(async () => {
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
    assert.deepEqual(failures, []);
  });
  return `http://127.0.0.1:${server.address().port}`;
}

function sendJson(response, status, data, cookie) {
  response.writeHead(status, { 'content-type': 'application/json',
    ...(cookie ? { 'set-cookie': `refreshToken=${cookie}; Path=/api/auth; HttpOnly; SameSite=Strict` } : {}) });
  response.end(JSON.stringify(data));
}

test('real HTTP: one refresh rotates the cookie and replays multipart with intact file bytes', async t => {
  const password = freshSecret();
  const tokenA = freshJwt(); const tokenB = freshJwt();
  const cookieA = freshSecret(); const cookieB = freshSecret();
  const upload = Buffer.from('PNG fixture content for HTTP transport');
  const posts = []; const observations = []; const requests = [];
  const base = await serverFor(t, async (request, response) => {
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    const body = Buffer.concat(chunks);
    requests.push(request.url);
    if (request.url === '/api/auth/login') {
      assert.deepEqual(JSON.parse(body), { email: member.email, password });
      sendJson(response, 200, { accessToken: tokenA, accessTokenExpiresAt: '2099-01-01T00:00:00.000Z', member }, cookieA);
    } else if (request.url === '/api/items') {
      assert.equal(request.headers.cookie, undefined, 'Refresh cookie is confined to auth endpoints');
      posts.push({ body, type: request.headers['content-type'] });
      if (posts.length === 1) {
        assert.equal(request.headers.authorization, `Bearer ${tokenA}`);
        sendJson(response, 401, { error: { code: 'UNAUTHENTICATED' } });
      } else {
        assert.equal(request.headers.authorization, `Bearer ${tokenB}`);
        sendJson(response, 201, { itemId: 12 });
      }
    } else if (request.url === '/api/auth/refresh') {
      assert.equal(request.headers.cookie, `refreshToken=${cookieA}`);
      assert.equal(request.headers.authorization, undefined);
      sendJson(response, 200, { accessToken: tokenB, accessTokenExpiresAt: '2099-01-01T00:00:00.000Z', member }, cookieB);
    } else if (request.url === '/api/auth/logout') {
      assert.equal(request.headers.cookie, `refreshToken=${cookieB}`);
      response.writeHead(204).end();
    } else assert.fail('Unexpected request');
  });
  const client = new ApiClient(base, { onResult: result => observations.push(result) });
  assert.deepEqual(await client.login({ email: member.email, password }, 'USER'), member);
  const form = new FormData();
  form.append('name', '시연 물품'); form.append('images', new Blob([upload], { type: 'image/png' }), 'sample.png');
  const result = await client.request('POST', '/api/items', { form, auth: true, expected: [201] });
  assert.deepEqual(result, { status: 201, data: { itemId: 12 }, contentType: 'application/json' });
  assert.equal(posts.length, 2);
  for (const post of posts) {
    assert.match(post.type, /^multipart\/form-data; boundary=/);
    assert.ok(post.body.includes(upload));
    assert.ok(post.body.includes(Buffer.from('filename="sample.png"')));
    assert.ok(post.body.includes(Buffer.from('시연 물품')));
  }
  const clientDisplay = inspect(client);
  await client.logout();
  assert.equal(client.member, null);
  assert.deepEqual(requests, ['/api/auth/login', '/api/items', '/api/auth/refresh', '/api/items', '/api/auth/logout']);
  assert.deepEqual(observations.map(({ status, code }) => [status, code]),
    [[200, null], [401, 'UNAUTHENTICATED'], [200, null], [201, null], [204, null]]);
  for (const secret of [password, tokenA, tokenB, cookieA, cookieB]) {
    assert.ok(!JSON.stringify({ observations, result, clientDisplay }).includes(secret));
  }
});

test('a repeated explicit 401 stops after exactly one refresh', async () => {
  const paths = [];
  const client = new ApiClient('http://localhost:8080', { fetchImpl: async url => {
    paths.push(url.pathname);
    if (url.pathname === '/api/auth/login' || url.pathname === '/api/auth/refresh') return authResponse(freshJwt(), freshSecret());
    return apiFailure(401, 'UNAUTHENTICATED');
  } });
  await client.login({ email: member.email, password: freshSecret() }, 'USER');
  await assert.rejects(client.request('POST', '/api/items', { auth: true }), { status: 401, code: 'UNAUTHENTICATED' });
  assert.deepEqual(paths, ['/api/auth/login', '/api/items', '/api/auth/refresh', '/api/items']);
});

test('different 401 codes and non-authenticated calls never refresh', async () => {
  for (const [code, auth] of [['INVALID_CREDENTIALS', true], ['UNAUTHENTICATED', false]]) {
    const paths = [];
    const client = new ApiClient('http://localhost:8080', { fetchImpl: async url => {
      paths.push(url.pathname);
      if (url.pathname === '/api/auth/login') return authResponse(freshJwt(), freshSecret());
      return apiFailure(401, code);
    } });
    await client.login({ email: member.email, password: freshSecret() }, 'USER');
    await assert.rejects(client.request('POST', '/api/items', { auth }), { status: 401, code });
    assert.deepEqual(paths, ['/api/auth/login', '/api/items']);
  }
});

test('POST and PUT 5xx or response loss are never retried', async () => {
  for (const method of ['POST', 'PUT']) {
    for (const failure of ['server', 'network', 'invalidJson']) {
      const sensitive = freshSecret(); const observations = []; let calls = 0;
      const client = new ApiClient('http://localhost:8080', { onResult: event => observations.push(event), fetchImpl: async () => {
        calls++;
        if (failure === 'server') return apiFailure(500, 'INTERNAL_ERROR', sensitive);
        if (failure === 'invalidJson') return new Response(sensitive, { headers: { 'content-type': 'application/json' } });
        throw new Error(`connection terminated after commit ${sensitive}`);
      } });
      const expected = failure === 'server' ? 'INTERNAL_ERROR' : failure === 'network' ? 'NETWORK_ERROR' : 'INVALID_RESPONSE';
      await assert.rejects(client.request(method, '/api/items', { json: { password: sensitive } }), error => {
        assert.ok(error instanceof ApiError); assert.equal(error.code, expected);
        assert.ok(!inspect(error).includes(sensitive)); assert.equal(error.cause, undefined); return true;
      });
      assert.equal(calls, 1); assert.equal(observations.length, 1);
      assert.ok(!JSON.stringify(observations).includes(sensitive));
    }
  }
});

test('refresh failure or response loss stops before replaying the original write', async () => {
  for (const failure of ['unauthorized', 'server', 'network']) {
    const paths = [];
    const client = new ApiClient('http://localhost:8080', { fetchImpl: async url => {
      paths.push(url.pathname);
      if (url.pathname === '/api/auth/login') return authResponse(freshJwt(), freshSecret());
      if (url.pathname !== '/api/auth/refresh') return apiFailure(401, 'UNAUTHENTICATED');
      if (failure === 'network') throw new Error(freshSecret());
      return apiFailure(failure === 'server' ? 500 : 401, failure === 'server' ? 'INTERNAL_ERROR' : 'UNAUTHENTICATED');
    } });
    await client.login({ email: member.email, password: freshSecret() }, 'USER');
    await assert.rejects(client.request('POST', '/api/items', { auth: true }), ApiError);
    assert.deepEqual(paths, ['/api/auth/login', '/api/items', '/api/auth/refresh']);
  }
});

test('wrong login role is rejected; logout can still revoke the newly issued cookie', async () => {
  const cookie = freshSecret(); let logoutCookie;
  const client = new ApiClient('http://localhost:8080', { fetchImpl: async (url, init) => {
    if (url.pathname === '/api/auth/login') return authResponse(freshJwt(), cookie, { role: 'ADMIN' });
    logoutCookie = init.headers.Cookie;
    return new Response(null, { status: 204 });
  } });
  await assert.rejects(client.login({ email: member.email, password: freshSecret() }, 'USER'), { code: 'INVALID_ROLE' });
  assert.equal(client.member, null);
  await client.logout();
  assert.equal(logoutCookie, `refreshToken=${cookie}`);
});

test('member copies only contract fields and logout clears all authentication after failure', async () => {
  const cookie = freshSecret(); const hidden = freshSecret(); let calls = 0;
  const client = new ApiClient('http://localhost:8080', { fetchImpl: async () => {
    calls++;
    if (calls === 1) return authResponse(freshJwt(), cookie, { privateToken: hidden });
    return apiFailure(500, 'INTERNAL_ERROR', hidden);
  } });
  const result = await client.login({ email: member.email, password: freshSecret() }, 'USER');
  assert.deepEqual(result, member); assert.ok(Object.isFrozen(result));
  assert.ok(!inspect(client).includes(cookie)); assert.ok(!JSON.stringify(client).includes(hidden));
  await assert.rejects(client.logout(), { code: 'INTERNAL_ERROR' });
  assert.equal(client.member, null);
  await assert.rejects(client.request('POST', '/api/items', { auth: true }), { code: 'NOT_LOGGED_IN' });
  await client.logout(); assert.equal(calls, 2);
});

test('redirects are refused without forwarding credentials or contacting a second destination', async t => {
  let redirectCalls = 0; let destinationCalls = 0;
  const destination = await serverFor(t, (request, response) => { destinationCalls++; response.writeHead(200).end(); });
  const base = await serverFor(t, (request, response) => {
    redirectCalls++; response.writeHead(307, { location: `${destination}/stolen` }).end();
  });
  await assert.rejects(new ApiClient(base).request('POST', '/api/auth/signup', { json: { password: freshSecret() } }),
    { status: 307, code: 'REDIRECT_REFUSED' });
  assert.equal(redirectCalls, 1); assert.equal(destinationCalls, 0);
});

test('network deadline aborts a real pending HTTP request and does not retry', async t => {
  let calls = 0;
  const base = await serverFor(t, () => { calls++; });
  const client = new ApiClient(base, { timeoutMs: 60 });
  await assert.rejects(client.request('POST', '/api/items'), { code: 'REQUEST_TIMEOUT' });
  assert.equal(calls, 1);
});

test('unsafe error strings, query values, and arbitrary paths cannot leak through reports or errors', async () => {
  const secret = freshSecret().toUpperCase(); const observations = [];
  const client = new ApiClient('http://localhost:8080', { onResult: event => observations.push(event),
    fetchImpl: async () => apiFailure(500, secret, secret) });
  for (const path of [`/api/sessions?password=${secret}`, `/api/${secret}`]) {
    await assert.rejects(client.request('GET', path), error => {
      assert.equal(error.code, 'HTTP_ERROR'); assert.ok(!inspect(error).includes(secret)); return true;
    });
  }
  assert.deepEqual(observations.map(event => event.path), ['/api/sessions', '/api/[redacted]']);
  assert.ok(!JSON.stringify(observations).includes(secret));
  assert.throws(() => new ApiClient(`http://name:${secret}@localhost:8080`), { code: 'INVALID_CONFIGURATION' });
});

test('successful HTML and malformed JSON fail safely; binary image data survives unchanged', async () => {
  const secret = freshSecret();
  for (const response of [new Response(secret, { headers: { 'content-type': 'text/html' } }),
    new Response(secret, { headers: { 'content-type': 'application/json' } })]) {
    const client = new ApiClient('http://localhost:8080', { fetchImpl: async () => response });
    await assert.rejects(client.request('GET', '/api/sessions'), { code: 'INVALID_RESPONSE' });
  }
  const bytes = Uint8Array.of(137, 80, 78, 71, 13, 10, 26, 10);
  const client = new ApiClient('http://localhost:8080', { fetchImpl: async () => new Response(bytes, { headers: { 'content-type': 'image/png' } }) });
  assert.deepEqual(await client.request('GET', '/api/images/0123456789abcdef0123456789abcdef', { binary: true }),
    { status: 200, data: bytes, contentType: 'image/png' });
});

test('signup conflict is preserved for the orchestrator without retry or server message leakage', async () => {
  const sensitive = freshSecret(); let calls = 0;
  const client = new ApiClient('http://localhost:8080', { fetchImpl: async () => { calls++; return apiFailure(409, 'EMAIL_ALREADY_EXISTS', sensitive); } });
  await assert.rejects(client.request('POST', '/api/auth/signup', { json: { email: member.email, password: sensitive }, expected: [201] }),
    error => error.code === 'EMAIL_ALREADY_EXISTS' && error.status === 409 && !inspect(error).includes(sensitive));
  assert.equal(calls, 1);
});
