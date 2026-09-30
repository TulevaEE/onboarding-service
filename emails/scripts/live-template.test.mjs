import test from 'node:test';
import assert from 'node:assert/strict';
import { matchesLive } from './live-template.mjs';

const built = `<div
         aria-roledescription="email" style="background-color:#FFFFFF;" role="article" dir="auto" >
      <p>Tere, *|FNAME|*</p>
    </div>`;

test('a template Mandrill stored with its line breaks collapsed and no space before > matches the build', () => {
  const stored =
    '<div aria-roledescription="email" style="background-color:#FFFFFF;" role="article" dir="auto"> <p>Tere, *|FNAME|*</p> </div>';

  assert.equal(matchesLive(stored, built), true);
});

test('a changed word does not match the build', () => {
  const stored =
    '<div aria-roledescription="email" style="background-color:#FFFFFF;" role="article" dir="auto"> <p>Tere, *|LNAME|*</p> </div>';

  assert.equal(matchesLive(stored, built), false);
});

test('a space added or removed between words does not match the build', () => {
  const stored =
    '<div aria-roledescription="email" style="background-color:#FFFFFF;" role="article" dir="auto"> <p>Tere,*|FNAME|*</p> </div>';

  assert.equal(matchesLive(stored, built), false);
});

test('a self-closing tag stored without the space before /> matches the build', () => {
  assert.equal(matchesLive('<img src="logo.png"/>', '<img src="logo.png" />'), true);
});
