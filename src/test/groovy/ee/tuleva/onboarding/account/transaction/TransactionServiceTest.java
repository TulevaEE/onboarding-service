package ee.tuleva.onboarding.account.transaction;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.*;
import static ee.tuleva.onboarding.epis.cashflows.CashFlowFixture.cashFlowFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.account.CashFlowService;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.savings.fund.SavingsFundTransactionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

  @Mock private CashFlowService cashFlowService;
  @Mock private SavingsFundTransactionService savingsFundTransactionService;

  @InjectMocks private TransactionService service;

  @Test
  void filtersContributionsAndSubtractionsFromEpis() {
    var person = AuthenticatedPerson.builder().personalCode("38812121215").build();
    var cashFlowStatement = cashFlowFixture();

    when(cashFlowService.getCashFlowStatement(person)).thenReturn(cashFlowStatement);
    when(savingsFundTransactionService.getTransactions(person)).thenReturn(List.of());

    List<Transaction> transactions = service.getTransactions(person);

    assertThat(transactions).hasSize(3);
    assertThat(transactions)
        .allSatisfy(
            transaction ->
                assertThat(transaction.type())
                    .isIn(
                        SUBTRACTION, CONTRIBUTION_CASH, CONTRIBUTION_CASH_WORKPLACE, CONTRIBUTION));
  }

  @Test
  void mergesEpisAndSavingsFundTransactionsSortedByDateDescending() {
    var person = AuthenticatedPerson.builder().personalCode("38812121215").build();
    var cashFlowStatement = cashFlowFixture();

    Instant newestDate = Instant.parse("2099-01-01T00:00:00Z");
    var savingsTransaction =
        new Transaction(
            new BigDecimal("100.00"), EUR, newestDate, "EE0000003283", CONTRIBUTION_CASH, null);

    when(cashFlowService.getCashFlowStatement(person)).thenReturn(cashFlowStatement);
    when(savingsFundTransactionService.getTransactions(person))
        .thenReturn(List.of(savingsTransaction));

    List<Transaction> transactions = service.getTransactions(person);

    assertThat(transactions).hasSize(4);
    assertThat(transactions.getFirst()).isEqualTo(savingsTransaction);
  }

  @Test
  void returnsEmptyListWhenNoTransactions() {
    var person = AuthenticatedPerson.builder().personalCode("38812121215").build();
    var emptyStatement = ee.tuleva.onboarding.epis.cashflows.CashFlowStatement.builder().build();

    when(cashFlowService.getCashFlowStatement(person)).thenReturn(emptyStatement);
    when(savingsFundTransactionService.getTransactions(person)).thenReturn(List.of());

    List<Transaction> transactions = service.getTransactions(person);

    assertThat(transactions).isEmpty();
  }
}
