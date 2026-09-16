package ee.tuleva.onboarding.banking;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface BankAccounts {

  String getIban(TulevaFund fund, BankAccountType type);

  Optional<BankAccount> find(@Nullable String iban);

  List<BankAccount> findAll();

  List<BankAccount> findAll(TulevaFund fund);
}
