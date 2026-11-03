import './load-env.mjs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { mkdir, readFile, writeFile, chmod, access } from 'node:fs/promises';
import { chromium } from 'playwright';

const ENVIRONMENTS = {
  live: { appOrigin: 'https://pension.tuleva.ee', apiBase: 'https://pension.tuleva.ee/api' },
  staging: { appOrigin: 'https://staging.tuleva.ee', apiBase: 'https://staging.tuleva.ee/api' },
  local: { appOrigin: 'https://local.tuleva.ee:3000', apiBase: 'https://local.tuleva.ee:3000/api' },
};

const BASIC_AUTH =
  'Basic ' + Buffer.from('onboarding-client:onboarding-client').toString('base64');

const cookieJar = {};
function mergeCookies(res) {
  const list = res.headers.getSetCookie ? res.headers.getSetCookie() : [];
  for (const c of list) {
    const pair = c.split(';')[0];
    const eq = pair.indexOf('=');
    if (eq === -1) continue;
    cookieJar[pair.slice(0, eq).trim()] = pair.slice(eq + 1).trim();
  }
}
function cookieHeader() {
  return Object.entries(cookieJar)
    .map(([k, v]) => `${k}=${v}`)
    .join('; ');
}

function parseArgs(argv) {
  const args = { env: 'live', method: 'browser', force: false, phone: process.env.TULEVA_PHONE };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--force') args.force = true;
    else if (a === '--env') args.env = argv[++i];
    else if (a === '--method') args.method = argv[++i];
    else if (a === '--phone') args.phone = argv[++i];
    else if (a === '--code') args.code = argv[++i];
  }
  return args;
}

function tokenPath(env) {
  return join(homedir(), '.tuleva', `${env}-token.json`);
}

function decodeExp(accessToken, obtainedAt) {
  try {
    const payload = accessToken.split('.')[1];
    const json = Buffer.from(payload, 'base64url').toString('utf8');
    const exp = JSON.parse(json).exp;
    if (typeof exp === 'number') return exp;
  } catch {}
  return obtainedAt + 3600;
}

async function readCache(env) {
  try {
    return JSON.parse(await readFile(tokenPath(env), 'utf8'));
  } catch {
    return null;
  }
}

async function writeCache(env, { accessToken, refreshToken, loginMethod }) {
  const dir = join(homedir(), '.tuleva');
  await mkdir(dir, { recursive: true });
  const obtainedAt = Math.floor(Date.now() / 1000);
  const expiresAt = decodeExp(accessToken, obtainedAt);
  const cache = { env, accessToken, refreshToken, loginMethod, obtainedAt, expiresAt };
  const path = tokenPath(env);
  await writeFile(path, JSON.stringify(cache, null, 2), { mode: 0o600 });
  await chmod(path, 0o600);
  return cache;
}

function fail(message) {
  console.error(message);
  process.exit(1);
}

function localTime(epochSeconds) {
  return new Date(epochSeconds * 1000).toLocaleString();
}

async function tryRefresh(apiBase, env, cache) {
  try {
    const res = await fetch(`${apiBase}/oauth/refresh-token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refresh_token: cache.refreshToken }),
    });
    if (res.status !== 200) return null;
    const body = await res.json();
    if (!body.access_token) return null;
    return await writeCache(env, {
      accessToken: body.access_token,
      refreshToken: body.refresh_token || cache.refreshToken,
      loginMethod: cache.loginMethod,
    });
  } catch {
    return null;
  }
}

const TERMINAL_ERRORS = ['USER_REFUSED', 'TIMEOUT', 'NOT_MID_CLIENT', 'EXPIRED_TRANSACTION'];

async function pollToken(apiBase, type, authenticationHash) {
  const url = `${apiBase}/oauth/token`;
  const form = new URLSearchParams({
    grant_type: type,
    client_id: 'onboarding-client',
    authenticationHash,
  }).toString();
  const deadline = Date.now() + 60000;
  let lastBeat = Date.now();
  let loggedPending = false;
  while (Date.now() < deadline) {
    let res;
    try {
      const headers = {
        Authorization: BASIC_AUTH,
        'Content-Type': 'application/x-www-form-urlencoded',
      };
      const ch = cookieHeader();
      if (ch) headers.Cookie = ch;
      res = await fetch(url, { method: 'POST', headers, body: form });
    } catch {
      await sleep(1000);
      continue;
    }
    mergeCookies(res);
    const text = await res.text();
    let body = null;
    try {
      body = JSON.parse(text);
    } catch {}
    if (body && body.access_token) return body;
    if (process.env.DEVLOOP_DEBUG && !loggedPending) {
      console.error(
        `\n[debug] pending: status=${res.status}, keys=${body ? Object.keys(body).join(',') : 'non-json'}, body=${text.slice(0, 200)}`,
      );
      loggedPending = true;
    }
    if (TERMINAL_ERRORS.some((e) => text.includes(e))) {
      fail(`Authentication aborted: status=${res.status}, body=${text}`);
    }
    if (Date.now() - lastBeat > 3000) {
      process.stdout.write('.');
      lastBeat = Date.now();
    }
    await sleep(1000);
  }
  fail('Authentication timed out: waited=60s');
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const envConfig = ENVIRONMENTS[args.env];
  if (!envConfig) fail(`Unknown env: env=${args.env}`);
  const { apiBase } = envConfig;
  const now = Math.floor(Date.now() / 1000);

  const cache = await readCache(args.env);

  if (!args.force && cache && cache.expiresAt && now < cache.expiresAt - 60) {
    console.log(`✅ already logged in (${args.env}), expires ${localTime(cache.expiresAt)}`);
    process.exit(0);
  }

  if (!args.force && cache && cache.refreshToken) {
    const refreshed = await tryRefresh(apiBase, args.env, cache);
    if (refreshed) {
      console.log(`✅ refreshed (${args.env})`);
      process.exit(0);
    }
  }

  const written =
    args.method === 'mobile-id'
      ? await loginWithMobileId(apiBase, args)
      : await loginInBrowser(envConfig.appOrigin, args.env);
  console.log(`✅ logged in (${args.env}), expires ${localTime(written.expiresAt)}`);
}

async function loginWithMobileId(apiBase, args) {
  const code = args.code || process.env.TULEVA_ID_CODE;
  if (!code) fail('Missing personal code: pass --code or set TULEVA_ID_CODE');
  if (!args.phone) fail('Missing phone number: --phone is required for mobile-id');

  let authRes;
  try {
    authRes = await fetch(`${apiBase}/authenticate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ personalCode: code, phoneNumber: args.phone, type: 'MOBILE_ID' }),
    });
  } catch (e) {
    fail(`Authenticate request failed: error=${e.message}`);
  }
  if (!authRes.ok) {
    const body = await authRes.text();
    fail(`Authenticate failed: status=${authRes.status}, body=${body}`);
  }
  mergeCookies(authRes);
  const { challengeCode, authenticationHash } = await authRes.json();
  if (!authenticationHash) fail('Authenticate response missing authenticationHash');

  console.log(`\n  ➡️  Verification code: ${challengeCode}\n      Approve in your Mobile-ID app now…\n`);

  const tokenBody = await pollToken(apiBase, 'MOBILE_ID', authenticationHash);
  process.stdout.write('\n');
  if (!tokenBody.access_token) fail('Token response missing access_token');

  return writeCache(args.env, {
    accessToken: tokenBody.access_token,
    refreshToken: tokenBody.refresh_token,
    loginMethod: 'MOBILE_ID',
  });
}

