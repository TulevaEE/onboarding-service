import test from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderMergeLanguage } from './merge-language.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

const copy = {
  et: {
    pitch:
      'Tasub avada ka II&nbsp;sammas. Riik suunab sinna osa sinu maksudest ja sinu sissemaksed on tulumaksuvabad. Tuleva II&nbsp;samba fond on madala tasuga indeksfond.',
    disclaimer: 'II&nbsp;sambasse makstud sotsiaalmaks vähendab veidi I&nbsp;sammast',
    anchoredOnTheLowestRate: ['2% palgast', 'kaks eurot'],
    onlyForRejoining: 'Pärast taasliitumist',
  },
  en: {
    pitch:
      "It is smart to open a second pillar as well. The state directs part of your taxes into it, and your contributions are free of income tax. Tuleva's second pillar fund is a low-fee index fund.",
    disclaimer: 'Social tax paid into the second pillar slightly reduces your first pillar',
    anchoredOnTheLowestRate: ['2% of your salary', 'two euros'],
    onlyForRejoining: 'After rejoining',
  },
};

const text = (html) => html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');

const variantsWithTheNudge = readdirSync(join(root, 'fixtures'))
  .filter((file) => file.endsWith('.json'))
  .map((file) => file.replace('.json', ''))
  .flatMap((name) =>
    Object.entries(JSON.parse(readFileSync(join(root, 'fixtures', `${name}.json`), 'utf8')).variants)
      .filter(([, vars]) => vars.suggestSecondPillarStart)
      .map(([variant, vars]) => ({ name, variant, vars })),
  );

test('the second pillar start nudge is exercised in both languages', () => {
  for (const language of Object.keys(copy)) {
    assert.ok(variantsWithTheNudge.some(({ name }) => name.endsWith(`_${language}`)), language);
  }
});

for (const { name, variant, vars } of variantsWithTheNudge) {
  const { pitch, disclaimer, anchoredOnTheLowestRate, onlyForRejoining } = copy[name.slice(-2)];

  test(`${name} "${variant}": the start nudge says where the money comes from, leaves what it costs the I pillar to the disclaimer, and does not anchor on the lowest rate`, () => {
    const rendered = text(renderMergeLanguage(readFileSync(join(root, 'dist', `${name}.html`), 'utf8'), vars));
    assert.ok(rendered.includes(pitch), `missing: ${pitch}`);
    assert.ok(rendered.includes(disclaimer), `missing: ${disclaimer}`);
    assert.ok(!rendered.includes(onlyForRejoining), `must not say: ${onlyForRejoining}`);
    for (const phrase of anchoredOnTheLowestRate) {
      assert.ok(!rendered.includes(phrase), `must not say: ${phrase}`);
    }
  });
}
