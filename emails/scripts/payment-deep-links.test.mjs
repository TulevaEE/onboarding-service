import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderMergeLanguage } from './merge-language.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

const PAYMENT_BUTTONS = {
  savings_fund_first_payment_reminder_child_et:
    'https://pension.tuleva.ee/savings-fund/payment/child/*|recipientAccountId|*?utm_content=first_payment_reminder_child_payment',
  savings_fund_first_payment_reminder_child_en:
    'https://pension.tuleva.ee/savings-fund/payment/child/*|recipientAccountId|*?language=en&utm_content=first_payment_reminder_child_payment',
  savings_fund_first_payment_reminder_company_et:
    'https://pension.tuleva.ee/savings-fund/payment/company?utm_content=first_payment_reminder_company_payment',
  savings_fund_first_payment_reminder_company_en:
    'https://pension.tuleva.ee/savings-fund/payment/company?language=en&utm_content=first_payment_reminder_company_payment',
};

const ACCOUNT_ID = '0f6f9d1e-4c1a-4b2a-9c3d-8e7f6a5b4c3d';
const PAYMENT_PAGE = 'https://pension.tuleva.ee/savings-fund/payment';

const BUTTONS_BY_MERGE_VARS = [
  [
    'savings_fund_company_onboarded_et',
    { recipientAccountId: ACCOUNT_ID },
    `${PAYMENT_PAGE}/company/${ACCOUNT_ID}?utm_content=company_onboarded_payment`,
  ],
  [
    'savings_fund_company_onboarded_et',
    {},
    `${PAYMENT_PAGE}/company?utm_content=company_onboarded_payment`,
  ],
  [
    'savings_fund_company_onboarded_en',
    { recipientAccountId: ACCOUNT_ID },
    `${PAYMENT_PAGE}/company/${ACCOUNT_ID}?language=en&utm_content=company_onboarded_payment`,
  ],
  [
    'savings_fund_company_onboarded_en',
    {},
    `${PAYMENT_PAGE}/company?language=en&utm_content=company_onboarded_payment`,
  ],
];

const ROLE_LESS_PAYMENT_LINKS = [
  'href="https://pension.tuleva.ee/savings-fund/payment"',
  'href="https://pension.tuleva.ee/savings-fund/payment?',
];

Object.entries(PAYMENT_BUTTONS).forEach(([name, href]) => {
  test(`${name}: the payment button deep links into the account holder's own payment flow`, () => {
    const html = readFileSync(join(root, 'dist', `${name}.html`), 'utf8');
    assert.ok(html.includes(`href="${href}"`), `missing button link: ${href}`);
    ROLE_LESS_PAYMENT_LINKS.forEach((link) => {
      assert.ok(!html.includes(link), `must not link to the role-less payment page: ${link}`);
    });
  });
});

BUTTONS_BY_MERGE_VARS.forEach(([name, vars, href]) => {
  test(`${name}: with ${JSON.stringify(vars)} the only payment link is ${href}`, () => {
    const html = readFileSync(join(root, 'dist', `${name}.html`), 'utf8');
    const links = [...renderMergeLanguage(html, vars).matchAll(/href="([^"]*\/savings-fund\/payment[^"]*)"/g)].map(
      (match) => match[1],
    );
    assert.deepEqual(links, [href]);
  });
});
