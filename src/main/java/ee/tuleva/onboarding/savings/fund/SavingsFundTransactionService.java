package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.SUBTRACTION;
import static ee.tuleva.onboarding.ledger.UserAccount.REDEMPTIONS;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static java.util.Comparator.reverseOrder;

import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import ee.tuleva.onboarding.ledger.LedgerEntry;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.LedgerTransaction;
import ee.tuleva.onboarding.ledger.UserAccount;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SavingsFundTransactionService {

  private final UserService userService;
  private final LedgerService ledgerService;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final SavingsFundConfiguration savingsFundConfiguration;

  @Transactional
  public List<Transaction> getTransactions(Person person) {
    User user = userService.findByPersonalCode(person.getPersonalCode()).orElseThrow();

    if (!savingsFundOnboardingService.isOnboardingCompleted(user)) {
      return List.of();
    }

    String isin = savingsFundConfiguration.getIsin();

    List<Transaction> subscriptions = mapEntries(user, SUBSCRIPTIONS, CONTRIBUTION_CASH, isin);
    List<Transaction> redemptions = mapEntries(user, REDEMPTIONS, SUBTRACTION, isin);

    return Stream.concat(subscriptions.stream(), redemptions.stream())
        .sorted(reverseOrder())
        .toList();
  }

  private List<Transaction> mapEntries(
      User user, UserAccount userAccount, CashFlow.Type type, String isin) {
    return ledgerService.getUserAccount(user, userAccount).getEntries().stream()
        .map(entry -> toTransaction(entry, type, isin))
        .toList();
  }

  private Transaction toTransaction(LedgerEntry entry, CashFlow.Type type, String isin) {
    LedgerTransaction ledgerTransaction = entry.getTransaction();

    return Transaction.builder()
        .id(ledgerTransaction.getId())
        .amount(entry.getAmount().negate())
        .currency(EUR)
        .time(ledgerTransaction.getTransactionDate())
        .isin(isin)
        .type(type)
        .units(ledgerTransaction.findUserFundUnits().orElseThrow())
        .nav(ledgerTransaction.findNavPerUnit().orElseThrow())
        .build();
  }
}
