package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.SUBTRACTION;
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.*;
import static ee.tuleva.onboarding.ledger.UserAccount.REDEMPTIONS;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerAccountFixture.EntryFixture;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundTransactionServiceTest {

  @Mock private UserService userService;
  @Mock private LedgerService ledgerService;
  @Mock private SavingsFundOnboardingService savingsFundOnboardingService;
  @Mock private SavingsFundConfiguration savingsFundConfiguration;

  @InjectMocks private SavingsFundTransactionService service;

  @Test
  void returnsTransactionsFromLedger() {
    User user = sampleUser().build();
    String isin = "EE0000003283";

    when(userService.findByPersonalCode(user.getPersonalCode())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(user)).thenReturn(true);
    when(savingsFundConfiguration.getIsin()).thenReturn(isin);

    Instant olderDate = Instant.parse("2025-01-15T10:00:00Z");
    Instant newerDate = Instant.parse("2025-02-20T14:00:00Z");

    LedgerAccount subscriptionsAccount =
        subscriptionsAccountWithEntries(
            List.of(
                new EntryFixture(new BigDecimal("100.00"), olderDate),
                new EntryFixture(new BigDecimal("50.00"), newerDate)));

    LedgerAccount redemptionsAccount =
        redemptionsAccountWithEntries(
            List.of(new EntryFixture(new BigDecimal("25.00"), newerDate)));

    when(ledgerService.getUserAccount(user, SUBSCRIPTIONS)).thenReturn(subscriptionsAccount);
    when(ledgerService.getUserAccount(user, REDEMPTIONS)).thenReturn(redemptionsAccount);

    List<Transaction> transactions = service.getTransactions(user);

    assertThat(transactions).hasSize(3);
    assertThat(transactions).allSatisfy(transaction -> assertThat(transaction.id()).isNotNull());

    assertThat(transactions)
        .extracting(
            Transaction::amount,
            Transaction::currency,
            Transaction::time,
            Transaction::isin,
            Transaction::type,
            Transaction::units,
            Transaction::nav)
        .containsExactly(
            tuple(
                new BigDecimal("50.00"),
                EUR,
                newerDate,
                isin,
                CONTRIBUTION_CASH,
                new BigDecimal("5.00000"),
                new BigDecimal("10.0")),
            tuple(
                new BigDecimal("-25.00"),
                EUR,
                newerDate,
                isin,
                SUBTRACTION,
                new BigDecimal("2.50000"),
                new BigDecimal("10.0")),
            tuple(
                new BigDecimal("100.00"),
                EUR,
                olderDate,
                isin,
                CONTRIBUTION_CASH,
                new BigDecimal("10.00000"),
                new BigDecimal("10.0")));

    assertThat(transactions.stream().map(Transaction::id).distinct()).hasSize(3);
  }

  @Test
  void returnsEmptyListWhenNotOnboarded() {
    User user = sampleUser().build();

    when(userService.findByPersonalCode(user.getPersonalCode())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(user)).thenReturn(false);

    List<Transaction> transactions = service.getTransactions(user);

    assertThat(transactions).isEmpty();
  }

  @Test
  void returnsEmptyListWhenNoEntries() {
    User user = sampleUser().build();

    when(userService.findByPersonalCode(user.getPersonalCode())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(user)).thenReturn(true);
    when(savingsFundConfiguration.getIsin()).thenReturn("EE0000003283");

    LedgerAccount emptySubscriptions = subscriptionsAccountWithBalance(BigDecimal.ZERO);
    LedgerAccount emptyRedemptions = redemptionsAccountWithBalance(BigDecimal.ZERO);

    when(ledgerService.getUserAccount(user, SUBSCRIPTIONS)).thenReturn(emptySubscriptions);
    when(ledgerService.getUserAccount(user, REDEMPTIONS)).thenReturn(emptyRedemptions);

    List<Transaction> transactions = service.getTransactions(user);

    assertThat(transactions).isEmpty();
  }
}
