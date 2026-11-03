package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.iban.IbanValidator.canonicalize;

import ee.tuleva.onboarding.iban.ValidIban;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ArteaQuestionnaire(
    @NotNull @Valid IdentityDocument idDocument,
    @NotNull @Valid Occupation occupation,
    @NotEmpty @Size(max = 10) List<@NotNull @Valid IncomeSource> incomeSources,
    @NotNull MonthlyNetIncome monthlyNetIncome,
    @ValidIban String premiumAccountIban) {

  public ArteaQuestionnaire {
    if (premiumAccountIban != null) {
      premiumAccountIban = canonicalize(premiumAccountIban);
    }
  }

  public enum MonthlyNetIncome {
    UP_TO_600,
    FROM_601_TO_1200,
    FROM_1201_TO_3000,
    FROM_3001_TO_6000,
    OVER_6000
  }
}
