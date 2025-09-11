package ee.tuleva.onboarding.epis.mandate.details;

import static ee.tuleva.onboarding.epis.mandate.details.BankAccountDetails.Bank.CITADELE;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BankAccountDetailsTest {

  @Test
  @DisplayName("parses iban, throws for invalid")
  void fromIban() {
    assertThrows(
        IllegalArgumentException.class,
        () -> BankAccountDetails.Bank.fromIban("EE591254471322749513"));
    assertEquals(CITADELE, BankAccountDetails.Bank.fromIban("EE591254471322749514"));
    assertEquals(CITADELE, BankAccountDetails.Bank.fromIban("EE 5912 5447 1322 7495 14"));
  }
}
