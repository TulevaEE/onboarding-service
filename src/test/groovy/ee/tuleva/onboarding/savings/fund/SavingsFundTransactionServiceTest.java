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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerAccountFixture.EntryFixture;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
  @Mock private RedemptionRequestRepository redemptionRequestRepository;
  @Mock private SavingFundPaymentRepository savingFundPaymentRepository;

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
            Transaction::priceTime,
            Transaction::settledTime,
            Transaction::isin,
            Transaction::type,
            Transaction::units,
            Transaction::nav)
        .containsExactly(
            tuple(
                new BigDecimal("50.00"),
                EUR,
                newerDate,
                newerDate,
                newerDate,
                isin,
                CONTRIBUTION_CASH,
                new BigDecimal("5.00000"),
                new BigDecimal("10.0000")),
            tuple(
                new BigDecimal("-25.00"),
                EUR,
                newerDate,
                newerDate,
                newerDate,
                isin,
                SUBTRACTION,
                new BigDecimal("2.50000"),
                new BigDecimal("10.0000")),
            tuple(
                new BigDecimal("100.00"),
                EUR,
                olderDate,
                olderDate,
                olderDate,
                isin,
                CONTRIBUTION_CASH,
                new BigDecimal("10.00000"),
                new BigDecimal("10.0000")));

    assertThat(transactions.stream().map(Transaction::id).distinct()).hasSize(3);
  }

  @Test
  void mapsRedemptionSettledTimeFromPayoutProcessedAt() {
    String isin = "EE0000003283";
    UUID refA = UUID.randomUUID();
    UUID refB = UUID.randomUUID();
    UUID danglingRef = UUID.randomUUID();
    Instant bookingTimeA = Instant.parse("2025-03-01T10:00:00Z");
    Instant bookingTimeB = Instant.parse("2025-12-31T10:00:00Z");
    Instant bookingTimeC = Instant.parse("2025-06-15T10:00:00Z");
    Instant processedAtA = Instant.parse("2025-03-05T09:00:00Z");

    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(true);
    when(savingsFundConfiguration.getIsin()).thenReturn(isin);

    when(ledgerService.getPartyAccount(personalCode, PERSON, SUBSCRIPTIONS))
        .thenReturn(subscriptionsAccountWithBalance(BigDecimal.ZERO));
    when(ledgerService.getPartyAccount(personalCode, PERSON, REDEMPTIONS))
        .thenReturn(
            redemptionsAccountWithEntries(
                List.of(
                    new EntryFixture(
                        new BigDecimal("25.00"), bookingTimeA, new BigDecimal("10.0"), refA),
                    new EntryFixture(
                        new BigDecimal("10.00"), bookingTimeB, new BigDecimal("10.0"), refB),
                    new EntryFixture(
                        new BigDecimal("15.00"),
                        bookingTimeC,
                        new BigDecimal("10.0"),
                        danglingRef))));

    RedemptionRequest requestA =
        RedemptionRequest.builder()
            .id(refA)
            .partyType(PartyId.Type.PERSON)
            .partyCode(personalCode)
            .processedAt(processedAtA)
            .build();
    RedemptionRequest requestB =
        RedemptionRequest.builder()
            .id(refB)
            .partyType(PartyId.Type.PERSON)
            .partyCode(personalCode)
            .build();
    when(redemptionRequestRepository.findAllById(Set.of(refA, refB, danglingRef)))
        .thenReturn(List.of(requestA, requestB));

    List<Transaction> transactions = service.getTransactions(person);

    assertThat(transactions)
        .extracting(Transaction::amount, Transaction::time, Transaction::settledTime)
        .containsExactly(
            tuple(new BigDecimal("-10.00"), bookingTimeB, bookingTimeB),
            tuple(new BigDecimal("-15.00"), bookingTimeC, bookingTimeC),
            tuple(new BigDecimal("-25.00"), bookingTimeA, processedAtA));
  }

  @Test
  void carriesTheAccountAPaymentCameFrom() {
    String isin = "EE0000003283";
    UUID paymentId = UUID.randomUUID();
    Instant bookingTime = Instant.parse("2025-03-01T10:00:00Z");

    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(true);
    when(savingsFundConfiguration.getIsin()).thenReturn(isin);
    when(ledgerService.getPartyAccount(personalCode, PERSON, SUBSCRIPTIONS))
        .thenReturn(
            subscriptionsAccountWithEntries(
                List.of(
                    new EntryFixture(
                        new BigDecimal("-100.00"),
                        bookingTime,
                        new BigDecimal("10.0"),
                        paymentId))));
    when(ledgerService.getPartyAccount(personalCode, PERSON, REDEMPTIONS))
        .thenReturn(redemptionsAccountWithEntries(List.of()));
    when(savingFundPaymentRepository.findAllById(Set.of(paymentId)))
        .thenReturn(
            List.of(
                SavingFundPayment.builder()
                    .id(paymentId)
                    .partyId(person.toPartyId())
                    .remitterIban("EE123456789012345678")
                    .build()));

    TransactionsWithCounterparties result = service.getTransactionsWithCounterpartyIbans(person);

    assertThat(result.transactions()).hasSize(1);
    assertThat(result.counterpartyIbans())
        .containsExactly(entry(result.transactions().getFirst().id(), "EE123456789012345678"));
  }

  @Test
  void leavesOutTheAccountOfAPaymentBelongingToAnotherParty() {
    UUID paymentId = UUID.randomUUID();
    Instant bookingTime = Instant.parse("2025-03-01T10:00:00Z");

    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(true);
    when(savingsFundConfiguration.getIsin()).thenReturn("EE0000003283");
    when(ledgerService.getPartyAccount(personalCode, PERSON, SUBSCRIPTIONS))
        .thenReturn(
            subscriptionsAccountWithEntries(
                List.of(
                    new EntryFixture(
                        new BigDecimal("-100.00"),
                        bookingTime,
                        new BigDecimal("10.0"),
                        paymentId))));
    when(ledgerService.getPartyAccount(personalCode, PERSON, REDEMPTIONS))
        .thenReturn(redemptionsAccountWithEntries(List.of()));
    when(savingFundPaymentRepository.findAllById(Set.of(paymentId)))
        .thenReturn(
            List.of(
                SavingFundPayment.builder()
                    .id(paymentId)
                    .partyId(new PartyId(PartyId.Type.PERSON, "38888888888"))
                    .remitterIban("EE123456789012345678")
                    .build()));

    TransactionsWithCounterparties result = service.getTransactionsWithCounterpartyIbans(person);

    assertThat(result.transactions()).hasSize(1);
    assertThat(result.counterpartyIbans()).isEmpty();
  }

  @Test
  void doesNotLookUpPaymentsWhenNoSubscriptionCarriesAReference() {
    Instant bookingTime = Instant.parse("2025-03-01T10:00:00Z");

    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(true);
    when(savingsFundConfiguration.getIsin()).thenReturn("EE0000003283");
    when(ledgerService.getPartyAccount(personalCode, PERSON, SUBSCRIPTIONS))
        .thenReturn(
            subscriptionsAccountWithEntries(
                List.of(new EntryFixture(new BigDecimal("-100.00"), bookingTime))));
    when(ledgerService.getPartyAccount(personalCode, PERSON, REDEMPTIONS))
        .thenReturn(redemptionsAccountWithEntries(List.of()));

    TransactionsWithCounterparties result = service.getTransactionsWithCounterpartyIbans(person);

    assertThat(result.transactions()).hasSize(1);
    assertThat(result.counterpartyIbans()).isEmpty();
    verifyNoInteractions(savingFundPaymentRepository);
  }

  @Test
  void carriesTheAccountARedemptionWasPaidTo() {
    String isin = "EE0000003283";
    UUID requestId = UUID.randomUUID();
    Instant bookingTime = Instant.parse("2025-03-01T10:00:00Z");

    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(true);
    when(savingsFundConfiguration.getIsin()).thenReturn(isin);
    when(ledgerService.getPartyAccount(personalCode, PERSON, SUBSCRIPTIONS))
        .thenReturn(subscriptionsAccountWithBalance(BigDecimal.ZERO));
    when(ledgerService.getPartyAccount(personalCode, PERSON, REDEMPTIONS))
        .thenReturn(
            redemptionsAccountWithEntries(
                List.of(
                    new EntryFixture(
                        new BigDecimal("25.00"), bookingTime, new BigDecimal("10.0"), requestId))));
    when(redemptionRequestRepository.findAllById(Set.of(requestId)))
        .thenReturn(
            List.of(
                RedemptionRequest.builder()
                    .id(requestId)
                    .partyType(PartyId.Type.PERSON)
                    .partyCode(personalCode)
                    .customerIban("EE111111111111111111")
                    .build()));

    TransactionsWithCounterparties result = service.getTransactionsWithCounterpartyIbans(person);

    assertThat(result.transactions()).hasSize(1);
    assertThat(result.counterpartyIbans())
        .containsExactly(entry(result.transactions().getFirst().id(), "EE111111111111111111"));
  }

  @Test
  void leavesOutTheAccountOfATransactionWeCannotTraceToOne() {
    UUID requestId = UUID.randomUUID();
    Instant bookingTime = Instant.parse("2025-03-01T10:00:00Z");

    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(true);
    when(savingsFundConfiguration.getIsin()).thenReturn("EE0000003283");
    when(ledgerService.getPartyAccount(personalCode, PERSON, SUBSCRIPTIONS))
        .thenReturn(subscriptionsAccountWithBalance(BigDecimal.ZERO));
    when(ledgerService.getPartyAccount(personalCode, PERSON, REDEMPTIONS))
        .thenReturn(
            redemptionsAccountWithEntries(
                List.of(
                    new EntryFixture(
                        new BigDecimal("25.00"), bookingTime, new BigDecimal("10.0"), requestId))));
    when(redemptionRequestRepository.findAllById(Set.of(requestId)))
        .thenReturn(
            List.of(
                RedemptionRequest.builder()
                    .id(requestId)
                    .partyType(PartyId.Type.PERSON)
                    .partyCode(personalCode)
                    .build()));

    TransactionsWithCounterparties result = service.getTransactionsWithCounterpartyIbans(person);

    assertThat(result.transactions()).hasSize(1);
    assertThat(result.counterpartyIbans()).isEmpty();
  }

  @Test
  void doesNotLookUpCounterpartyAccountsForThePlainTransactionList() {
    UUID paymentId = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    Instant bookingTime = Instant.parse("2025-03-01T10:00:00Z");
    Instant processedAt = Instant.parse("2025-03-05T09:00:00Z");

    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(true);
    when(savingsFundConfiguration.getIsin()).thenReturn("EE0000003283");
    when(ledgerService.getPartyAccount(personalCode, PERSON, SUBSCRIPTIONS))
        .thenReturn(
            subscriptionsAccountWithEntries(
                List.of(
                    new EntryFixture(
                        new BigDecimal("100.00"),
                        bookingTime,
                        new BigDecimal("10.0"),
                        paymentId))));
    when(ledgerService.getPartyAccount(personalCode, PERSON, REDEMPTIONS))
        .thenReturn(
            redemptionsAccountWithEntries(
                List.of(
                    new EntryFixture(
                        new BigDecimal("25.00"), bookingTime, new BigDecimal("10.0"), requestId))));
    when(redemptionRequestRepository.findAllById(Set.of(requestId)))
        .thenReturn(
            List.of(
                RedemptionRequest.builder()
                    .id(requestId)
                    .partyType(PartyId.Type.PERSON)
                    .partyCode(personalCode)
                    .customerIban("EE111111111111111111")
                    .processedAt(processedAt)
                    .build()));

    List<Transaction> transactions = service.getTransactions(person);

    assertThat(transactions)
        .extracting(Transaction::amount, Transaction::settledTime)
        .containsExactly(
            tuple(new BigDecimal("100.00"), bookingTime),
            tuple(new BigDecimal("-25.00"), processedAt));
    verifyNoInteractions(savingFundPaymentRepository);
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
  void failsWhenLedgerTransactionHasNoFundUnits() {
    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(true);
    when(savingsFundConfiguration.getIsin()).thenReturn("EE0000003283");
    when(ledgerService.getPartyAccount(personalCode, PERSON, SUBSCRIPTIONS))
        .thenReturn(
            subscriptionsAccountWithoutFundUnits(
                new EntryFixture(new BigDecimal("100.00"), Instant.parse("2025-01-15T10:00:00Z"))));
    when(ledgerService.getPartyAccount(personalCode, PERSON, REDEMPTIONS))
        .thenReturn(redemptionsAccountWithBalance(BigDecimal.ZERO));

    assertThatThrownBy(() -> service.getTransactions(person))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void failsWhenLedgerTransactionHasNoNavPerUnit() {
    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(true);
    when(savingsFundConfiguration.getIsin()).thenReturn("EE0000003283");
    when(ledgerService.getPartyAccount(personalCode, PERSON, SUBSCRIPTIONS))
        .thenReturn(
            subscriptionsAccountWithoutNavPerUnit(
                new EntryFixture(new BigDecimal("100.00"), Instant.parse("2025-01-15T10:00:00Z"))));
    when(ledgerService.getPartyAccount(personalCode, PERSON, REDEMPTIONS))
        .thenReturn(redemptionsAccountWithBalance(BigDecimal.ZERO));

    assertThatThrownBy(() -> service.getTransactions(person))
        .isInstanceOf(IllegalStateException.class);
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
