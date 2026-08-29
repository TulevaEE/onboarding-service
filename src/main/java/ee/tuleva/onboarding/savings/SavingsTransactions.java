package ee.tuleva.onboarding.savings;

import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.util.List;

public interface SavingsTransactions {

  List<Transaction> getTransactions(AuthenticatedPerson person);
}
