package ee.tuleva.onboarding.epis.mandate.details;

import static ee.tuleva.onboarding.capital.transfer.iban.IbanValidator.canonicalize;
import static ee.tuleva.onboarding.capital.transfer.iban.IbanValidator.isValid;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

public record BankAccountDetails(BankAccountType type, String accountIban) implements Serializable {
  @JsonIgnore
  public @Nullable String bankDisplayName() {
    return Bank.displayNameFromIban(accountIban);
  }

  public enum BankAccountType {
    ESTONIAN,
    // EUROPEAN(E) and OTHER(V) not supported
  }

  @Slf4j
  public enum Bank {
    COOP("42", "Coop Pank aktsiaselts"),
    SEB("10", "AS SEB Pank"),
    SWED("22", "Swedbank AS"),
    LUMINOR("96", "Luminor Bank AS"),
    LUMINOR_2("17", "Luminor Bank AS"),
    LHV("77", "AS LHV Pank"),
    BIGBANK("75", "Bigbank AS"),
    CITADELE("12", "AS Citadele banka Eesti filiaal");

    @Getter private final String displayName;

    private final String ibanCheckCode;

    Bank(String ibanCheckCode, String displayName) {
      this.displayName = displayName;
      this.ibanCheckCode = ibanCheckCode;
    }

    public static @Nullable String displayNameFromIban(String iban) {
      return fromIban(iban).map(Bank::getDisplayName).orElse(null);
    }

    public static Optional<Bank> fromIban(String iban) {
      if (!isValid(iban)) {
        throw new IllegalArgumentException("Invalid IBAN");
      }

      String canonicalIban = canonicalize(iban);
      if (!canonicalIban.startsWith("EE")) {
        return Optional.empty();
      }

      String ibanCheckCode = canonicalIban.substring(4, 6);
      Optional<Bank> bank =
          Arrays.stream(values())
              .filter(candidate -> candidate.ibanCheckCode.equals(ibanCheckCode))
              .findFirst();
      if (bank.isEmpty()) {
        log.error(
            "No Estonian bank matched a valid Estonian IBAN, add it to the Bank enum if it is a real bank: identityCode={}",
            ibanCheckCode);
      }
      return bank;
    }
  }
}
