import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderMergeLanguage } from './merge-language.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const DISCLAIMER_TEXT_COLOR = '#212529';

const languages = {
  et: {
    sotsmaksHref: 'https://tuleva.ee/sotsmaks',
    sotsmaksText: 'tuleva.ee/sotsmaks',
    required: [
      'Sinu avaldus II&nbsp;sambas kogumise jätkamiseks on esitatud. Alates *|contributionStartDate|* hakkavad sissemaksed toimuma automaatselt sinu palgast.',
      'Kui mõtled ümber, saad avalduse oma Tuleva kontol tühistada kuni *|cancellationDeadline|*.',
      'Kogutud raha saad kasutama hakata kuni viis aastat enne vanaduspensioniiga. Pärast taasliitumist saad raha välja võtta alles siis, kui tekib õigus väljamaksetele.',
      'Maksustamine oleneb isikuga seotud asjaoludest, sh residentsusest. Maksuseadused muutuvad ajas.',
      'II&nbsp;sambasse makstud sotsiaalmaks vähendab veidi I&nbsp;sammast.',
      'Vaata II&nbsp;samba mõju I&nbsp;sambale',
    ],
    forbidden: ['30.&nbsp;novembril', 'Mineviku tootlus', 'Tulumaksu tagasisaamiseks'],
  },
  en: {
    sotsmaksHref: 'https://tuleva.ee/en/sotsmaks',
    sotsmaksText: 'tuleva.ee/en/sotsmaks',
    required: [
      'Contributions start on *|contributionStartDate|*.',
      'If you change your mind, you can cancel the application in your Tuleva account until *|cancellationDeadline|*.',
      'You can use the money from age 60. After rejoining, you can\'t stop contributions again or withdraw the money a second time.',
      'Taxation depends on your personal circumstances, including residency. Tax laws change over time.',
      'Social tax paid into the II&nbsp;pillar slightly reduces your I&nbsp;pillar.',
      'See how the II&nbsp;pillar affects your I&nbsp;pillar at',
    ],
    forbidden: ['30&nbsp;November', 'Past performance', 'tax return'],
    alwaysFiled: 'Application for making contributions',
    filedWhenChanged: {
      paymentRateChange: 'Contribution rate change application',
      fundSelection: 'Fund selection application',
    },
  },
};

const text = (html) => html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');

for (const [language, copy] of Object.entries(languages)) {
  const name = `second_pillar_rejoin_${language}`;
  const html = readFileSync(join(root, 'dist', `${name}.html`), 'utf8');

  test(`${name}: says when contributions start, until when the application can be canceled, what rejoining commits to, and how it affects the I pillar`, () => {
    for (const sentence of copy.required) {
      assert.ok(text(html).includes(sentence), `missing: ${sentence}`);
    }
  });

  test(`${name}: carries no returns or III pillar tax refund disclaimer and no hard-coded filing deadline`, () => {
    for (const phrase of copy.forbidden) {
      assert.ok(!text(html).includes(phrase), `must not carry: ${phrase}`);
    }
  });

  test(`${name}: the social tax link is in the disclaimer text color, not the link blue`, () => {
    const links = [...html.matchAll(/<a\b[^>]*>/g)]
      .map((m) => m[0])
      .filter((tag) => tag.includes(`href="${copy.sotsmaksHref}"`));
    assert.equal(links.length, 1, `expected one link to ${copy.sotsmaksHref}`);
    assert.match(links[0], new RegExp(`style="[^"]*color: ${DISCLAIMER_TEXT_COLOR}`));
    assert.ok(html.includes(`href="${copy.sotsmaksHref}">${copy.sotsmaksText}</a>`), 'link text matches where it goes');
  });

  if (copy.filedWhenChanged) {
    test(`${name}: lists the rate and fund applications only when they were filed`, () => {
      const filed = (vars) => text(renderMergeLanguage(html, vars));
      const nothingChanged = filed({ paymentRateChange: false, fundSelection: false });
      assert.ok(nothingChanged.includes(copy.alwaysFiled), `missing: ${copy.alwaysFiled}`);
      for (const [variable, application] of Object.entries(copy.filedWhenChanged)) {
        assert.ok(filed({ [variable]: true }).includes(application), `missing: ${application}`);
        assert.ok(!nothingChanged.includes(application), `must not list: ${application}`);
      }
    });
  }

  test(`${name}: every fixture variant fills the contribution start date and the cancellation deadline`, () => {
    const fixture = JSON.parse(readFileSync(join(root, 'fixtures', `${name}.json`), 'utf8'));
    for (const [variant, vars] of Object.entries(fixture.variants)) {
      assert.ok(vars.contributionStartDate, `variant "${variant}" has no contributionStartDate`);
      assert.ok(vars.cancellationDeadline, `variant "${variant}" has no cancellationDeadline`);
    }
  });
}
