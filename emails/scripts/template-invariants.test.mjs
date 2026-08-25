import test from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync, existsSync } from 'node:fs';
import { join, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderMergeLanguage } from './merge-language.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const distDir = join(root, 'dist');
const manifest = JSON.parse(readFileSync(join(root, 'manifest.json'), 'utf8'));

const distTemplates = readdirSync(distDir)
  .filter((f) => f.endsWith('.html'))
  .map((f) => basename(f, '.html'));

for (const name of distTemplates) {
  const html = readFileSync(join(distDir, `${name}.html`), 'utf8');

  test(`${name}: merge tags are well formed`, () => {
    const malformed = [...html.matchAll(/\*[^|*\s][A-Z]*(?:IF|ELSEIF|ELSE|END):[A-Za-z0-9_]*/g)].map(
      (m) => m[0],
    );
    assert.deepEqual(malformed, [], `malformed merge tags like *IELSEIF: ${malformed.join(', ')}`);
  });

  test(`${name}: conditionals are balanced`, () => {
    const opens = (html.match(/\*\|IF:[A-Za-z0-9_]+\|\*/g) ?? []).length;
    const ends = (html.match(/\*\|END:IF\|\*/g) ?? []).length;
    assert.equal(opens, ends, `IF count ${opens} does not match END:IF count ${ends}`);
  });

  test(`${name}: every conditional variable is covered by a fixture variant`, () => {
    const fixturePath = join(root, 'fixtures', `${name}.json`);
    assert.ok(existsSync(fixturePath), `missing fixtures/${name}.json`);
    const fixture = JSON.parse(readFileSync(fixturePath, 'utf8'));
    const variants = Object.values(fixture.variants);
    const conditionVars = new Set(
      [...html.matchAll(/\*\|(?:IF|ELSEIF):([A-Za-z0-9_]+)\|\*/g)].map((m) => m[1]),
    );
    for (const variable of conditionVars) {
      assert.ok(
        variants.some((vars) => Boolean(vars[variable])),
        `no fixture variant exercises ${variable}=true`,
      );
    }
  });
}

for (const name of distTemplates) {
  const html = readFileSync(join(distDir, `${name}.html`), 'utf8');

  test(`${name}: conditional structure is valid`, () => {
    const stack = [];
    for (const [tag, keyword] of [...html.matchAll(/\*\|(IF|ELSEIF|ELSE|END):?[A-Za-z0-9_]*\|\*/g)].map(
      (m) => [m[0], m[1]],
    )) {
      if (keyword === 'IF') {
        stack.push({ elseSeen: false });
      } else if (keyword === 'ELSEIF') {
        assert.ok(stack.length > 0, `${tag} outside of a conditional`);
        assert.ok(!stack.at(-1).elseSeen, `${tag} after *|ELSE:|* in the same conditional`);
      } else if (keyword === 'ELSE') {
        assert.ok(stack.length > 0, `${tag} outside of a conditional`);
        assert.ok(!stack.at(-1).elseSeen, `second *|ELSE:|* in the same conditional`);
        stack.at(-1).elseSeen = true;
      } else if (keyword === 'END') {
        assert.ok(stack.length > 0, `${tag} without a matching *|IF|*`);
        stack.pop();
      }
    }
    assert.equal(stack.length, 0, `${stack.length} unclosed conditional(s)`);
  });

  test(`${name}: every fixture variant renders to a fully resolved email`, () => {
    const fixture = JSON.parse(readFileSync(join(root, 'fixtures', `${name}.json`), 'utf8'));
    for (const [variant, vars] of Object.entries(fixture.variants)) {
      const rendered = renderMergeLanguage(html, vars);
      const leftover = rendered.match(/\*\|(?:IF|ELSEIF|ELSE|END):?[A-Za-z0-9_]*\|\*/g);
      assert.equal(
        leftover,
        null,
        `variant "${variant}" leaves unresolved conditionals: ${leftover?.join(', ')}`,
      );
      assert.ok(rendered.length > html.length / 3, `variant "${variant}" rendered almost nothing`);
    }
  });
}

test('manifest entries have sources, dist output, and fixtures', () => {
  for (const name of Object.keys(manifest.templates)) {
    assert.ok(existsSync(join(root, 'src', `${name}.mjml`)), `missing src/${name}.mjml`);
    assert.ok(existsSync(join(root, 'dist', `${name}.html`)), `missing dist/${name}.html`);
    assert.ok(existsSync(join(root, 'fixtures', `${name}.json`)), `missing fixtures/${name}.json`);
    const meta = manifest.templates[name];
    assert.ok(meta.subject, `${name}: manifest entry needs a subject`);
    assert.ok(meta.from_email, `${name}: manifest entry needs a from_email`);
  }
});
