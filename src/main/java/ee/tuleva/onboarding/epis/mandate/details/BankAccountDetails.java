package ee.tuleva.onboarding.epis.mandate.details;

import java.util.Arrays;
import lombok.Getter;

public record BankAccountDetails(BankAccountType type, Bank bank, String accountIban) {
  // TODO bank name missing, required in form
  public enum BankAccountType {
    ESTONIAN,
    // EUROPEAN(E) and OTHER(V) not supported
  }

  public enum Bank {
    COOP("42", "Coop Pank aktsiaselts"),
    SEB("10", "AS SEB Pank"),
    SWED("22", "Swedbank AS"),
    LUMINOR("96", "Luminor Bank AS"),
    LUMINOR_2("17", "Luminor Bank AS"),
    LHV("77", "AS LHV Pank"),
    BIGBANK("75", "BigBank AS");

    @Getter private final String displayName;

    private final String ibanCheckCode;

    Bank(String ibanCheckCode, String displayName) {
      this.displayName = displayName;
      this.ibanCheckCode = ibanCheckCode;
    }

    public static Bank fromIban(String iban) {
      String ibanCheckCode = iban.substring(4, 6);
      return Arrays.stream(values())
          .filter(bank -> bank.ibanCheckCode.equals(ibanCheckCode))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("No bank found for IBAN"));
    }
  }
}
