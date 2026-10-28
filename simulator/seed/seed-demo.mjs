import { randomUUID, createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { parseArgs } from 'node:util';
import { ApiClient, ApiError } from './api-client.mjs';

const simulatorRoot = fileURLToPath(new URL('../', import.meta.url));
const illustrations = ['camera', 'headphones', 'keyboard', 'lamp'];
const names = ['시연용 카메라', '시연용 헤드폰', '시연용 키보드', '시연용 조명'];
const rejectReason = '시연용 반려: 상품 상태 설명을 보완해 새 출품으로 등록해 주세요.';

export class SeedError extends Error {
  constructor(code) { super(code); this.code = code; this.name = 'SeedError'; }
}
function ensure(condition, code) { if (!condition) throw new SeedError(code); }
function positiveInteger(value, code) {
  ensure(/^[1-9][0-9]*$/.test(value ?? ''), code);
  const number = Number(value);
  ensure(Number.isSafeInteger(number), code);
  return number;
}
function origin(value, code) {
  let url;
  try { url = new URL(value); } catch { throw new SeedError(code); }
  ensure(['http:', 'https:'].includes(url.protocol) && !url.username && !url.password
    && url.pathname === '/' && !url.search && !url.hash, code);
  return url.origin;
}
export function readConfig(args, env = process.env) {
  let values;
  try {
    ({ values } = parseArgs({ args, strict: true, allowPositionals: false, options: {
      lanes: { type: 'string', default: '2' },
      'lots-per-lane': { type: 'string', default: '2' },
      'starts-in-minutes': { type: 'string', default: '30' },
      output: { type: 'string' }, help: { type: 'boolean', default: false },
    } }));
  } catch { throw new SeedError('INVALID_ARGUMENTS'); }
  if (values.help) return { help: true };
  const lanes = positiveInteger(values.lanes, 'INVALID_LANES');
  ensure(lanes <= 4, 'INVALID_LANES');
  const lotsPerLane = positiveInteger(values['lots-per-lane'], 'INVALID_LOTS_PER_LANE');
  const startsInMinutes = positiveInteger(values['starts-in-minutes'], 'INVALID_STARTS_IN_MINUTES');
  ensure(Number.isSafeInteger(lanes * lotsPerLane + 1), 'INVALID_LOTS_PER_LANE');
  ensure(Number.isSafeInteger(startsInMinutes * 60_000), 'INVALID_STARTS_IN_MINUTES');
  const accounts = ['ADMIN', 'SELLER_1', 'SELLER_2', 'BIDDER_1', 'BIDDER_2'].map(key => {
    const email = env[`DEMO_${key}_EMAIL`]?.trim();
    const password = env[`DEMO_${key}_PASSWORD`];
    ensure(typeof email === 'string' && email.length <= 255 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email), 'INVALID_ACCOUNT_EMAIL');
    ensure(typeof password === 'string' && password.trim().length > 0 && [...password].length >= 8
      && [...password].length <= 64 && Buffer.byteLength(password, 'utf8') <= 72, 'INVALID_ACCOUNT_PASSWORD');
    return { key, email, password, role: key === 'ADMIN' ? 'ADMIN' : 'USER',
      nickname: key.startsWith('SELLER') ? `시연판매자${key.at(-1)}` : `시연입찰자${key.at(-1)}` };
  });
  ensure(new Set(accounts.map(a => a.email.toLowerCase())).size === accounts.length, 'DUPLICATE_ACCOUNT_EMAIL');
  return { lanes, lotsPerLane, startsInMinutes, accounts,
    apiUrl: origin(env.DEMO_API_URL ?? 'http://127.0.0.1:8080', 'INVALID_API_URL'),
    webUrl: origin(env.DEMO_WEB_URL ?? 'http://127.0.0.1:5173', 'INVALID_WEB_URL'),
    output: values.output ? resolve(values.output) : null };
}

export function scheduledTime(serverTime, minutes) {
  ensure(typeof serverTime === 'string' && /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3}Z$/.test(serverTime), 'INVALID_SERVER_TIME');
  const timestamp = Date.parse(serverTime) + minutes * 60_000;
  ensure(Number.isSafeInteger(timestamp) && timestamp <= Date.parse('9999-12-31T23:59:59.499Z'), 'INVALID_SCHEDULED_TIME');
  return new Date(timestamp).toISOString();
}
export function assertNoReservePrice(value) {
  if (!value || typeof value !== 'object') return;
  for (const [key, child] of Object.entries(value)) {
    ensure(key.replaceAll('_', '').toLowerCase() !== 'reserveprice', 'PUBLIC_RESERVE_PRICE_LEAK');
    assertNoReservePrice(child);
  }
}
function id(value) { ensure(Number.isSafeInteger(value) && value > 0, 'INVALID_RESOURCE_ID'); return value; }
function digest(bytes) { return createHash('sha256').update(bytes).digest('hex'); }

