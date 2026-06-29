import { resolve, join, dirname } from 'node:path';
import { readFile, stat, writeFile, unlink } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { chromium } from 'playwright';

const SEVERITY_COLORS = {
  blocking: '#e74c3c',
  'should-fix': '#e8a838',
  polish: '#4a90d9',
};

function colorFor(severity) {
  return SEVERITY_COLORS[severity] || SEVERITY_COLORS.polish;
}

function fail(message) {
  console.error(message);
  process.exit(1);
}

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--image') args.image = argv[++i];
    else if (a === '--out') args.out = argv[++i];
    else if (a === '--findings') args.findings = argv[++i];
  }
  return args;
}

async function readPngSize(path) {
  const handle = await readFile(path);
  if (handle.length < 24) fail(`PNG too small: image=${path}`);
  const width = handle.readUInt32BE(16);
  const height = handle.readUInt32BE(20);
  return { width, height };
}

async function loadFindings(input) {
  const trimmed = input.trim();
  if (trimmed.startsWith('[')) return JSON.parse(trimmed);
  try {
    return JSON.parse(await readFile(resolve(input), 'utf8'));
  } catch (e) {
    fail(`Findings unreadable: findings=${input}, error=${e.message}`);
  }
}

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function badgeHtml(n, color) {
  return `<span style="display:inline-flex;align-items:center;justify-content:center;width:24px;height:24px;border-radius:50%;background:${color};color:#fff;font-weight:bold;font-size:13px;">${n}</span>`;
}

function buildHtml(imageUrl, width, findings) {
  const boxes = findings
    .map((f) => {
      const color = colorFor(f.severity);
      const b = f.box;
      return `<div style="position:absolute;left:${b.x}px;top:${b.y}px;width:${b.width}px;height:${b.height}px;border:3px solid ${color};box-sizing:border-box;"></div>
<div style="position:absolute;left:${b.x - 12}px;top:${b.y - 12}px;">${badgeHtml(f.n, color)}</div>`;
    })
    .join('\n');

  const legendItems = findings.length
    ? findings
        .map((f) => {
          const color = colorFor(f.severity);
          return `<div style="display:flex;align-items:center;gap:8px;margin:4px 0;">${badgeHtml(f.n, color)}<span style="font-weight:bold;color:${color};">[${escapeHtml(f.severity)}]</span><span>${escapeHtml(f.note)}</span></div>`;
        })
        .join('\n')
    : '<div>No findings</div>';

  return `<!DOCTYPE html>
<html><head><meta charset="utf-8"><style>body{margin:0;font-family:-apple-system,Segoe UI,Roboto,sans-serif;}</style></head>
<body>
<div style="position:relative;width:${width}px;">
<img src="${imageUrl}" style="display:block;width:${width}px;">
${boxes}
</div>
<div style="background:#fff;padding:16px;width:${width}px;box-sizing:border-box;">
${legendItems}
</div>
</body></html>`;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (!args.image) fail('No image: pass --image <png>');
  if (!args.findings) fail('No findings: pass --findings <jsonPathOrInline>');

  const imagePath = resolve(args.image);
  try {
    await stat(imagePath);
  } catch {
    fail(`Image not found: image=${imagePath}`);
  }

  const out = args.out ? resolve(args.out) : imagePath.replace(/\.png$/, '.annotated.png');
  const findings = await loadFindings(args.findings);
  if (!Array.isArray(findings)) fail(`Findings must be an array: findings=${args.findings}`);

  const { width } = await readPngSize(imagePath);
  const imageUrl = pathToFileURL(imagePath).href;
  const html = buildHtml(imageUrl, width, findings);

  const htmlPath = join(dirname(imagePath), `.annotate-${process.pid}-${Date.now()}.html`);
  await writeFile(htmlPath, html, 'utf8');

  const browser = await chromium.launch({ headless: true });
  try {
    const context = await browser.newContext({ viewport: { width, height: 600 } });
    const page = await context.newPage();
    await page.goto(pathToFileURL(htmlPath).href, { waitUntil: 'load' });
    await page.waitForFunction(() => {
      const img = document.querySelector('img');
      return !img || (img.complete && img.naturalWidth > 0);
    });
    await page.screenshot({ path: out, fullPage: true });
  } finally {
    await browser.close();
    await unlink(htmlPath).catch(() => {});
  }

  console.log(out);
}

main().catch((e) => fail(`Annotate failed: error=${e.message}`));
