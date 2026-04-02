package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonLegalEntity;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.SUBTRACTION;
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.*;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.ledger.UserAccount.REDEMPTIONS;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerAccountFixture.EntryFixture;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.party.PartyId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundTransactionServiceTest {

  @Mock private LedgerService ledgerService;
  @Mock private SavingsFundOnboardingService savingsFundOnboardingService;
  @Mock private SavingsFundConfiguration savingsFundConfiguration;

  @InjectMocks private SavingsFundTransactionService service;

  private final AuthenticatedPerson person = sampleAuthenticatedPersonAndMember().build();
  private final String personalCode = person.getPersonalCode();

  @Test
  void returnsTransactionsFromLedger() {
    String isin = "EE0000003283";

    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(true);
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

    when(ledgerService.getPartyAccount(personalCode, PERSON, SUBSCRIPTIONS))
        .thenReturn(subscriptionsAccount);
    when(ledgerService.getPartyAccount(personalCode, PERSON, REDEMPTIONS))
        .thenReturn(redemptionsAccount);

    List<Transaction> transactions = service.getTransactions(person);

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
                new BigDecimal("10.0000")),
            tuple(
                new BigDecimal("-25.00"),
                EUR,
                newerDate,
                isin,
                SUBTRACTION,
                new BigDecimal("2.50000"),
                new BigDecimal("10.0000")),
            tuple(
                new BigDecimal("100.00"),
                EUR,
                olderDate,
                isin,
                CONTRIBUTION_CASH,
                new BigDecimal("10.00000"),
                new BigDecimal("10.0000")));

    assertThat(transactions.stream().map(Transaction::id).distinct()).hasSize(3);
  }

  @Test
  void returnsEmptyListWhenNotOnboarded() {
    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(false);

    assertThat(service.getTransactions(person)).isEmpty();
  }

  @Test
  void returnsEmptyListWhenNoEntries() {
    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(true);
    when(savingsFundConfiguration.getIsin()).thenReturn("EE0000003283");

    when(ledgerService.getPartyAccount(personalCode, PERSON, SUBSCRIPTIONS))
        .thenReturn(subscriptionsAccountWithBalance(BigDecimal.ZERO));
    when(ledgerService.getPartyAccount(personalCode, PERSON, REDEMPTIONS))
        .thenReturn(redemptionsAccountWithBalance(BigDecimal.ZERO));

    assertThat(service.getTransactions(person)).isEmpty();
  }

  @Test
  void returnsTransactionsForLegalEntity() {
    var legalEntityPerson = sampleAuthenticatedPersonLegalEntity().build();

    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(true);
    when(savingsFundConfiguration.getIsin()).thenReturn("EE0000003283");

    when(ledgerService.getPartyAccount("12345678", LEGAL_ENTITY, SUBSCRIPTIONS))
        .thenReturn(
            subscriptionsAccountWithEntries(
                List.of(
                    new EntryFixture(
                        new BigDecimal("100.00"), Instant.parse("2025-01-15T10:00:00Z")))));
    when(ledgerService.getPartyAccount("12345678", LEGAL_ENTITY, REDEMPTIONS))
        .thenReturn(redemptionsAccountWithBalance(BigDecimal.ZERO));

    List<Transaction> transactions = service.getTransactions(legalEntityPerson);

    assertThat(transactions).hasSize(1);
    assertThat(transactions.get(0).amount()).isEqualTo(new BigDecimal("100.00"));
  }
}