export async function ensureUser(client, account) {
  let created = true;
  try {
    await client.request('POST', '/api/auth/signup', {
      json: { email: account.email, password: account.password, nickname: account.nickname }, expected: [201],
    });
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 409 || error.code !== 'EMAIL_ALREADY_EXISTS') throw error;
    created = false;
  }
  const member = await client.login(account, 'USER');
  return { key: account.key, email: account.email, memberId: id(member.memberId), role: member.role, created };
}

/** All writes go through the existing HTTP API. Completed calls are not rolled back together. */
export async function runSeed(config, { clientFactory = (url, options) => new ApiClient(url, options),
  checkpoint = async () => {}, runId = randomUUID() } = {}) {
  const report = { schemaVersion: 1, runId, status: 'RUNNING', phase: 'preflight',
    config: { lanes: config.lanes, lotsPerLane: config.lotsPerLane, startsInMinutes: config.startsInMinutes,
      apiUrl: config.apiUrl, webUrl: config.webUrl }, accounts: [], items: [], session: null,
    requests: {}, verification: {}, cleanup: [], failure: null };
  const onResult = ({ method, path, status, code }) => {
    const key = `${method} ${path} ${status ?? 'NO_RESPONSE'} ${code ?? 'OK'}`;
    report.requests[key] = (report.requests[key] ?? 0) + 1;
  };
  const publicApi = clientFactory(config.apiUrl, { onResult });
  const clients = new Map();
  const save = () => checkpoint(report);
  try {
    // Load and validate every committed asset before the first mutation.
    const images = await Promise.all(illustrations.map(async name => {
      const bytes = await readFile(new URL(`assets/${name}.png`, import.meta.url));
      ensure(bytes.length > 33 && bytes.length <= 10_000_000
        && bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])), 'INVALID_DEMO_IMAGE');
      return { bytes, sha256: digest(bytes), name: `${name}.png` };
    }));
    await save();
    report.phase = 'admin-login';
    const admin = clientFactory(config.apiUrl, { onResult });
    clients.set('ADMIN', admin);
    const adminAccount = config.accounts.find(a => a.key === 'ADMIN');
    const administrator = await admin.login(adminAccount, 'ADMIN');
    report.accounts.push({ key: 'ADMIN', email: adminAccount.email, memberId: id(administrator.memberId), role: 'ADMIN', created: false });
    await save();
    for (const account of config.accounts.filter(a => a.key !== 'ADMIN')) {
      report.phase = `account:${account.key}`;
      const client = clientFactory(config.apiUrl, { onResult });
      clients.set(account.key, client);
      report.accounts.push(await ensureUser(client, account));
      await save();
    }
    const count = config.lanes * config.lotsPerLane;
    for (let i = 0; i <= count; i++) {
      const rejected = i === count;
      report.phase = `item:${i + 1}:register`;
      const sellerKey = `SELLER_${i % 2 + 1}`;
      const image = images[i % images.length];
      const startPrice = [50_000, 30_000, 20_000, 10_000][i % 4];
      const reserveExplicit = i % 2 === 1;
      const form = new FormData();
      const name = `${names[i % names.length]} ${runId.slice(0, 8)}-${i + 1}${rejected ? ' (반려 예시)' : ''}`;
      form.append('name', name);
      form.append('description', '실제 판매 상품이 아닌 API 시연 데이터입니다. 이미지는 이 저장소에서 직접 작성한 CC0 도형 일러스트입니다.');
      form.append('startPrice', String(startPrice));
      if (reserveExplicit) form.append('reservePrice', String(startPrice + 20_000));
      form.append('images', new Blob([image.bytes], { type: 'image/png' }), image.name);
      const { data: item } = await clients.get(sellerKey).request('POST', '/api/items', { form, auth: true, expected: [201] });
      const recorded = { itemId: id(item.itemId), sellerKey, name, startPrice,
        reserveMode: reserveExplicit ? 'EXPLICIT' : 'OMITTED', image: image.name,
        imageSha256: image.sha256, reviewStatus: 'PENDING_REVIEW' };
      report.items.push(recorded);
      await save();
      report.phase = `item:${i + 1}:${rejected ? 'reject' : 'approve'}`;
      await admin.request('POST', `/api/admin/items/${recorded.itemId}/${rejected ? 'reject' : 'approve'}`,
        { auth: true, ...(rejected ? { json: { reason: rejectReason } } : {}) });
      recorded.reviewStatus = rejected ? 'REJECTED' : 'APPROVED';
      const { data: detail } = await admin.request('GET', `/api/admin/items/${recorded.itemId}`, { auth: true });
      ensure(detail.reservePrice === startPrice + (reserveExplicit ? 20_000 : 0), 'RESERVE_DEFAULT_MISMATCH');
      ensure(detail.reviewStatus === recorded.reviewStatus, 'REVIEW_STATUS_MISMATCH');
      if (rejected) ensure(detail.rejectReason === rejectReason, 'REJECT_REASON_MISMATCH');
      await save();
    }
    report.phase = 'session:create';
    const { data: clock } = await publicApi.request('GET', '/api/sessions?status=SCHEDULED&size=1');
    const scheduledStartAt = scheduledTime(clock.serverTime, config.startsInMinutes);
    const { data: created } = await admin.request('POST', '/api/admin/sessions', {
      auth: true, expected: [201], json: { laneCount: config.lanes, scheduledStartAt },
    });
    report.session = { sessionId: id(created.session.sessionId), scheduledStartAt, referenceServerTime: clock.serverTime,
      viewerUrl: `${config.webUrl}/#/sessions/${created.session.sessionId}`,
      adminUrl: `${config.webUrl}/#/admin/sessions/${created.session.sessionId}/lineup`, lanes: [] };
    await save();
    for (let laneNo = 1; laneNo <= config.lanes; laneNo++) {
      report.phase = `lineup:${laneNo}`;
      const itemIds = report.items.slice((laneNo - 1) * config.lotsPerLane, laneNo * config.lotsPerLane).map(item => item.itemId);
      const { data: lane } = await admin.request('PUT', `/api/admin/sessions/${report.session.sessionId}/lanes/${laneNo}/lots`,
        { auth: true, json: { itemIds } });
      report.session.lanes.push({ laneNo, laneId: id(lane.laneId), itemIds,
        lotIds: lane.lots.map(lot => id(lot.lotId)) });
      await save();
    }
    report.phase = 'verify';
    const { data: administration } = await admin.request('GET', `/api/admin/sessions/${report.session.sessionId}`, { auth: true });
    const { data: visible } = await publicApi.request('GET', `/api/sessions/${report.session.sessionId}`);
    assertNoReservePrice(visible);
    ensure(administration.startable === true && administration.emptyLaneNos.length === 0, 'SESSION_NOT_STARTABLE');
    ensure(visible.session.status === 'SCHEDULED' && visible.session.laneCount === config.lanes
      && visible.lanes.length === config.lanes && administration.lanes.length === config.lanes, 'SESSION_SHAPE_MISMATCH');
    ensure(visible.session.scheduledStartAt === scheduledStartAt && Date.parse(visible.serverTime) < Date.parse(scheduledStartAt), 'SCHEDULE_ALREADY_ELAPSED');
    const timing = visible.session.timing;
    ensure(timing.firstBidWaitMs === 15000 && timing.softCloseMs === 10000
      && timing.lotGapMs === 5000 && timing.resumeMinMs === 10000, 'TIMING_DEFAULT_MISMATCH');
    let imageCount = 0;
    for (const recordedLane of report.session.lanes) {
      const lane = administration.lanes.find(value => value.laneNo === recordedLane.laneNo);
      const view = visible.lanes.find(value => value.laneNo === recordedLane.laneNo);
      ensure(lane?.laneStatus === 'READY' && lane.lots.length === config.lotsPerLane
        && view?.lineup?.length === config.lotsPerLane && view.snapshot.state.laneStatus === 'READY', 'LANE_SHAPE_MISMATCH');
      for (let index = 0; index < lane.lots.length; index++) {
        const lot = lane.lots[index];
        const preview = view.lineup[index];
        ensure(lot.seqNo === index + 1 && preview.seqNo === index + 1 && lot.status === 'PENDING'
          && lot.itemId === recordedLane.itemIds[index] && lot.lotId === recordedLane.lotIds[index]
          && preview.lotId === lot.lotId, 'LINEUP_ORDER_MISMATCH');
        const item = report.items.find(value => value.itemId === lot.itemId);
        const { data: detail } = await publicApi.request('GET', `/api/lots/${lot.lotId}`);
        assertNoReservePrice(detail);
        ensure(detail.status === 'PENDING' && detail.name === item.name && detail.startPrice === item.startPrice
          && detail.images.length === 1 && detail.images[0].sortOrder === 0, 'LOT_DETAIL_MISMATCH');
        const imagePath = detail.images[0].url;
        ensure(/^\/api\/images\/[a-f0-9]{32}$/.test(imagePath), 'INVALID_IMAGE_PATH');
        const image = await publicApi.request('GET', imagePath, { binary: true });
        ensure(image.contentType.split(';')[0] === 'image/png' && digest(image.data) === item.imageSha256, 'IMAGE_BYTES_MISMATCH');
        imageCount++;
      }
    }
    for (const sellerKey of ['SELLER_1', 'SELLER_2']) {
      const pending = new Map(report.items.filter(item => item.sellerKey === sellerKey).map(item => [item.itemId, item]));
      let cursor = null;
      const seenCursors = new Set();
      do {
        const path = `/api/me/items?size=100${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`;
        const { data: page } = await clients.get(sellerKey).request('GET', path, { auth: true });
        assertNoReservePrice(page);
        for (const item of page.items) {
          const original = pending.get(item.itemId);
          if (!original) continue;
          ensure(item.stage === (original.reviewStatus === 'REJECTED' ? 'REJECTED' : 'SCHEDULED'), 'SELLER_STAGE_MISMATCH');
          if (item.stage === 'REJECTED') ensure(item.rejectReason === rejectReason, 'REJECT_REASON_MISMATCH');
          pending.delete(item.itemId);
        }
        cursor = page.nextCursor;
        ensure(!cursor || !seenCursors.has(cursor), 'REPEATED_CURSOR');
        if (cursor) seenCursors.add(cursor);
      } while (cursor && pending.size > 0);
      ensure(pending.size === 0, 'SELLER_ITEMS_MISSING');
    }
    // Images and paginated seller checks can take time. Recheck the current
    // public state before reporting success, including concurrent re-lineup.
    const { data: finalView } = await publicApi.request('GET', `/api/sessions/${report.session.sessionId}`);
    assertNoReservePrice(finalView);
    ensure(finalView.session.status === 'SCHEDULED'
      && finalView.session.scheduledStartAt === scheduledStartAt
      && Date.parse(finalView.serverTime) < Date.parse(scheduledStartAt), 'SCHEDULE_ALREADY_ELAPSED');
    for (const lane of report.session.lanes) {
      const current = finalView.lanes.find(value => value.laneId === lane.laneId);
      ensure(current?.snapshot.state.laneStatus === 'READY'
        && current.lineup?.length === lane.lotIds.length
        && current.lineup.every((lot, index) => lot.lotId === lane.lotIds[index] && lot.seqNo === index + 1), 'LINEUP_CHANGED_DURING_VERIFICATION');
    }
    report.verification = { verifiedAt: finalView.serverTime, approvedItems: count, rejectedItems: 1, lanes: config.lanes,
      lots: count, images: imageCount, sellerStages: 'PASS', reserveDefaults: 'PASS',
      publicReserveFields: 'ABSENT', startable: true, stillFuture: true };
    report.status = 'PASS';
    report.phase = 'complete';
  } catch (error) {
    report.status = 'FAIL';
    report.failure = { phase: report.phase,
      code: error instanceof SeedError || error instanceof ApiError ? error.code : 'LOCAL_OPERATION_FAILED',
      ...(error instanceof ApiError ? { status: error.status } : {}),
      completedResourcesRemain: true };
  } finally {
    for (const [key, client] of clients) {
      try { await client.logout(); report.cleanup.push({ account: key, status: 'PASS' }); }
      catch { report.cleanup.push({ account: key, status: 'FAIL', code: 'LOGOUT_FAILED' }); }
    }
    if (report.cleanup.some(value => value.status === 'FAIL') && report.status === 'PASS') {
      report.status = 'FAIL'; report.failure = { phase: 'cleanup', code: 'LOGOUT_FAILED', completedResourcesRemain: true };
    }
    await save();
  }
  return report;
}

