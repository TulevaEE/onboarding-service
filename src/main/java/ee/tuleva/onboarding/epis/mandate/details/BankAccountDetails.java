package ee.tuleva.onboarding.epis.mandate.details;

import static ee.tuleva.onboarding.capital.transfer.iban.IbanValidator.isValid;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Arrays;
import lombok.Getter;

public record BankAccountDetails(BankAccountType type, String accountIban) {
  @JsonIgnore
  public Bank bank() {
    return Bank.fromIban(accountIban);
  }

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
    BIGBANK("75", "BigBank AS"),
    CITADELE("12", "AS Citadele banka Eesti filiaal");

    @Getter private final String displayName;

    private final String ibanCheckCode;

    Bank(String ibanCheckCode, String displayName) {
      this.displayName = displayName;
      this.ibanCheckCode = ibanCheckCode;
    }

    public static Bank fromIban(String iban) {
      if (!isValid(iban)) {
        throw new IllegalArgumentException("Invalid IBAN");
      }

      String ibanCheckCode = iban.replaceAll("\\s+", "").substring(4, 6);
      return Arrays.stream(values())
          .filter(bank -> bank.ibanCheckCode.equals(ibanCheckCode))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("No bank found for IBAN"));
    }
  }
}
