import { execSync } from 'node:child_process';
import { readdirSync, readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { join, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import mjml2html from 'mjml';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const srcDir = join(root, 'src');
const distDir = join(root, 'dist');
const checkMode = process.argv.includes('--check');

mkdirSync(distDir, { recursive: true });

const templates = readdirSync(srcDir).filter((f) => f.endsWith('.mjml'));
let failed = false;

for (const file of templates) {
  const name = basename(file, '.mjml');
  const source = readFileSync(join(srcDir, file), 'utf8');
  const { html, errors } = mjml2html(source, {
    filePath: join(srcDir, file),
    validationLevel: 'strict',
  });
  if (errors.length > 0) {
    console.error(`${file}: ${errors.map((e) => e.formattedMessage).join('; ')}`);
    failed = true;
    continue;
  }
  const outPath = join(distDir, `${name}.html`);
  if (checkMode) {
    const existing = existsSync(outPath) ? readFileSync(outPath, 'utf8') : null;
    if (existing !== html) {
      console.error(`${name}.html is missing or stale: run "npm run build" and commit dist/`);
      failed = true;
    }
  } else {
    writeFileSync(outPath, html);
    console.log(`built dist/${name}.html`);
  }
}

if (failed) {
  process.exit(1);
}
if (checkMode) {
  console.log(`dist is up to date (${templates.length} templates)`);
}