async function main() {
  try {
    const config = readConfig(process.argv.slice(2));
    if (config.help) {
      console.log('node --env-file=simulator/.env simulator/seed/seed-demo.mjs [--lanes 1..4] [--lots-per-lane M] [--starts-in-minutes X] [--output file.json]\nDefaults: 2 lanes, 2 lots/lane, 30 minutes. Passwords: environment or ignored .env only. See simulator/README.md.');
      return;
    }
    const runId = randomUUID();
    const output = config.output ?? resolve(simulatorRoot, 'output', `${runId}.json`);
    await mkdir(dirname(output), { recursive: true });
    // Reserve the path before making requests; never overwrite evidence of an earlier run.
    await writeFile(output, '{}\n', { flag: 'wx', mode: 0o600 });
    const report = await runSeed(config, { runId,
      checkpoint: data => writeFile(output, `${JSON.stringify(data, null, 2)}\n`, { mode: 0o600 }) });
    console.log(JSON.stringify({ status: report.status, runId, output, session: report.session,
      verification: report.verification, failure: report.failure }, null, 2));
    process.exitCode = report.status === 'PASS' ? 0 : 1;
  } catch (error) {
    console.error(JSON.stringify({ status: 'FAIL', code: error instanceof SeedError ? error.code : 'LOCAL_OPERATION_FAILED' }));
    process.exitCode = 1;
  }
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) await main();
