import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderMergeLanguage } from './merge-language.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const DISCLAIMER_TEXT_COLOR = '#212529';

const languages = {
  et: {
    sotsmaksHref: 'https://tuleva.ee/sotsmaks',
    sotsmaksText: 'tuleva.ee/sotsmaks',
    required: [
      'Sinu avaldus II&nbsp;sambas kogumise jätkamiseks on esitatud. Sissemaksed hakkavad laekuma automaatselt ja nende alguskuupäev on *|contributionStartDate|*.',
      'Kui mõtled ümber, saad avalduse oma Tuleva kontol tühistada. Viimane päev selleks on *|cancellationDeadline|*.',
      'Kogutud raha saad kasutada alates eelpensionieast ehk viis aastat enne vanaduspensioniiga (praegu 60.&nbsp;eluaastast). Pärast taasliitumist ei saa sa raha enne seda teist korda välja võtta.',
      'Maksustamine oleneb isikuga seotud asjaoludest, sh residentsusest. Maksuseadused muutuvad ajas.',
      'II&nbsp;sambasse makstud sotsiaalmaks vähendab veidi I&nbsp;sammast.',
      'Vaata II&nbsp;samba mõju I&nbsp;sambale',
    ],
    forbidden: ['30.&nbsp;novembril', 'Mineviku tootlus', 'Tulumaksu tagasisaamiseks'],
    fundBranches: {
      stock: 'Sinu sissemaksed lähevad Tuleva Maailma Aktsiate Pensionifondi .',
      bond: 'Sinu sissemaksed lähevad Tuleva Maailma Võlakirjade Pensionifondi .',
      other: 'Sinu sissemaksed lähevad fondi LHV',
      keptTuleva: 'Sinu sissemaksed lähevad edasi Tuleva fondi, milles kogusid ka varem.',
      keptOther: 'Sinu sissemaksed lähevad fondi, milles kogusid ka varem.',
    },
    fundBranchByVariant: {
      'aktsiafond-6': 'stock',
      'volakirjafond-4': 'bond',
      'muu-fond-korge-tasuga': 'other',
      'muu-fond-madala-tasuga': 'other',
      'jai-tuleva-fondi': 'keptTuleva',
      'jai-vanasse-fondi-2': 'keptOther',
    },
  },
  en: {
    sotsmaksHref: 'https://tuleva.ee/en/sotsmaks',
    sotsmaksText: 'tuleva.ee/en/sotsmaks',
    required: [
      'Contributions start on *|contributionStartDate|*.',
      'If you change your mind, you can cancel the application in your Tuleva account until *|cancellationDeadline|*.',
      'You can use the money from age 60. After rejoining, you can\'t stop contributions again or withdraw the money a second time.',
      'Taxation depends on your personal circumstances, including residency. Tax laws change over time.',
      'Social tax paid into the II&nbsp;pillar slightly reduces your I&nbsp;pillar.',
      'See how the II&nbsp;pillar affects your I&nbsp;pillar at',
    ],
    forbidden: ['30&nbsp;November', 'Past performance', 'tax return'],
    alwaysFiled: 'Application for making contributions',
    filedWhenChanged: {
      paymentRateChange: 'Contribution rate change application',
      fundSelection: 'Fund selection application',
    },
  },
};

const text = (html) => html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');

