import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderMergeLanguage } from './merge-language.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

const copy = {
  et: {
    reminder: 'Paar päeva tagasi kirjutasin sulle II&nbsp;samba tasudest.',
    pitch: 'Alustasid kogumist Tuleva madalate tasudega III&nbsp;samba fondis.',
    feesLink: 'href="https://pension.tuleva.ee/2nd-pillar-fees?utm_content=nudge_second_pillar"',
  },
  en: {
    reminder: 'A few days ago I wrote to you about your second pillar fees.',
    pitch: 'You have started saving in Tuleva’s third pillar fund',
    feesLink:
      'href="https://pension.tuleva.ee/2nd-pillar-fees?language=en&utm_content=nudge_second_pillar"',
  },
};

const text = (html) => html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');
const occurrences = (html, needle) => html.split(needle).length - 1;

for (const [language, words] of Object.entries(copy)) {
  const name = `third_pillar_suggest_second_${language}`;
  const html = () => readFileSync(join(root, 'dist', `${name}.html`), 'utf8');

  test(`${name}: after the payment arrived email, only reminds of the fee check, with one button to the fee comparison`, () => {
    const rendered = renderMergeLanguage(html(), { FNAME: 'Mari', followsPaymentArrived: true });
    assert.ok(text(rendered).includes(words.reminder), 'missing the reminder');
    assert.ok(!text(rendered).includes(words.pitch), 'must not repeat the whole pitch');
    assert.equal(occurrences(rendered, words.feesLink), 1);
    assert.ok(!rendered.includes('pension.tuleva.ee/login'), 'must not send the saver to the login page');
  });

  test(`${name}: after a mandate, makes the whole pitch, with one button to the fee comparison`, () => {
    const rendered = renderMergeLanguage(html(), { FNAME: 'Mari', followsPaymentArrived: false });
    assert.ok(text(rendered).includes(words.pitch), 'missing the pitch');
    assert.ok(!text(rendered).includes(words.reminder), 'must not remind of an email that was not sent');
    assert.equal(occurrences(rendered, words.feesLink), 1);
    assert.ok(!rendered.includes('pension.tuleva.ee/login'), 'must not send the saver to the login page');
  });
}
