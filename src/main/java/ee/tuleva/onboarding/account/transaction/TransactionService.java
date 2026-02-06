package ee.tuleva.onboarding.account.transaction;

import static java.util.Comparator.reverseOrder;

import ee.tuleva.onboarding.account.CashFlowService;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.savings.fund.SavingsFundTransactionService;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionService {

  private final CashFlowService cashFlowService;
  private final SavingsFundTransactionService savingsFundTransactionService;

  public List<Transaction> getTransactions(Person person) {
    List<Transaction> episTransactions =
        cashFlowService.getCashFlowStatement(person).getTransactions().stream()
            .filter(cashFlow -> cashFlow.isContribution() || cashFlow.isSubtraction())
            .map(Transaction::from)
            .toList();

    List<Transaction> savingsTransactions = savingsFundTransactionService.getTransactions(person);

    return Stream.concat(episTransactions.stream(), savingsTransactions.stream())
        .sorted(reverseOrder())
        .toList();
  }
}
