package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.SystemAccount.INCOMING_PAYMENTS_CLEARING;
import static ee.tuleva.onboarding.ledger.SystemAccount.SECURITIES_CUSTODY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SystemAccountTest {

  @Test
  void fromAccountName_resolvesExactMatch() {
    assertThat(SystemAccount.fromAccountName("INCOMING_PAYMENTS_CLEARING"))
        .isEqualTo(INCOMING_PAYMENTS_CLEARING);
  }

  @Test
  void fromAccountName_resolvesFundQualifiedName() {
    assertThat(SystemAccount.fromAccountName(INCOMING_PAYMENTS_CLEARING.getAccountName(TKF100)))
        .isEqualTo(INCOMING_PAYMENTS_CLEARING);
  }

  @Test
  void owns_itsFundAndInstrumentAccountsButNotAnotherWhoseNameStartsTheSame() {
    assertThat(SECURITIES_CUSTODY.owns(SECURITIES_CUSTODY.getAccountName(TKF100, "LU1291102447")))
        .isTrue();
    assertThat(SECURITIES_CUSTODY.owns("SECURITIES_CUSTODY_ARCHIVE:TKF100")).isFalse();
  }

  @Test
  void fromAccountName_throwsForUnknownAccount() {
    assertThatThrownBy(() -> SystemAccount.fromAccountName("NOT_A_REAL_ACCOUNT"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
