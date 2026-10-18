package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.application;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.ergoRequest;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TerminationStatementTest {

  @Test
  void endsTheContractFromTheChosenDate() {
    assertThat(TerminationStatement.of(application(ergoRequest())))
        .isEqualTo(
            "Ütlen üles täiendava kogumispensioni kindlustuslepingu nr 70-512-345678 alates"
                + " 01.11.2026 ja soovin kasutada selle kogu tagastusväärtust pensionifondi"
                + " Tuleva III Samba Pensionifond (ISIN EE3600001707) osakute omandamiseks,"
                + " pensionikonto 993432432 (KoPS § 63 lg 5, lg 5¹ p 2, lg 5² ja 5³)."
                + " Tulumaksu ei peeta kinni (TuMS § 21 lg 1¹ p 1 ja 2).");
  }
}
