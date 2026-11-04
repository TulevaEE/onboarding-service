import test from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderMergeLanguage } from './merge-language.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

const copy = {
  et: {
    bridge:
      'Oled juba III&nbsp;samba enda kasuks tööle pannud. Selleks, et oma kogumisplaaniga kindlamalt kursil püsida, tasub see enda jaoks võimalikult mugavaks teha.',
    recurring: 'Püsimaksega toimub kogumine',
  },
  en: {
    bridge:
      'You have already put your third pillar to work for you. To stay on course with your savings plan more reliably, it pays to make it as convenient for yourself as possible.',
    recurring: 'recurring payment',
  },
};

const text = (html) => html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');

const templatesWithTheNudge = readdirSync(join(root, 'fixtures'))
  .filter((file) => file.endsWith('.json'))
  .map((file) => file.replace('.json', ''))
  .flatMap((name) =>
    Object.entries(JSON.parse(readFileSync(join(root, 'fixtures', `${name}.json`), 'utf8')).variants)
      .filter(([, vars]) => vars.suggestThirdPillarRecurringPayment)
      .map(([variant, vars]) => ({ name, variant, vars })),
  );

test('the third pillar recurring nudge is exercised in both languages', () => {
  for (const language of Object.keys(copy)) {
    assert.ok(templatesWithTheNudge.some(({ name }) => name.endsWith(`_${language}`)), language);
  }
});

for (const { name, variant, vars } of templatesWithTheNudge) {
  const { bridge, recurring } = copy[name.slice(-2)];

  test(`${name} "${variant}": the recurring nudge opens by building on the third pillar they already have`, () => {
    const rendered = text(renderMergeLanguage(readFileSync(join(root, 'dist', `${name}.html`), 'utf8'), vars));
    assert.ok(rendered.includes(`${bridge} `), 'missing the bridge sentence');
    assert.ok(rendered.indexOf(bridge) < rendered.lastIndexOf(recurring), 'the bridge must come before the pitch');
  });
}
