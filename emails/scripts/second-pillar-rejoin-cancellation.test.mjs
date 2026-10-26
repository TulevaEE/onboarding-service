import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

const languages = {
  et: {
    required: [
      'Tühistasid oma II&nbsp;samba sissemaksete tasumise avalduse. Sinu eest ei hakata II&nbsp;samba sissemakseid tegema.',
      'Kui soovid hiljem siiski uuesti koguma hakata, saad uue avalduse esitada Tuleva kaudu.',
      'Tühistamiseks esitasid II&nbsp;samba sissemaksete tasumisest vabastamise avalduse. Selle koopia, mis on saadetud Pensionikeskusesse, on kirjale lisatud.',
      'Maksustamine oleneb isikuga seotud asjaoludest, sh residentsusest. Maksuseadused muutuvad ajas.',
      'II&nbsp;sambasse makstud sotsiaalmaks vähendab veidi I&nbsp;sammast.',
    ],
    forbidden: ['Mineviku tootlus', 'Tulumaksu tagasisaamiseks'],
  },
  en: {
    required: [
      'You canceled your II&nbsp;pillar application for making contributions. Contributions to your II&nbsp;pillar will not start.',
      'If you want to start saving again later, you can file a new application through Tuleva.',
      'To cancel it, you filed a II&nbsp;pillar application for exemption from contributions. A copy of it, as sent to Pensionikeskus, is attached to this email.',
      'Taxation depends on your personal circumstances, including residency. Tax laws change over time.',
      'Social tax paid into the II&nbsp;pillar slightly reduces your I&nbsp;pillar.',
    ],
    forbidden: ['Past performance', 'tax return'],
  },
};

const text = (html) => html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');

for (const [language, copy] of Object.entries(languages)) {
  const name = `second_pillar_rejoin_cancellation_${language}`;
  const html = readFileSync(join(root, 'dist', `${name}.html`), 'utf8');

  test(`${name}: confirms contributions will not start, how to rejoin later, that the signed application is attached, and carries the II pillar rejoin disclaimer`, () => {
    for (const sentence of copy.required) {
      assert.ok(text(html).includes(sentence), `missing: ${sentence}`);
    }
  });

  test(`${name}: carries no returns or III pillar tax refund disclaimer`, () => {
    for (const phrase of copy.forbidden) {
      assert.ok(!text(html).includes(phrase), `must not carry: ${phrase}`);
    }
  });
}
