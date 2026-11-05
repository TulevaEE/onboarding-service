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
    link: 'https://tuleva.ee/ii-sambaga-taasliitumine/?utm_content=nudge_second_pillar_rejoin',
    required: [
      'Nüüd saad soovi korral uuesti ka II&nbsp;sambas koguma hakata ja suunata osa maksudest taas enda II&nbsp;samba kontole.',
      'Viimane päev avalduse esitamiseks on 30. november.',
      'Loe lähemalt',
    ],
    disclaimer: [
      'II&nbsp;sambasse makstud sotsiaalmaks vähendab veidi I&nbsp;sammast',
      'Pärast taasliitumist II&nbsp;sambast enam lahkuda ei saa.',
    ],
    forbidden: ['Sotsiaalmaksu osa', '106&nbsp;€', 'maksuvõitu saama'],
  },
  en: {
    link: 'https://tuleva.ee/en/rejoining-the-second-pillar/?utm_content=nudge_second_pillar_rejoin',
    required: [
      'You can now start saving in the II&nbsp;pillar again if you wish, and direct part of your taxes back to your own II&nbsp;pillar account.',
      'The last day to file the application is November 30.',
      'Read more',
    ],
    disclaimer: [
      'Social tax paid into the second pillar slightly reduces your first pillar',
      'After rejoining you can no longer leave the second pillar.',
    ],
    forbidden: ['The social tax part', '106&nbsp;€', 'tax win'],
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

  test(`${name}: the rejoin nudge links to the landing page and gives the filing deadline, leaves that rejoining is for good and the I pillar line to the disclaimer below the button, and the sums to the landing page`, () => {
    const html = readFileSync(join(distDir, `${name}.html`), 'utf8');
    const fixture = JSON.parse(readFileSync(join(root, 'fixtures', `${name}.json`), 'utf8'));
    const rejoinVariant = Object.values(fixture.variants).find((vars) => vars.suggestSecondPillarRejoin);
    const rendered = renderMergeLanguage(html, rejoinVariant);
    assert.ok(rendered.includes(copy.link), `missing link: ${copy.link}`);
    for (const sentence of copy.required) {
      assert.ok(text(rendered).includes(sentence), `missing: ${sentence}`);
    }
    const button = text(rendered).indexOf(copy.required.at(-1));
    const [socialTax, forGood] = copy.disclaimer.map((sentence) => text(rendered).indexOf(sentence));
    assert.ok(socialTax > button, `not below the button: ${copy.disclaimer[0]}`);
    assert.ok(forGood > socialTax, `not after the social tax line: ${copy.disclaimer[1]}`);
    for (const phrase of copy.forbidden) {
      assert.ok(!text(rendered).includes(phrase), `must not carry: ${phrase}`);
    }
  });
}
