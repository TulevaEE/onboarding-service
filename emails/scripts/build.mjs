import { readdirSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import mjml2html from 'mjml';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const srcDir = join(root, 'src');
const distDir = join(root, 'dist');

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
  writeFileSync(join(distDir, `${name}.html`), html);
}
console.log(`built ${templates.length} templates into dist/`);

if (failed) {
  process.exit(1);
}
