package ee.tuleva.onboarding.account.transaction;

import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY;
import static java.util.Comparator.reverseOrder;

import ee.tuleva.onboarding.account.CashFlowService;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
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

  public List<Transaction> getTransactions(AuthenticatedPerson person) {
    List<Transaction> savingsTransactions = savingsFundTransactionService.getTransactions(person);

    if (person.getRoleType() == LEGAL_ENTITY) {
      return savingsTransactions;
    }

    List<Transaction> episTransactions =
        cashFlowService.getCashFlowStatement(person).getTransactions().stream()
            .filter(cashFlow -> cashFlow.isContribution() || cashFlow.isSubtraction())
            .map(Transaction::from)
            .toList();

    return Stream.concat(episTransactions.stream(), savingsTransactions.stream())
        .sorted(reverseOrder())
        .toList();
  }
}
