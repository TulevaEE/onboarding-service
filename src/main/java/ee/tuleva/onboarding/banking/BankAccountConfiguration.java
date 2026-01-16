package ee.tuleva.onboarding.banking;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

public interface BankAccountConfiguration {

  @NotNull
  String getAccountIban(BankAccountType account);

  @Nullable
  BankAccountType getAccountType(String iban);
}
