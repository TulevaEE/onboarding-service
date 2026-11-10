import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

const languages = {
  et: {
    required: [
      'Sinu avaldus II&nbsp;sambas kogumise jätkamiseks on tühistatud. Selle alusel sissemaksed ei alga ja praegu ei pea sa rohkem midagi tegema.',
      'Kui otsustad siiski uuesti II&nbsp;sambas koguma hakata, saad esitada uue avalduse oma Tuleva kontol',
      'Millal sissemaksed algavad, sõltub sellest, millal uue avalduse esitad.',
      'Otsus on sinu ja on täiesti mõistetav, kui tahad seda veel kaaluda. Taasliitumise lehel oleme kirja pannud, mida taasliitumine sinu jaoks tähendab, nii plussid kui ka piirangud.',
      'Tühistamise avaldus on edastatud Pensionikeskusele ja selle koopia leiad kirja manusest.',
      'Maksustamine oleneb isikuga seotud asjaoludest, sh residentsusest. Maksuseadused muutuvad ajas.',
      'II&nbsp;sambasse makstud sotsiaalmaks vähendab veidi I&nbsp;sammast',
    ],
    forbidden: ['Mineviku tootlus', 'Tulumaksu tagasisaamiseks'],
  },
  en: {
    required: [
      'Your application to continue saving in the second pillar has been cancelled. Contributions will not start, and there is nothing more you need to do right now.',
      'If you do decide to start saving in the second pillar again, you can submit a new application in your Tuleva account',
      'When contributions start depends on when you submit the new application.',
      'On our rejoining page we\'ve set out what rejoining means for you, both the benefits and the limits.',
      'The cancellation application has been sent to Pensionikeskus and a copy is attached to this email.',
      'Taxation depends on your personal circumstances, including residency. Tax laws change over time.',
      'Social tax paid into the II&nbsp;pillar slightly reduces your I&nbsp;pillar',
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
