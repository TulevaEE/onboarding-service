package ee.tuleva.onboarding.banking;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface BankAccounts {

  String getIban(TulevaFund fund, BankAccountType type);

  Optional<BankAccount> find(String iban);

  List<BankAccount> findAll();

  List<BankAccount> findAll(TulevaFund fund);
}
