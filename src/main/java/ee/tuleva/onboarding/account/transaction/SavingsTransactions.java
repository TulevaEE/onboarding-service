package ee.tuleva.onboarding.account.transaction;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.util.List;

public interface SavingsTransactions {

  List<Transaction> getTransactions(AuthenticatedPerson person);
}