const BROWSER_LOGIN_TIMEOUT_MS = 180000;

function browserStatePath(env) {
  return join(homedir(), '.tuleva', `${env}-browser-state.json`);
}

async function fileExists(path) {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

async function readAppAuthentication(page) {
  try {
    const raw = await page.evaluate(() => sessionStorage.getItem('AUTHENTICATION_CONFIGURATION_KEY'));
    const authentication = raw && JSON.parse(raw);
    return authentication && authentication.accessToken ? authentication : null;
  } catch {
    return null;
  }
}

async function waitForAppAuthentication(page) {
  const deadline = Date.now() + BROWSER_LOGIN_TIMEOUT_MS;
  while (Date.now() < deadline) {
    const authentication = await readAppAuthentication(page);
    if (authentication) return authentication;
    await sleep(1000);
  }
  fail(`Authentication timed out: waited=${BROWSER_LOGIN_TIMEOUT_MS / 1000}s`);
}

async function startRememberedLogin(page) {
  const continueAs = page.getByRole('button', { name: /^(Jätka kui|Continue as) / }).first();
  const remembered = await continueAs
    .waitFor({ state: 'visible', timeout: 8000 })
    .then(() => true, () => false);
  if (!remembered) return false;
  const method = (await page.getByRole('tab', { selected: true }).first().innerText()).trim();
  await continueAs.click();
  const verificationCode = page.getByText(/^\d{4}$/).first();
  const code = await verificationCode
    .waitFor({ state: 'visible', timeout: 15000 })
    .then(() => verificationCode.innerText(), () => null);
  console.log(
    code
      ? `\n  ➡️  Verification code: ${code.trim()}\n      Approve in your ${method} app now…\n`
      : `\n  ➡️  Approve the ${method} request on your phone (code shown in the browser window)…\n`,
  );
  return true;
}

async function loginInBrowser(appOrigin, env) {
  const statePath = browserStatePath(env);
  const browser = await chromium.launch({ headless: false });
  try {
    const context = await browser.newContext({
      ignoreHTTPSErrors: env === 'local',
      storageState: (await fileExists(statePath)) ? statePath : undefined,
    });
    const page = await context.newPage();
    await page.goto(`${appOrigin}/login`, { waitUntil: 'domcontentloaded', timeout: 30000 });

    if (!(await startRememberedLogin(page))) {
      console.log(
        '\n  ➡️  Log in in the browser window that opened: "Sisenen" shows the Smart-ID QR code; Mobile-ID and ID-card work too.' +
          '\n      Tick "Jäta mind meelde" so the next login is a single Smart-ID push.\n',
      );
    }

    const authentication = await waitForAppAuthentication(page);
    await mkdir(join(homedir(), '.tuleva'), { recursive: true });
    await context.storageState({ path: statePath });
    await chmod(statePath, 0o600);
    return writeCache(env, {
      accessToken: authentication.accessToken,
      refreshToken: authentication.refreshToken,
      loginMethod: authentication.loginMethod,
    });
  } finally {
    await browser.close();
  }
}

main().catch((e) => fail(`Login failed: error=${e.message}`));
