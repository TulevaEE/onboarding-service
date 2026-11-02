import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const manifest = JSON.parse(readFileSync(join(root, 'manifest.json'), 'utf8'));

const languages = {
  et: {
    required: [
      'Kahjuks läks Pensionikeskuses läbi ainult osa sinu avaldustest.',
      '*|totalMandateCount|* esitatud avaldusest *|successfulMandateCount|* läks läbi ja *|failedMandateCount|* mitte.',
      'Palun võta meiega ühendust, et saaksime vaadata, mida edasi teha:',
    ],
    applicationTypes: ['väljamak', 'taasliitu'],
    claims: ['tehniline viga', 'korda teha'],
  },
  en: {
    required: [
      'Unfortunately, only some of your applications went through at Pensionikeskus.',
      'Out of the *|totalMandateCount|* applications you submitted, *|successfulMandateCount|* went through and *|failedMandateCount|* did not.',
      'Please contact us so we can see what to do next:',
    ],
    applicationTypes: ['withdrawal', 'rejoin'],
    claims: ['technical problem', 'fix the problem'],
  },
};

const text = (html) => html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');

for (const [language, copy] of Object.entries(languages)) {
  const name = `batch_failed_${language}`;
  const body = text(readFileSync(join(root, 'dist', `${name}.html`), 'utf8'));

  test(`${name}: names no application type in the subject, because any signed batch can send it`, () => {
    const subject = manifest.templates[name].subject.toLowerCase();
    for (const applicationType of copy.applicationTypes) {
      assert.ok(!subject.includes(applicationType), `must not name: ${applicationType}`);
    }
  });

  test(`${name}: says how many applications went through, without calling a refusal by Pensionikeskus a technical error or promising to fix it`, () => {
    for (const sentence of copy.required) {
      assert.ok(body.includes(sentence), `missing: ${sentence}`);
    }
    for (const phrase of [...copy.applicationTypes, ...copy.claims]) {
      assert.ok(!body.toLowerCase().includes(phrase), `must not say: ${phrase}`);
    }
  });
}
