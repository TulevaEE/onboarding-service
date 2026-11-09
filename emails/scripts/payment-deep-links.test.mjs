import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderMergeLanguage } from './merge-language.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

const ACCOUNT_ID = '0f6f9d1e-4c1a-4b2a-9c3d-8e7f6a5b4c3d';
const PAYMENT_PAGE = 'https://pension.tuleva.ee/savings-fund/payment';

const BUTTONS_BY_MERGE_VARS = [
  [
    'savings_fund_first_payment_reminder_child_et',
    { recipientAccountId: ACCOUNT_ID },
    `${PAYMENT_PAGE}/child/${ACCOUNT_ID}?utm_content=first_payment_reminder_child_payment`,
  ],
  [
    'savings_fund_first_payment_reminder_child_et',
    {},
    `${PAYMENT_PAGE}/child?utm_content=first_payment_reminder_child_payment`,
  ],
  [
    'savings_fund_first_payment_reminder_child_en',
    { recipientAccountId: ACCOUNT_ID },
    `${PAYMENT_PAGE}/child/${ACCOUNT_ID}?language=en&utm_content=first_payment_reminder_child_payment`,
  ],
  [
    'savings_fund_first_payment_reminder_child_en',
    {},
    `${PAYMENT_PAGE}/child?language=en&utm_content=first_payment_reminder_child_payment`,
  ],
  [
    'savings_fund_first_payment_reminder_company_et',
    { recipientAccountId: ACCOUNT_ID },
    `${PAYMENT_PAGE}/company/${ACCOUNT_ID}?utm_content=first_payment_reminder_company_payment`,
  ],
  [
    'savings_fund_first_payment_reminder_company_et',
    {},
    `${PAYMENT_PAGE}/company?utm_content=first_payment_reminder_company_payment`,
  ],
  [
    'savings_fund_first_payment_reminder_company_en',
    { recipientAccountId: ACCOUNT_ID },
    `${PAYMENT_PAGE}/company/${ACCOUNT_ID}?language=en&utm_content=first_payment_reminder_company_payment`,
  ],
  [
    'savings_fund_first_payment_reminder_company_en',
    {},
    `${PAYMENT_PAGE}/company?language=en&utm_content=first_payment_reminder_company_payment`,
  ],
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
  ...['savings_fund_payment_failed', 'savings_fund_payment_cancelled'].flatMap((family) => [
    [`${family}_et`, {}, PAYMENT_PAGE],
    [`${family}_en`, {}, `${PAYMENT_PAGE}?language=en`],
    [
      `${family}_et`,
      { recipientIsChild: true, recipientAccountId: ACCOUNT_ID },
      `${PAYMENT_PAGE}/child/${ACCOUNT_ID}?utm_content=payment_retry_child`,
    ],
    [
      `${family}_en`,
      { recipientIsChild: true, recipientAccountId: ACCOUNT_ID },
      `${PAYMENT_PAGE}/child/${ACCOUNT_ID}?language=en&utm_content=payment_retry_child`,
    ],
    [
      `${family}_et`,
      { recipientIsCompany: true, recipientAccountId: ACCOUNT_ID },
      `${PAYMENT_PAGE}/company/${ACCOUNT_ID}?utm_content=payment_retry_company`,
    ],
    [
      `${family}_en`,
      { recipientIsCompany: true, recipientAccountId: ACCOUNT_ID },
      `${PAYMENT_PAGE}/company/${ACCOUNT_ID}?language=en&utm_content=payment_retry_company`,
    ],
  ]),
];

BUTTONS_BY_MERGE_VARS.forEach(([name, vars, href]) => {
  test(`${name}: with ${JSON.stringify(vars)} the only payment link is ${href}`, () => {
    const html = readFileSync(join(root, 'dist', `${name}.html`), 'utf8');
    const links = [...renderMergeLanguage(html, vars).matchAll(/href="([^"]*\/savings-fund\/payment[^"]*)"/g)].map(
      (match) => match[1],
    );
    assert.deepEqual(links, [href]);
  });
});

const COMPANY_ACCOUNT_EXPLANATIONS = {
  savings_fund_payment_failed_et: 'ainult ettevõtte enda pangakontolt',
  savings_fund_payment_failed_en: "only be made from the company's own bank account",
};

Object.entries(COMPANY_ACCOUNT_EXPLANATIONS).forEach(([name, explanation]) => {
  test(`${name}: only a failed payment into a company account says it has to come from the company's bank account`, () => {
    const html = readFileSync(join(root, 'dist', `${name}.html`), 'utf8');
    const text = (vars) => renderMergeLanguage(html, vars).replace(/\s+/g, ' ');
    assert.ok(text({ recipientIsCompany: true, recipientAccountId: ACCOUNT_ID }).includes(explanation));
    assert.ok(!text({ recipientIsChild: true, recipientAccountId: ACCOUNT_ID }).includes(explanation));
    assert.ok(!text({}).includes(explanation));
  });
});
