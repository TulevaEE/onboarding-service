import test from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { join, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderMergeLanguage } from './merge-language.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const distDir = join(root, 'dist');

const languages = {
  et: {
    link: 'https://pension.tuleva.ee/2nd-pillar-rejoin?utm_content=nudge_second_pillar_rejoin',
    required: [
      '94&nbsp;€ netopalgast ja 106&nbsp;€ maksudest. Sotsiaalmaksu osa vähendab veidi I&nbsp;sammast.',
      'Viimane päev avalduse esitamiseks on 30. november 2026.',
      'Pärast taasliitumist II&nbsp;sambast enam lahkuda ei saa.',
    ],
  },
  en: {
    link: 'https://pension.tuleva.ee/2nd-pillar-rejoin?language=en&utm_content=nudge_second_pillar_rejoin',
    required: [
      '106&nbsp;€ from taxes. The social tax part slightly reduces your I&nbsp;pillar.',
      'The last day to file the application is November 30, 2026.',
      'After rejoining you can no longer leave the II&nbsp;pillar.',
    ],
  },
};

const text = (html) => html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');

const chainTemplates = readdirSync(distDir)
  .filter((file) => file.endsWith('.html'))
  .map((file) => basename(file, '.html'))
  .filter((name) => readFileSync(join(distDir, `${name}.html`), 'utf8').includes('*|IF:suggestSecondPillarRejoin|*'));

test('the rejoin nudge is in every template that carries the nudge chain', () => {
  assert.equal(chainTemplates.length, 12);
});

for (const name of chainTemplates) {
  const copy = languages[name.slice(-2)];

  test(`${name}: the rejoin nudge links to the rejoin flow, gives the filing deadline and says that rejoining is for good`, () => {
    const html = readFileSync(join(distDir, `${name}.html`), 'utf8');
    const fixture = JSON.parse(readFileSync(join(root, 'fixtures', `${name}.json`), 'utf8'));
    const rejoinVariant = Object.values(fixture.variants).find((vars) => vars.suggestSecondPillarRejoin);
    const rendered = renderMergeLanguage(html, rejoinVariant);
    assert.ok(rendered.includes(copy.link), `missing link: ${copy.link}`);
    for (const sentence of copy.required) {
      assert.ok(text(rendered).includes(sentence), `missing: ${sentence}`);
    }
  });
}
