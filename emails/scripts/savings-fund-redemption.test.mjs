import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderMergeLanguage } from './merge-language.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const manifest = JSON.parse(readFileSync(join(root, 'manifest.json'), 'utf8'));

const text = (html) => html.replace(/<[^>]+>/g, ' ').replace(/[ \t\r\n]+/g, ' ');
const html = (name) => readFileSync(join(root, 'dist', `${name}.html`), 'utf8');
const mergeTags = (name) =>
  [...new Set([...html(name).matchAll(/\*\|(?:IF:|ELSEIF:)?([A-Za-z0-9_]+)\|\*/g)].map((m) => m[1]))].sort();
const fixture = (name) => JSON.parse(readFileSync(join(root, 'fixtures', `${name}.json`), 'utf8'));

const REQUEST = {
  FNAME: 'Mari',
  iban: 'EE47 **** **** **** 5685',
  cancellationDeadline: '30.09.2025 16:00',
  payoutDeadline: '01.10.2025 16:00',
};

const REQUEST_FACTS = ['FNAME', 'amount', 'cancellationDeadline', 'fundUnits', 'iban', 'payoutDeadline'];

const copy = {
  et: {
    amounts: { fundUnits: '1\u00a0157,40741', amount: '1\u00a0250,50' },
    facts: [
      'Tere,&nbsp;Mari',
      'Võtame tagasi 1\u00a0157,40741 osakut, mis on praegu väärt umbes 1\u00a0250,50&nbsp;€. Täpne summa sõltub osakute puhasväärtusest (NAV) tehingupäeval.',
      'Kanname raha kontole EE47 **** **** **** 5685 eeldatavalt hiljemalt 01.10.2025 16:00.',
      'Üldjuhul saad avalduse Tuleva kontol tühistada kuni 30.09.2025 16:00.',
      'Kui see polnud sina, võta meiega kohe ühendust: tuleva@tuleva.ee või 644&nbsp;5100',
    ],
    request: {
      person: { unnamed: 'Saime kätte sinu avalduse Tuleva Täiendava Kogumisfondi osakute tagasivõtmiseks.' },
      child: {
        named:
          'Saime kätte avalduse Tuleva Täiendava Kogumisfondi osakute tagasivõtmiseks lapse kontolt. Osakute omanik on Jaan Tamm.',
        unnamed: 'Saime kätte avalduse Tuleva Täiendava Kogumisfondi osakute tagasivõtmiseks lapse kontolt.',
      },
      company: {
        named: 'Saime kätte avalduse Tuleva Täiendava Kogumisfondi osakute tagasivõtmiseks Mesila OÜ kontolt.',
        unnamed: 'Saime kätte avalduse Tuleva Täiendava Kogumisfondi osakute tagasivõtmiseks ettevõtte kontolt.',
      },
    },
    nameOnlyWords: ['Osakute omanik'],
  },
  en: {
    amounts: { fundUnits: '1,157.40741', amount: '1,250.50' },
    facts: [
      'Hello,&nbsp;Mari',
      'We will redeem 1,157.40741 units, currently worth about 1,250.50&nbsp;€. The exact amount depends on the net asset value (NAV) of the units on the dealing day.',
      'We expect to pay the money to the account EE47 **** **** **** 5685 by 01.10.2025 16:00 (Estonian time).',
      'You can usually cancel the request in your Tuleva account until 30.09.2025 16:00 (Estonian time).',
      "If it wasn't you, contact us right away: tuleva@tuleva.ee or 644&nbsp;5100",
    ],
    request: {
      person: { unnamed: 'We received your request to redeem units of Tuleva Additional Investment Fund.' },
      child: {
        named: "We received your request to redeem units of Tuleva Additional Investment Fund from Jaan Tamm's account.",
        unnamed: "We received your request to redeem units of Tuleva Additional Investment Fund from your child's account.",
      },
      company: {
        named: "We received your request to redeem units of Tuleva Additional Investment Fund from Mesila OÜ's account.",
        unnamed: "We received your request to redeem units of Tuleva Additional Investment Fund from your company's account.",
      },
    },
    nameOnlyWords: ["from 's"],
  },
};

const RECIPIENTS = { person: undefined, child: 'Jaan Tamm', company: 'Mesila OÜ' };

for (const [language, languageCopy] of Object.entries(copy)) {
  for (const [party, request] of Object.entries(languageCopy.request)) {
    const name = `savings_fund_redemption_${party}_${language}`;

    test(`${name}: confirms the request with its units, approximate sum, masked account, both deadlines and who to contact if it wasn't them`, () => {
      const body = text(
        renderMergeLanguage(html(name), { ...REQUEST, ...languageCopy.amounts, RECIPIENTNAME: RECIPIENTS[party] }),
      );
      for (const sentence of [request.named ?? request.unnamed, ...languageCopy.facts]) {
        assert.ok(body.includes(sentence), `missing: ${sentence}`);
      }
    });

    test(`${name}: carries no merge value but the request facts${party === 'person' ? '' : ' and the account holder name'}`, () => {
      assert.deepEqual(
        mergeTags(name),
        party === 'person' ? REQUEST_FACTS : [...REQUEST_FACTS, 'RECIPIENTNAME'].sort(),
      );
    });

    test(`${name}: has a subject and the fund as sender`, () => {
      assert.ok(manifest.templates[name]?.subject, 'manifest entry needs a subject');
      assert.equal(manifest.templates[name].from_email, 'tuleva@tuleva.ee');
    });

    test(`${name}: the preview shows the amounts as the service sends them in this language`, () => {
      for (const vars of Object.values(fixture(name).variants)) {
        assert.equal(vars.amount, languageCopy.amounts.amount);
        assert.equal(vars.fundUnits, languageCopy.amounts.fundUnits);
      }
    });

    if (party !== 'person') {
      test(`${name}: reads naturally when the account holder's name is unknown`, () => {
        const body = text(renderMergeLanguage(html(name), { ...REQUEST, ...languageCopy.amounts }));
        assert.ok(body.includes(request.unnamed), `missing: ${request.unnamed}`);
        for (const word of languageCopy.nameOnlyWords) {
          assert.ok(!body.includes(word), `must not say without a name: ${word}`);
        }
      });

      test(`${name}: the preview shows the email with and without the account holder's name`, () => {
        const variants = Object.values(fixture(name).variants);
        assert.ok(variants.some((vars) => vars.RECIPIENTNAME), 'no variant with RECIPIENTNAME');
        assert.ok(variants.some((vars) => !vars.RECIPIENTNAME), 'no variant without RECIPIENTNAME');
      });
    }
  }
}
