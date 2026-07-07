package ee.tuleva.onboarding.epis.mandate.details;

import static ee.tuleva.onboarding.epis.mandate.details.BankAccountDetails.Bank.*;
import static ee.tuleva.onboarding.epis.mandate.details.BankAccountDetails.BankAccountType.ESTONIAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.epis.mandate.details.BankAccountDetails.Bank;
import org.junit.jupiter.api.Test;

class BankAccountDetailsTest {

  @Test
  void fromIban_findsBankByIbanPrefix() {
    assertThat(Bank.fromIban("EE591254471322749514")).contains(CITADELE);
    assertThat(Bank.fromIban("EE 5912 5447 1322 7495 14")).contains(CITADELE);
    assertThat(Bank.fromIban("EE211722839095030454")).contains(LUMINOR_2);
  }

  @Test
  void fromIban_isEmptyForValidIbanFromUnknownBank() {
    assertThat(Bank.fromIban("EE093300001111222233")).isEmpty();
    assertThat(Bank.fromIban("EE525500001111222233")).isEmpty();
  }

  @Test
  void fromIban_isEmptyForNonEstonianIbanEvenWhenPrefixMatchesAnEstonianBank() {
    assertThat(Bank.fromIban("LT771000011122223333")).isEmpty();
  }

  @Test
  void fromIban_throwsForInvalidIban() {
    assertThatThrownBy(() -> Bank.fromIban("EE591254471322749513"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void bankDisplayName_returnsDisplayNameForKnownBank() {
    var details = new BankAccountDetails(ESTONIAN, "EE591254471322749514");
    assertThat(details.bankDisplayName()).isEqualTo("AS Citadele banka Eesti filiaal");
  }

  @Test
  void bankDisplayName_isNullForUnknownBank() {
    var details = new BankAccountDetails(ESTONIAN, "EE093300001111222233");
    assertThat(details.bankDisplayName()).isNull();
  }
}
