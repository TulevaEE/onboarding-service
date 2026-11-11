import test from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderMergeLanguage } from './merge-language.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

const copy = {
  et: {
    link: 'https://pension.tuleva.ee/3rd-pillar-recurring-payment?utm_content=nudge_third_pillar_raise',
    button: 'Muudan&nbsp;püsimakset',
  },
  en: {
    link: 'https://pension.tuleva.ee/3rd-pillar-recurring-payment?language=en&utm_content=nudge_third_pillar_raise',
    button: 'Change&nbsp;recurring&nbsp;payment',
  },
};

const variantsWithTheNudge = readdirSync(join(root, 'fixtures'))
  .filter((file) => file.endsWith('.json'))
  .map((file) => file.replace('.json', ''))
  .flatMap((name) =>
    Object.entries(JSON.parse(readFileSync(join(root, 'fixtures', `${name}.json`), 'utf8')).variants)
      .filter(([, vars]) => vars.suggestThirdPillarRaise)
      .map(([variant, vars]) => ({ name, variant, vars })),
  );

test('the third pillar raise nudge is exercised in both languages', () => {
  for (const language of Object.keys(copy)) {
    assert.ok(variantsWithTheNudge.some(({ name }) => name.endsWith(`_${language}`)), language);
  }
});

for (const { name, variant, vars } of variantsWithTheNudge) {
  const { link, button } = copy[name.slice(-2)];

  test(`${name} "${variant}": the raise nudge sends the saver to the flow for changing a recurring payment and says so`, () => {
    const rendered = renderMergeLanguage(readFileSync(join(root, 'dist', `${name}.html`), 'utf8'), vars);
    assert.ok(rendered.includes(link), `missing link: ${link}`);
    assert.ok(rendered.includes(button), `missing button: ${button}`);
  });
}
