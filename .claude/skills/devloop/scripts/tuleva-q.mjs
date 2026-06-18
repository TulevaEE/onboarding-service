import './load-env.mjs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { readFile, writeFile, chmod, mkdir } from 'node:fs/promises';

const ENVIRONMENTS = {
  live: { appOrigin: 'https://pension.tuleva.ee', apiBase: 'https://pension.tuleva.ee/api' },
  staging: { appOrigin: 'https://staging.tuleva.ee', apiBase: 'https://staging.tuleva.ee/api' },
  local: { appOrigin: 'https://local.tuleva.ee:3000', apiBase: 'https://local.tuleva.ee:3000/api' },
};

const ALLOWLIST = [
  /^\/v1\/me$/,
  /^\/v1\/me\/principal$/,
  /^\/v1\/me\/capital$/,
  /^\/v1\/me\/capital\/events$/,
  /^\/v1\/pension-account-statement$/,
  /^\/v1\/savings-account-statement$/,
  /^\/v1\/second-pillar-assets$/,
  /^\/v1\/contributions$/,
  /^\/v1\/me\/conversion$/,
  /^\/v1\/withdrawals\/eligibility$/,
  /^\/v1\/withdrawals\/fund-pension-status$/,
  /^\/v1\/applications$/,
  /^\/v1\/funds$/,
  /^\/v1\/funds\/[A-Z0-9]+\/nav$/,
  /^\/v1\/listings$/,
];

function parseArgs(argv) {
  const args = { env: 'live' };
  const rest = [];
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--env') args.env = argv[++i];
    else rest.push(a);
  }
  args.path = rest[0];
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

function fail(message) {
  console.error(message);
  process.exit(1);
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

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const envConfig = ENVIRONMENTS[args.env];
  if (!envConfig) fail(`Unknown env: env=${args.env}`);
  const { apiBase } = envConfig;

  if (!args.path) fail('Missing path argument: e.g. node tuleva-q.mjs /v1/me');

  const pathOnly = args.path.split('?')[0];
  if (!ALLOWLIST.some((re) => re.test(pathOnly))) {
    fail(`Refused: ${args.path} is not in the read-only allowlist`);
  }

  let cache = await readCache(args.env);
  if (!cache) fail(`Run: node devloop/tuleva-login.mjs --env ${args.env}`);

  const now = Math.floor(Date.now() / 1000);
  if (cache.expiresAt && now >= cache.expiresAt - 60) {
    if (cache.refreshToken) {
      const refreshed = await tryRefresh(apiBase, args.env, cache);
      if (!refreshed) fail(`Token expired and refresh failed. Run: node devloop/tuleva-login.mjs --env ${args.env}`);
      cache = refreshed;
    } else {
      fail(`Token expired. Run: node devloop/tuleva-login.mjs --env ${args.env}`);
    }
  }

  let res;
  try {
    res = await fetch(`${apiBase}${args.path}`, {
      method: 'GET',
      headers: { Authorization: `Bearer ${cache.accessToken}`, Accept: 'application/json' },
    });
  } catch (e) {
    fail(`Request failed: error=${e.message}`);
  }

  const text = await res.text();
  if (!res.ok) {
    fail(`Request failed: status=${res.status}, body=${text}`);
  }
  try {
    console.log(JSON.stringify(JSON.parse(text), null, 2));
  } catch {
    console.log(text);
  }
}

main().catch((e) => fail(`Query failed: error=${e.message}`));
