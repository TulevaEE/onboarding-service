package ee.tuleva.onboarding.epis.mandate.details;

import static ee.tuleva.onboarding.epis.mandate.details.BankAccountDetails.Bank.*;
import static org.junit.jupiter.api.Assertions.*;

import ee.tuleva.onboarding.epis.mandate.details.BankAccountDetails.Bank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BankAccountDetailsTest {

  @Test
  @DisplayName("parses iban, throws for invalid")
  void fromIban() {
    assertThrows(IllegalArgumentException.class, () -> Bank.fromIban("EE591254471322749513"));
    assertEquals(CITADELE, Bank.fromIban("EE591254471322749514"));
    assertEquals(CITADELE, Bank.fromIban("EE 5912 5447 1322 7495 14"));
    assertEquals(LUMINOR_2, Bank.fromIban("EE211722839095030454"));
  }
}
