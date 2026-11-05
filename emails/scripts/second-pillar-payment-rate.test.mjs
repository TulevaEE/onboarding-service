import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderMergeLanguage } from './merge-language.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

const languages = {
  et: {
    raised: 'hakkad II&nbsp;sambasse maksma',
    lowered: 'langeb sinu II&nbsp;samba sissemakse suurus',
    kept: 'Sinu II&nbsp;samba sissemakse suurus jääb samaks',
    changed: 'on muudetud',
    closing:
      'Tuleva on loodud selleks, et võimalikult palju Eesti inimesi saaks tõhusalt ja enesekindlalt oma tulevikuks koguda ja igast kõrvale pandud eurost võimalikult palju kasu saada. Kui sul tekib mõni küsimus või kõhklus, anna märku, aitame rõõmuga.',
  },
  en: {
    raised: 'you will start saving',
    lowered: 'your second pillar contribution will decrease',
    kept: 'Your second pillar contribution rate stays the same',
    changed: 'has been changed',
    closing:
      'Tuleva exists so that as many people in Estonia as possible can save for their future effectively and confidently, and get as much as possible out of every euro they set aside. If you have any questions or doubts, let us know, we are glad to help.',
  },
};

const text = (html) => html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');
const directionOf = (vars) => (vars.increased ? 'raised' : vars.decreased ? 'lowered' : 'kept');
const carriesNudge = (vars) => Object.keys(vars).some((key) => key.startsWith('suggest') && vars[key]);

for (const [language, copy] of Object.entries(languages)) {
  const name = `second_pillar_payment_rate_${language}`;
  const html = readFileSync(join(root, 'dist', `${name}.html`), 'utf8');
  const variants = Object.entries(JSON.parse(readFileSync(join(root, 'fixtures', `${name}.json`), 'utf8')).variants);
  const rendered = (vars) => text(renderMergeLanguage(html, vars));

  test(`${name}: says the rate went up, went down or stays the same, and never that an unchanged rate was changed`, () => {
    assert.deepEqual(new Set(variants.map(([, vars]) => directionOf(vars))), new Set(['raised', 'lowered', 'kept']));
    for (const [variant, vars] of variants) {
      for (const direction of ['raised', 'lowered', 'kept']) {
        assert.equal(rendered(vars).includes(copy[direction]), direction === directionOf(vars), `variant "${variant}": ${direction}`);
      }
      assert.equal(rendered(vars).includes(copy.changed), directionOf(vars) !== 'kept', `variant "${variant}"`);
    }
  });

  test(`${name}: closes on what Tuleva is for exactly when there is no nudge`, () => {
    for (const [variant, vars] of variants) {
      assert.equal(rendered(vars).includes(copy.closing), !carriesNudge(vars), `variant "${variant}"`);
    }
  });

  test(`${name}: only a raised rate is shown with a nudge, as the nudge rules decide it`, () => {
    for (const [variant, vars] of variants.filter(([, vars]) => carriesNudge(vars))) {
      assert.equal(directionOf(vars), 'raised', `variant "${variant}"`);
    }
  });
}
