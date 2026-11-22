import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderMergeLanguage } from './merge-language.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const manifest = JSON.parse(readFileSync(join(root, 'manifest.json'), 'utf8'));

const text = (html) => html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');
const html = (name) => readFileSync(join(root, 'dist', `${name}.html`), 'utf8');
const mergeTags = (name) =>
  new Set([...html(name).matchAll(/\*\|(?:IF:|ELSEIF:)?([A-Za-z0-9_]+)\|\*/g)].map((m) => m[1]));

const contactDetailsChanged = {
  et: {
    emailOnly: 'Sinu Tuleva kontol muutus e-posti aadress.',
    phoneOnly: 'Sinu Tuleva kontol muutus telefoninumber.',
    both: 'Sinu Tuleva kontol muutusid e-posti aadress ja telefoninumber.',
    previousAddress: 'Saatsime selle kirja sinu varasemale e-posti aadressile.',
    nothingToDo: 'Kui tegid muudatuse ise, ei pea sa midagi tegema.',
    contactUs: 'Kui see polnud sina, võta meiega kohe ühendust: tuleva@tuleva.ee või 644&nbsp;5100',
  },
  en: {
    emailOnly: 'The email address in your Tuleva account was changed.',
    phoneOnly: 'The phone number in your Tuleva account was changed.',
    both: 'The email address and phone number in your Tuleva account were changed.',
    previousAddress: 'We are sending this to your previous email address.',
    nothingToDo: 'If you made the change yourself, there is nothing you need to do.',
    contactUs: "If it wasn't you, contact us right away: tuleva@tuleva.ee or 644&nbsp;5100",
  },
};

const previousContactActivity = {
  et: {
    required: [
      'Sinu Tuleva kontol allkirjastati avaldus kogutud raha väljamaksmiseks.',
      'Saatsime selle kirja sinu varasemale e-posti aadressile, sest sinu kontaktandmed muutusid hiljuti.',
      'Kui allkirjastasid avalduse ise, ei pea sa midagi tegema.',
      'Kui see polnud sina, võta meiega kohe ühendust: tuleva@tuleva.ee või 644&nbsp;5100',
    ],
    applicationTypes: ['fondipension', 'osali', 'sammas', 'tagasivõt'],
  },
  en: {
    required: [
      'An application to pay out your savings was signed in your Tuleva account.',
      'We are sending this to your previous email address because your contact details changed recently.',
      'If you signed it yourself, there is nothing you need to do.',
      "If it wasn't you, contact us right away: tuleva@tuleva.ee or 644&nbsp;5100",
    ],
    applicationTypes: ['fund pension', 'partial', 'pillar', 'redemption'],
  },
};

const realCombinations = {
  'an email change, at a previous address': {
    change: 'emailOnly',
    vars: { emailChanged: true, phoneChanged: false, previousAddress: true },
  },
  'an email and phone change, at a previous address': {
    change: 'both',
    vars: { emailChanged: true, phoneChanged: true, previousAddress: true },
  },
  'a phone change, at the current address': {
    change: 'phoneOnly',
    vars: { emailChanged: false, phoneChanged: true, previousAddress: false },
  },
  'a phone change, at a previous address': {
    change: 'phoneOnly',
    vars: { emailChanged: false, phoneChanged: true, previousAddress: true },
  },
};

const flags = (vars) =>
  JSON.stringify([Boolean(vars.emailChanged), Boolean(vars.phoneChanged), Boolean(vars.previousAddress)]);

for (const [language, copy] of Object.entries(contactDetailsChanged)) {
  const name = `contact_details_changed_${language}`;

  for (const [combination, { change, vars }] of Object.entries(realCombinations)) {
    test(`${name}: ${combination} names exactly what changed, says why this address hears of it only when it is a previous one, and asks to contact us if it wasn't them`, () => {
      const body = text(renderMergeLanguage(html(name), { FNAME: 'Mari', ...vars }));
      for (const [other, sentence] of Object.entries({
        emailOnly: copy.emailOnly,
        phoneOnly: copy.phoneOnly,
        both: copy.both,
      })) {
        assert.equal(body.includes(sentence), other === change, `"${sentence}" in ${combination}`);
      }
      assert.equal(body.includes(copy.previousAddress), vars.previousAddress, 'previous address sentence');
      assert.ok(body.includes(copy.nothingToDo), `missing: ${copy.nothingToDo}`);
      assert.ok(body.includes(copy.contactUs), `missing: ${copy.contactUs}`);
    });
  }

  test(`${name}: the preview shows every real combination of what changed and who is told, and nothing else`, () => {
    const fixture = JSON.parse(readFileSync(join(root, 'fixtures', `${name}.json`), 'utf8'));
    assert.deepEqual(
      Object.values(fixture.variants).map(flags).sort(),
      Object.values(realCombinations).map(({ vars }) => flags(vars)).sort(),
    );
  });

  test(`${name}: carries no merge value but the name, what changed and whether this is a previous address, so no address or number can leak into it`, () => {
    assert.deepEqual([...mergeTags(name)].sort(), ['FNAME', 'emailChanged', 'phoneChanged', 'previousAddress']);
  });
}

for (const [language, copy] of Object.entries(previousContactActivity)) {
  const name = `previous_contact_activity_${language}`;

  test(`${name}: says a payout application was signed and why this address hears of it, without naming which application`, () => {
    const body = text(renderMergeLanguage(html(name), { FNAME: 'Mari' }));
    for (const sentence of copy.required) {
      assert.ok(body.includes(sentence), `missing: ${sentence}`);
    }
    const subject = manifest.templates[name].subject.toLowerCase();
    for (const applicationType of copy.applicationTypes) {
      assert.ok(!body.toLowerCase().includes(applicationType), `body must not name: ${applicationType}`);
      assert.ok(!subject.includes(applicationType), `subject must not name: ${applicationType}`);
    }
  });

  test(`${name}: carries no merge value but the name`, () => {
    assert.deepEqual([...mergeTags(name)], ['FNAME']);
  });
}
