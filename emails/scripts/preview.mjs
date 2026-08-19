import { readdirSync, readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { join, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderMergeLanguage } from './merge-language.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const previewDir = join(root, 'preview');
mkdirSync(previewDir, { recursive: true });

const DEFAULT_VALUES = { FNAME: 'Mari' };

const TAG = /\*\|(IF|ELSEIF|ELSE|END):?([A-Za-z0-9_]*)\|\*/g;

function autoVariants(html) {
  const variants = { default: { ...DEFAULT_VALUES } };
  const stack = [];
  for (const [, keyword, variable] of html.matchAll(TAG)) {
    if (keyword === 'IF') {
      stack.push(variable);
    } else if (keyword === 'END') {
      stack.pop();
    }
    if ((keyword === 'IF' || keyword === 'ELSEIF') && !variants[variable]) {
      const enclosing = keyword === 'IF' ? stack.slice(0, -1) : [...stack];
      variants[variable] = {
        ...DEFAULT_VALUES,
        ...Object.fromEntries(enclosing.map((v) => [v, true])),
        [variable]: true,
      };
    }
  }
  return variants;
}

function collect(dir, group) {
  if (!existsSync(dir)) {
    return [];
  }
  return readdirSync(dir)
    .filter((f) => f.endsWith('.html'))
    .map((f) => {
      const name = basename(f, '.html');
      const html = readFileSync(join(dir, f), 'utf8');
      const fixturePath = join(root, 'fixtures', `${name}.json`);
      const variants = existsSync(fixturePath)
        ? JSON.parse(readFileSync(fixturePath, 'utf8')).variants
        : autoVariants(html);
      return { name, html, variants, group };
    });
}

function language(name) {
  if (name.endsWith('_et')) {
    return 'eesti keel';
  }
  if (name.endsWith('_en')) {
    return 'English';
  }
  return 'muu';
}

const templates = [
  ...collect(join(root, 'dist'), 'Managed (MJML)'),
  ...collect(join(root, 'exported'), 'Legacy (exported from Mandrill)'),
].map((t) => ({ ...t, group: `${t.group} · ${language(t.name)}` }));

const GROUP_ORDER = [
  'Managed (MJML) · eesti keel',
  'Managed (MJML) · English',
  'Legacy (exported from Mandrill) · eesti keel',
  'Legacy (exported from Mandrill) · English',
];
const groupRank = (group) => {
  const index = GROUP_ORDER.indexOf(group);
  return index === -1 ? GROUP_ORDER.length : index;
};
templates.sort(
  (a, b) => groupRank(a.group) - groupRank(b.group) || a.name.localeCompare(b.name),
);

const cards = [];
for (const { name, html, variants, group } of templates) {
  for (const [variant, vars] of Object.entries(variants)) {
    const fileName = `${name}--${variant.replaceAll(/[^a-zA-Z0-9äöüõÄÖÜÕ-]+/g, '-')}.html`;
    writeFileSync(join(previewDir, fileName), renderMergeLanguage(html, vars));
    cards.push({ template: name, variant, fileName, group });
  }
}

const groups = {};
for (const card of cards) {
  ((groups[card.group] ??= {})[card.template] ??= []).push(card);
}

const index = `<!doctype html>
<html lang="en">
<meta charset="utf-8" />
<title>Tuleva email previews</title>
<style>
  body { font-family: system-ui, sans-serif; margin: 2rem auto; max-width: 1400px; padding: 0 1rem; }
  h1 + p { color: #555; }
  h2 { margin-top: 3rem; padding-bottom: 0.3rem; border-bottom: 2px solid #0081EE; }
  h3 { margin-top: 2rem; font-size: 1rem; }
  .variants { display: flex; flex-wrap: wrap; gap: 1rem; }
  .variant { border: 1px solid #ddd; border-radius: 8px; overflow: hidden; width: 420px; }
  .variant header { padding: 0.4rem 0.8rem; background: #f5f5f5; font-size: 0.85rem; }
  .variant iframe { width: 100%; height: 560px; border: 0; }
  nav { display: flex; flex-wrap: wrap; gap: 2.5rem; font-size: 0.85rem; margin-top: 1rem; }
  nav h4 { margin: 0 0 0.4rem; font-size: 0.85rem; }
  nav a { display: block; padding: 0.1rem 0; }
</style>
<h1>Tuleva email previews</h1>
<p>Every template, every content branch, rendered with sample data. Click a title to open full size.
Legacy templates get auto-detected variants; add a fixtures file to control them.</p>
<nav>
${Object.entries(
  templates.reduce((acc, t) => (((acc[t.group] ??= []).push(t)), acc), {}),
)
  .map(
    ([group, groupTemplates]) => `  <div>
    <h4>${group}</h4>
${groupTemplates.map((t) => `    <a href="#${t.name}">${t.name}</a>`).join('\n')}
  </div>`,
  )
  .join('\n')}
</nav>
${Object.entries(groups)
  .map(
    ([group, byTemplate]) => `
<h2>${group}</h2>
${Object.entries(byTemplate)
  .map(
    ([template, variants]) => `
<h3 id="${template}">${template}</h3>
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
  .join('\n')}`,
  )
  .join('\n')}
</html>
`;

writeFileSync(join(previewDir, 'index.html'), index);
console.log(`preview: ${cards.length} variants across ${templates.length} templates -> emails/preview/index.html`);
