package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.documentNumber;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.personalCode;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RememberedSmartIdAccountResponseTest {

  @Test
  void capitalizesTheNamesTheCertificateSpellsInCapitals() {
    var account =
        new RememberedSmartIdAccount(personalCode, documentNumber, "ERKO", "KUUSK-ÕUNAPUU");

    assertThat(RememberedSmartIdAccountResponse.from(account))
        .isEqualTo(new RememberedSmartIdAccountResponse("Erko", "Kuusk-Õunapuu"));
  }

  @Test
  void keepsDeliberatelyCasedNamePartsAndCapitalizesEachHyphenatedPart() {
    var account =
        new RememberedSmartIdAccount(personalCode, documentNumber, "MARI-LIIS", "McDonald");

    assertThat(RememberedSmartIdAccountResponse.from(account))
        .isEqualTo(new RememberedSmartIdAccountResponse("Mari-Liis", "McDonald"));
  }
}
