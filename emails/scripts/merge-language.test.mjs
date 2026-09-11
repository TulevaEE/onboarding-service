import test from 'node:test';
import assert from 'node:assert/strict';
import { renderMergeLanguage } from './merge-language.mjs';

test('substitutes variables and leaves unknown ones intact', () => {
  const out = renderMergeLanguage('Tere, *|FNAME|*! Summa: *|amount|* / *|missing|*', {
    FNAME: 'Mari',
    amount: '300.00',
  });
  assert.equal(out, 'Tere, Mari! Summa: 300.00 / *|missing|*');
});

test('renders only the matching branch of an IF chain', () => {
  const html = 'A*|IF:x|*X*|ELSEIF:y|*Y*|ELSE:|*Z*|END:IF|*B';
  assert.equal(renderMergeLanguage(html, { x: true }), 'AXB');
  assert.equal(renderMergeLanguage(html, { y: true }), 'AYB');
  assert.equal(renderMergeLanguage(html, {}), 'AZB');
});

test('first satisfied branch wins even when several are true', () => {
  const html = '*|IF:x|*X*|ELSEIF:y|*Y*|END:IF|*';
  assert.equal(renderMergeLanguage(html, { x: true, y: true }), 'X');
});

test('handles nested conditionals like the hasTulevaUser wrapper', () => {
  const html =
    '*|IF:hasTulevaUser|*konto:*|IF:suggestSecondPillar|*II*|ELSE:|*muu*|END:IF|**|ELSE:|*logi sisse*|END:IF|*';
  assert.equal(
    renderMergeLanguage(html, { hasTulevaUser: true, suggestSecondPillar: true }),
    'konto:II',
  );
  assert.equal(renderMergeLanguage(html, { hasTulevaUser: true }), 'konto:muu');
  assert.equal(renderMergeLanguage(html, { hasTulevaUser: false }), 'logi sisse');
});

test('a false outer branch suppresses true inner branches', () => {
  const html = '*|IF:outer|**|IF:inner|*deep*|END:IF|**|END:IF|*after';
  assert.equal(renderMergeLanguage(html, { outer: false, inner: true }), 'after');
});
