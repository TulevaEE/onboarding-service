import './load-env.mjs';
import { homedir } from 'node:os';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readFile, writeFile, chmod, mkdir } from 'node:fs/promises';
import { execSync } from 'node:child_process';
import { chromium } from 'playwright';

const ENVIRONMENTS = {
  live: { appOrigin: 'https://pension.tuleva.ee', apiBase: 'https://pension.tuleva.ee/api' },
  staging: { appOrigin: 'https://staging.tuleva.ee', apiBase: 'https://staging.tuleva.ee/api' },
  local: { appOrigin: 'https://local.tuleva.ee:3000', apiBase: 'https://local.tuleva.ee:3000/api' },
};

const FEATURES = {
  dashboard: ['/account'],
  withdrawals: ['/withdrawals'],
  'second-pillar': ['/account', '/2nd-pillar-transactions', '/2nd-pillar-contributions'],
  capital: ['/capital'],
};

const VIEWPORTS = [
  { name: 'mobile', width: 375, height: 812 },
  { name: 'desktop', width: 1280, height: 900 },
];

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = (() => {
  try {
    return execSync('git rev-parse --show-toplevel', { cwd: SCRIPT_DIR }).toString().trim();
  } catch {
    return resolve(SCRIPT_DIR, '../../../..');
  }
})();

function parseArgs(argv) {
  const args = { env: 'live', headless: false, routes: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--headless') args.headless = true;
    else if (a === '--env') args.env = argv[++i];
    else if (a === '--feature') args.feature = argv[++i];
    else if (a === '--route') args.routes.push(argv[++i]);
    else if (a === '--out') args.out = argv[++i];
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

function timestamp() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
}

function routeSlug(route) {
  const slug = route.replace(/\//g, '-').replace(/^-/, '');
  return slug === '' ? 'home' : slug;
}

function pad2(n) {
  return String(n).padStart(2, '0');
}

async function extractElements(page) {
  await page.evaluate(() => window.scrollTo(0, 0));
  return page.evaluate(() => {
    const selector =
      'h1,h2,h3,h4,button,a,[data-testid],[role],input,select,textarea,label,table tr,.btn';
    const scrollX = window.scrollX;
    const scrollY = window.scrollY;
    const seen = new Set();
    const out = [];
    const nodes = document.querySelectorAll(selector);
    let i = 0;
    for (const el of nodes) {
      const rect = el.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) continue;
      const box = {
        x: Math.round(rect.left + scrollX),
        y: Math.round(rect.top + scrollY),
        width: Math.round(rect.width),
        height: Math.round(rect.height),
      };
      const key = `${box.x},${box.y},${box.width},${box.height}`;
      if (seen.has(key)) continue;
      seen.add(key);
      out.push({
        i: i++,
        tag: el.tagName.toLowerCase(),
        role: el.getAttribute('role'),
        testid: el.getAttribute('data-testid'),
        text: (el.innerText || '').trim().replace(/\s+/g, ' ').slice(0, 80),
        box,
      });
      if (out.length >= 250) break;
    }
    return out;
  });
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const envConfig = ENVIRONMENTS[args.env];
  if (!envConfig) fail(`Unknown env: env=${args.env}`);
  const { apiBase } = envConfig;
  const appOrigin = envConfig.appOrigin;

  let routes = [];
  if (args.feature) {
    if (!FEATURES[args.feature]) fail(`Unknown feature: feature=${args.feature}`);
    routes = [...FEATURES[args.feature]];
  }
  if (args.routes.length) routes = routes.concat(args.routes);
  if (!routes.length) fail('No routes: pass --feature <name> and/or --route /path');

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

  const slug = args.feature || routes.map(routeSlug).join('_');
  const outDir = args.out
    ? resolve(args.out)
    : join(REPO_ROOT, 'tmp', 'live-review', `${slug}-${timestamp()}`);
  await mkdir(outDir, { recursive: true });

  const tokenJson = JSON.stringify({
    accessToken: cache.accessToken,
    refreshToken: cache.refreshToken,
    loginMethod: cache.loginMethod,
    signingMethod: cache.loginMethod,
  });

  const browser = await chromium.launch({ headless: args.headless });
  const screenshots = [];
  const elementMaps = [];
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true });
    await context.addInitScript(
      `(() => {
        try {
          if (window.location.origin === ${JSON.stringify(appOrigin)}) {
            sessionStorage.setItem('AUTHENTICATION_CONFIGURATION_KEY', ${JSON.stringify(tokenJson)});
          }
        } catch (e) {}
      })();`,
    );
    const page = await context.newPage();

    let counter = 0;
    for (const route of routes) {
      for (const viewport of VIEWPORTS) {
        counter++;
        await page.setViewportSize({ width: viewport.width, height: viewport.height });
        try {
          await page.goto(appOrigin + route, { waitUntil: 'networkidle', timeout: 30000 });
        } catch (e) {
          console.error(`Navigation warning: route=${route}, viewport=${viewport.name}, error=${e.message}`);
        }
        await page.waitForTimeout(1500);
        const file = join(outDir, `${pad2(counter)}-${routeSlug(route)}-${viewport.name}.png`);
        await page.screenshot({ path: file, fullPage: true });
        screenshots.push(file);
        const elementsFile = file.replace(/\.png$/, '.elements.json');
        const elements = await extractElements(page);
        await writeFile(elementsFile, JSON.stringify(elements, null, 2));
        elementMaps.push(elementsFile);
      }
    }
  } finally {
    await browser.close();
  }

  console.log(`\nOutput directory: ${outDir}`);
  for (const file of screenshots) console.log(file);
  for (const file of elementMaps) console.log(file);
}

main().catch((e) => fail(`Drive failed: error=${e.message}`));
