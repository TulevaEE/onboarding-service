import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const manifest = JSON.parse(readFileSync(join(root, 'manifest.json'), 'utf8'));
const key = process.env.MANDRILL_API_KEY;
const dryRun = process.argv.includes('--dry-run');

if (!key) {
  console.error('MANDRILL_API_KEY is not set');
  process.exit(1);
}

async function mandrill(method, payload) {
  const response = await fetch(`https://mandrillapp.com/api/1.0/templates/${method}.json`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ key, ...payload }),
  });
  const body = await response.json();
  if (!response.ok || body.status === 'error') {
    throw new Error(`${method} failed for ${payload.name ?? ''}: ${JSON.stringify(body)}`);
  }
  return body;
}

let changed = 0;
for (const [name, meta] of Object.entries(manifest.templates)) {
  const code = readFileSync(join(root, 'dist', `${name}.html`), 'utf8');
  const live = await mandrill('info', { name });
  const liveCode = live.publish_code ?? live.code ?? '';

  if (liveCode === code && live.publish_subject === meta.subject) {
    console.log(`unchanged: ${name}`);
    continue;
  }
  changed++;
  if (dryRun) {
    console.log(`would publish: ${name}`);
    continue;
  }
  await mandrill('update', {
    name,
    code,
    subject: meta.subject,
    from_email: meta.from_email,
    from_name: meta.from_name,
    publish: true,
  });
  console.log(`published: ${name}`);
}
console.log(changed === 0 ? 'nothing to publish' : `${changed} template(s) ${dryRun ? 'pending' : 'published'}`);
