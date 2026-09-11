package ee.tuleva.onboarding.account.transaction;

import static java.util.Comparator.reverseOrder;

import ee.tuleva.onboarding.account.CashFlowService;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionService {

  private final CashFlowService cashFlowService;
  private final SavingsTransactions savingsTransactions;

  public List<Transaction> getTransactions(AuthenticatedPerson person) {
    List<Transaction> savingsFundTransactions = savingsTransactions.getTransactions(person);

    if (!person.isActingAsSelf()) {
      return savingsFundTransactions;
    }

    List<Transaction> episTransactions =
        cashFlowService.getCashFlowStatement(person).getTransactions().stream()
            .filter(cashFlow -> cashFlow.isContribution() || cashFlow.isSubtraction())
            .map(Transaction::from)
            .toList();

    return Stream.concat(episTransactions.stream(), savingsFundTransactions.stream())
        .sorted(reverseOrder())
        .toList();
  }
}