for (const [language, copy] of Object.entries(languages)) {
  const name = `second_pillar_rejoin_${language}`;
  const html = readFileSync(join(root, 'dist', `${name}.html`), 'utf8');

  test(`${name}: says when contributions start, until when the application can be canceled, what rejoining commits to, and how it affects the I pillar`, () => {
    for (const sentence of copy.required) {
      assert.ok(text(html).includes(sentence), `missing: ${sentence}`);
    }
  });

  test(`${name}: carries no returns or III pillar tax refund disclaimer and no hard-coded filing deadline`, () => {
    for (const phrase of copy.forbidden) {
      assert.ok(!text(html).includes(phrase), `must not carry: ${phrase}`);
    }
  });

  test(`${name}: the social tax link is in the disclaimer text color, not the link blue`, () => {
    const links = [...html.matchAll(/<a\b[^>]*>/g)]
      .map((m) => m[0])
      .filter((tag) => tag.includes(`href="${copy.sotsmaksHref}"`));
    assert.equal(links.length, 1, `expected one link to ${copy.sotsmaksHref}`);
    assert.match(links[0], new RegExp(`style="[^"]*color: ${DISCLAIMER_TEXT_COLOR}`));
    assert.ok(html.includes(`href="${copy.sotsmaksHref}">${copy.sotsmaksText}</a>`), 'link text matches where it goes');
  });

  if (copy.filedWhenChanged) {
    test(`${name}: lists the rate and fund applications only when they were filed`, () => {
      const filed = (vars) => text(renderMergeLanguage(html, vars));
      const nothingChanged = filed({ paymentRateChange: false, fundSelection: false });
      assert.ok(nothingChanged.includes(copy.alwaysFiled), `missing: ${copy.alwaysFiled}`);
      for (const [variable, application] of Object.entries(copy.filedWhenChanged)) {
        assert.ok(filed({ [variable]: true }).includes(application), `missing: ${application}`);
        assert.ok(!nothingChanged.includes(application), `must not list: ${application}`);
      }
    });
  }

  if (copy.fundBranches) {
    test(`${name}: names exactly one destination fund, the one the saver chose or kept`, () => {
      const fixture = JSON.parse(readFileSync(join(root, 'fixtures', `${name}.json`), 'utf8'));
      const sentences = Object.values(copy.fundBranches);
      for (const [variant, expected] of Object.entries(copy.fundBranchByVariant)) {
        const rendered = text(renderMergeLanguage(html, fixture.variants[variant]));
        for (const sentence of sentences) {
          const shown = rendered.includes(sentence);
          assert.equal(shown, sentence === copy.fundBranches[expected], `variant "${variant}": ${sentence}`);
        }
      }
    });

    test(`${name}: names the payment rate and calls 6% the maximum tax benefit only at 6%`, () => {
      const rendered = (vars) => text(renderMergeLanguage(html, vars));
      const atSix = rendered({ paymentRate: 6, maxPaymentRate: true });
      const atTwo = rendered({ paymentRate: 2, maxPaymentRate: false });
      assert.ok(atSix.includes('Valisid II&nbsp;samba sissemaksete suuruseks 6% oma brutopalgast. See tähendab, et kasutad II&nbsp;samba maksuvõitu maksimaalselt.'));
      assert.ok(atTwo.includes('Valisid II&nbsp;samba sissemaksete suuruseks 2% oma brutopalgast. Oma sissemakse suurust saad vajadusel muuta kord aastas.'));
      assert.ok(!atTwo.includes('maksuvõitu maksimaalselt'));
    });

    test(`${name}: states the fee of another manager's fund only when it is high`, () => {
      const rendered = (vars) => text(renderMergeLanguage(html, vars));
      const other = { selectedOtherFund: true, selectedFundName: 'Fond X' };
      const fee = 'jooksvad tasud on 1,1% aastas';
      assert.ok(rendered({ ...other, selectedHighFeeFund: true, selectedFundFee: '1,1' }).includes(fee));
      assert.ok(!rendered(other).includes('jooksvad tasud'));
    });
  }

  test(`${name}: every fixture variant fills the contribution start date and the cancellation deadline`, () => {
    const fixture = JSON.parse(readFileSync(join(root, 'fixtures', `${name}.json`), 'utf8'));
    for (const [variant, vars] of Object.entries(fixture.variants)) {
      assert.ok(vars.contributionStartDate, `variant "${variant}" has no contributionStartDate`);
      assert.ok(vars.cancellationDeadline, `variant "${variant}" has no cancellationDeadline`);
    }
  });
}
