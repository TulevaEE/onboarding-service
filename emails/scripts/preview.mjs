import { readdirSync, readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { join, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderMergeLanguage } from './merge-language.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const distDir = join(root, 'dist');
const fixturesDir = join(root, 'fixtures');
const previewDir = join(root, 'preview');

mkdirSync(previewDir, { recursive: true });

const cards = [];
for (const file of readdirSync(distDir).filter((f) => f.endsWith('.html'))) {
  const name = basename(file, '.html');
  const html = readFileSync(join(distDir, file), 'utf8');
  const fixturePath = join(fixturesDir, `${name}.json`);
  const fixture = existsSync(fixturePath)
    ? JSON.parse(readFileSync(fixturePath, 'utf8'))
    : { variants: { default: {} } };

  for (const [variant, vars] of Object.entries(fixture.variants)) {
    const fileName = `${name}--${variant.replaceAll(/[^a-zA-Z0-9äöüõÄÖÜÕ-]+/g, '-')}.html`;
    writeFileSync(join(previewDir, fileName), renderMergeLanguage(html, vars));
    cards.push({ template: name, variant, fileName });
  }
}

const grouped = {};
for (const card of cards) {
  (grouped[card.template] ??= []).push(card);
}

const index = `<!doctype html>
<html lang="en">
<meta charset="utf-8" />
<title>Tuleva email previews</title>
<style>
  body { font-family: system-ui, sans-serif; margin: 2rem auto; max-width: 1400px; padding: 0 1rem; }
  h2 { margin-top: 2.5rem; font-size: 1.1rem; }
  .variants { display: flex; flex-wrap: wrap; gap: 1rem; }
  .variant { border: 1px solid #ddd; border-radius: 8px; overflow: hidden; width: 420px; }
  .variant header { padding: 0.4rem 0.8rem; background: #f5f5f5; font-size: 0.85rem; }
  .variant iframe { width: 100%; height: 560px; border: 0; }
</style>
<h1>Tuleva email previews</h1>
<p>Every template, every content branch, rendered with sample data. Click a title to open full size.</p>
${Object.entries(grouped)
  .map(
    ([template, variants]) => `
<h2>${template}</h2>
<div class="variants">
${variants
  .map(
    (v) => `  <div class="variant">
    <header><a href="${v.fileName}">${v.variant}</a></header>
    <iframe src="${v.fileName}" loading="lazy"></iframe>
  </div>`,
  )
  .join('\n')}
</div>`,
  )
  .join('\n')}
</html>
`;

writeFileSync(join(previewDir, 'index.html'), index);
console.log(`preview: ${cards.length} variants -> emails/preview/index.html`);
