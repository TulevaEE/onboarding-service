import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderMergeLanguage } from './merge-language.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

const copy = {
  et: {
    secondPillarElsewhere: 'Sinu II&nbsp;sammas kogub praegu mujal.',
    alreadyHasAnAccount: 'Eraldi kontot pole vaja luua, see on sul juba olemas.',
    feesLink: 'href="https://pension.tuleva.ee/2nd-pillar-fees?utm_content=nudge_second_pillar"',
    logInLink: 'href="https://pension.tuleva.ee/login?utm_content=nudge_log_in"',
    firstPillarDisclaimer: 'II&nbsp;sambasse makstud sotsiaalmaks vähendab veidi I&nbsp;sammast',
  },
  en: {
    secondPillarElsewhere: 'Your second pillar is currently invested elsewhere.',
    alreadyHasAnAccount: 'You do not need to create a separate account, you already have one.',
    feesLink:
      'href="https://pension.tuleva.ee/2nd-pillar-fees?language=en&utm_content=nudge_second_pillar"',
    logInLink: 'href="https://pension.tuleva.ee/login?language=en&utm_content=nudge_log_in"',
    firstPillarDisclaimer: 'Social tax paid into the second pillar slightly reduces your first pillar',
  },
};

const text = (html) => html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');
const withoutAnAccount = { FNAME: 'Mari', paymentDate: '12.08.2026', hasTulevaUser: false };

for (const [language, words] of Object.entries(copy)) {
  const name = `third_pillar_payment_arrived_${language}`;
  const html = () => readFileSync(join(root, 'dist', `${name}.html`), 'utf8');

  test(`${name}: asks a saver without an account whose second pillar is elsewhere to check their fees, with one button to the fee comparison and no I pillar disclaimer`, () => {
    const rendered = renderMergeLanguage(html(), {
      ...withoutAnAccount,
      suggestSecondPillar: true,
      anyPillarSuggestion: true,
    });
    assert.ok(text(rendered).includes(words.secondPillarElsewhere), 'missing the second pillar pitch');
    assert.ok(text(rendered).includes(words.alreadyHasAnAccount), 'missing the existing account line');
    assert.ok(rendered.includes(words.feesLink), 'missing the fee comparison button');
    assert.ok(!rendered.includes(words.logInLink), 'must not also show the log in button');
    assert.ok(!text(rendered).includes(words.firstPillarDisclaimer), 'must not carry the I pillar disclaimer');
  });

  test(`${name}: only asks a saver without an account whose second pillar is not elsewhere to log in`, () => {
    const rendered = renderMergeLanguage(html(), withoutAnAccount);
    assert.ok(rendered.includes(words.logInLink), 'missing the log in button');
    assert.ok(!rendered.includes(words.feesLink), 'must not show the fee comparison button');
    assert.ok(!text(rendered).includes(words.secondPillarElsewhere), 'must not pitch the second pillar');
  });
}
