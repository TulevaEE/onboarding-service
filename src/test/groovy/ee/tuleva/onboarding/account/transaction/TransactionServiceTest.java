package ee.tuleva.onboarding.account.transaction;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonLegalEntity;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.epis.CashFlow.Type.*;
import static ee.tuleva.onboarding.epis.CashFlowFixture.cashFlowFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.account.CashFlowService;
import ee.tuleva.onboarding.auth.role.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

  @Mock private CashFlowService cashFlowService;
  @Mock private SavingsTransactions savingsTransactions;

  @InjectMocks private TransactionService service;

  @Test
  void
      appsGetTheSavingsFundTransactionsWithoutAskingPensionikeskusEvenForAPersonActingAsThemselves() {
    var person = sampleAuthenticatedPersonAndMember().build();
    var savingsTransaction = savingsFundPaymentFrom(null);
    given(savingsTransactions.getTransactions(person)).willReturn(List.of(savingsTransaction));

    assertThat(service.getTransactionsSharedWithApps(person)).containsExactly(savingsTransaction);
    verifyNoInteractions(cashFlowService);
  }

  @Test
  void appsGetNoBankAccountNumberOfWhoeverPaidOrReceivedTheMoney() {
    var person = sampleAuthenticatedPersonAndMember().build();
    given(savingsTransactions.getTransactions(person))
        .willReturn(List.of(savingsFundPaymentFrom("EE001010220000000000")));

    assertThat(service.getTransactionsSharedWithApps(person))
        .containsExactly(savingsFundPaymentFrom(null));
  }

  private static Transaction savingsFundPaymentFrom(@Nullable String counterpartyIban) {
    return Transaction.builder()
        .id(UUID.fromString("9c3f5a7b-1d2e-4f6a-8b9c-0d1e2f3a4b5c"))
        .amount(new BigDecimal("100.00"))
        .currency(EUR)
        .time(Instant.parse("2026-10-02T14:00:00Z"))
        .counterpartyIban(counterpartyIban)
        .isin("EE0000003283")
        .type(CONTRIBUTION_CASH)
        .units(new BigDecimal("8.13008"))
        .nav(new BigDecimal("12.3000"))
        .build();
  }

  @Test
  void filtersContributionsAndSubtractionsFromEpis() {
    var person = sampleAuthenticatedPersonAndMember().build();
    var cashFlowStatement = cashFlowFixture();

    when(cashFlowService.getCashFlowStatement(person)).thenReturn(cashFlowStatement);
    when(savingsTransactions.getTransactions(person)).thenReturn(List.of());

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
    var person = sampleAuthenticatedPersonAndMember().build();
    var cashFlowStatement = cashFlowFixture();

    Instant newestDate = Instant.parse("2099-01-01T00:00:00Z");
    var savingsTransaction =
        Transaction.builder()
            .amount(new BigDecimal("100.00"))
            .currency(EUR)
            .time(newestDate)
            .isin("EE0000003283")
            .type(CONTRIBUTION_CASH)
            .units(new BigDecimal("10.00000"))
            .nav(new BigDecimal("10.0000"))
            .build();

    when(cashFlowService.getCashFlowStatement(person)).thenReturn(cashFlowStatement);
    when(savingsTransactions.getTransactions(person)).thenReturn(List.of(savingsTransaction));

    List<Transaction> transactions = service.getTransactions(person);

    assertThat(transactions).hasSize(4);
    assertThat(transactions.getFirst()).isEqualTo(savingsTransaction);
  }

  @Test
  void keepsSavingsFundTransfersThatTheEpisContributionFilterWouldNeverLetThrough() {
    var person = sampleAuthenticatedPersonAndMember().build();
    var cashFlowStatement = cashFlowFixture();

    var transferIn =
        Transaction.builder()
            .amount(new BigDecimal("400.00"))
            .currency(EUR)
            .time(Instant.parse("2099-01-02T00:00:00Z"))
            .isin("EE0000003283")
            .type(TRANSFER_IN)
            .units(new BigDecimal("40.00000"))
            .nav(new BigDecimal("10.0000"))
            .acquisitionCost(new BigDecimal("0.00"))
            .build();
    var transferOut =
        Transaction.builder()
            .amount(new BigDecimal("-400.00"))
            .currency(EUR)
            .time(Instant.parse("2099-01-01T00:00:00Z"))
            .isin("EE0000003283")
            .type(TRANSFER_OUT)
            .units(new BigDecimal("40.00000"))
            .nav(new BigDecimal("10.0000"))
            .build();

    when(cashFlowService.getCashFlowStatement(person)).thenReturn(cashFlowStatement);
    when(savingsTransactions.getTransactions(person)).thenReturn(List.of(transferIn, transferOut));

    List<Transaction> transactions = service.getTransactions(person);

    assertThat(transactions).hasSize(5);
    assertThat(transactions).startsWith(transferIn, transferOut);
  }

  @Test
  void returnsOnlySavingsTransactionsWhenRepresentingAnotherPerson() {
    var person =
        sampleAuthenticatedPersonAndMember()
            .role(new Role(PERSON, "61506150006", "Child Name"))
            .build();
    var savingsTransaction =
        Transaction.builder()
            .amount(new BigDecimal("100.00"))
            .currency(EUR)
            .time(Instant.parse("2099-01-01T00:00:00Z"))
            .isin("EE0000003283")
            .type(CONTRIBUTION_CASH)
            .units(new BigDecimal("10.00000"))
            .nav(new BigDecimal("10.0000"))
            .build();

    when(savingsTransactions.getTransactions(person)).thenReturn(List.of(savingsTransaction));

    List<Transaction> transactions = service.getTransactions(person);

    assertThat(transactions).containsExactly(savingsTransaction);
    verifyNoInteractions(cashFlowService);
  }

  @Test
  void returnsOnlySavingsTransactionsWhenRepresentingLegalEntity() {
    var person = sampleAuthenticatedPersonLegalEntity().build();

    when(savingsTransactions.getTransactions(person)).thenReturn(List.of());

    List<Transaction> transactions = service.getTransactions(person);

    assertThat(transactions).isEmpty();
    verifyNoInteractions(cashFlowService);
  }

  @Test
  void returnsEmptyListWhenNoTransactions() {
    var person = sampleAuthenticatedPersonAndMember().build();
    var emptyStatement = ee.tuleva.onboarding.epis.CashFlowStatement.builder().build();

    when(cashFlowService.getCashFlowStatement(person)).thenReturn(emptyStatement);
    when(savingsTransactions.getTransactions(person)).thenReturn(List.of());

    List<Transaction> transactions = service.getTransactions(person);

    assertThat(transactions).isEmpty();
  }
}
