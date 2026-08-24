package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.SUBTRACTION;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.UserAccount.REDEMPTIONS;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static java.math.RoundingMode.UNNECESSARY;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import ee.tuleva.onboarding.ledger.LedgerEntry;
import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.LedgerTransaction;
import ee.tuleva.onboarding.ledger.UserAccount;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SavingsFundTransactionService {

  private final LedgerService ledgerService;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final SavingsFundConfiguration savingsFundConfiguration;
  private final RedemptionRequestRepository redemptionRequestRepository;
  private final SavingFundPaymentRepository savingFundPaymentRepository;

  @Transactional
  public List<Transaction> getTransactions(AuthenticatedPerson person) {
    String ownerCode = person.getRoleCode();
    PartyType partyType = PartyType.from(person.getRoleType());

    if (!savingsFundOnboardingService.isOnboardingCompleted(person.toPartyId())) {
      return List.of();
    }

    String isin = savingsFundConfiguration.getIsin();

    List<LedgerEntry> subscriptionEntries = entries(ownerCode, partyType, SUBSCRIPTIONS);
    Map<UUID, String> payerIbans = payerIbans(subscriptionEntries, person.toPartyId());
    List<Transaction> subscriptions =
        subscriptionEntries.stream()
            .map(entry -> toTransaction(entry, CONTRIBUTION_CASH, isin, Map.of(), payerIbans))
            .toList();

    List<LedgerEntry> redemptionEntries = entries(ownerCode, partyType, REDEMPTIONS);
    List<RedemptionRequest> redemptionRequests = redemptionRequests(redemptionEntries);
    Map<UUID, Instant> payoutTimes = payoutTimes(redemptionRequests);
    Map<UUID, String> payoutIbans = payoutIbans(redemptionRequests);
    List<Transaction> redemptions =
        redemptionEntries.stream()
            .map(entry -> toTransaction(entry, SUBTRACTION, isin, payoutTimes, payoutIbans))
            .toList();

    return Stream.concat(subscriptions.stream(), redemptions.stream())
        .sorted(reverseOrder())
        .toList();
  }

  private List<LedgerEntry> entries(
      String ownerCode, PartyType partyType, UserAccount userAccount) {
    return List.copyOf(
        ledgerService.getPartyAccount(ownerCode, partyType, userAccount).getEntries());
  }

  private List<RedemptionRequest> redemptionRequests(List<LedgerEntry> redemptionEntries) {
    Set<UUID> requestIds = externalReferences(redemptionEntries);

    if (requestIds.isEmpty()) {
      return List.of();
    }

    List<RedemptionRequest> requests = new ArrayList<>();
    redemptionRequestRepository.findAllById(requestIds).forEach(requests::add);
    return List.copyOf(requests);
  }

  private static Map<UUID, Instant> payoutTimes(List<RedemptionRequest> redemptionRequests) {
    Map<UUID, Instant> byRequestId = new HashMap<>();
    redemptionRequests.forEach(
        request -> {
          if (request.getProcessedAt() != null) {
            byRequestId.put(request.getId(), request.getProcessedAt());
          }
        });
    return Map.copyOf(byRequestId);
  }

  private static Map<UUID, String> payoutIbans(List<RedemptionRequest> redemptionRequests) {
    Map<UUID, String> byRequestId = new HashMap<>();
    redemptionRequests.forEach(
        request -> {
          if (request.getCustomerIban() != null) {
            byRequestId.put(request.getId(), request.getCustomerIban());
          }
        });
    return Map.copyOf(byRequestId);
  }

  private Map<UUID, String> payerIbans(List<LedgerEntry> subscriptionEntries, PartyId partyId) {
    if (externalReferences(subscriptionEntries).isEmpty()) {
      return Map.of();
    }

    Map<UUID, String> byPaymentId = new HashMap<>();
    savingFundPaymentRepository
        .findPayments(partyId)
        .forEach(
            payment -> {
              if (payment.getId() != null && payment.getRemitterIban() != null) {
                byPaymentId.put(payment.getId(), payment.getRemitterIban());
              }
            });
    return Map.copyOf(byPaymentId);
  }

  private static Set<UUID> externalReferences(List<LedgerEntry> entries) {
    return entries.stream()
        .map(entry -> entry.getTransaction().getExternalReference())
        .filter(Objects::nonNull)
        .collect(toSet());
  }

  private Transaction toTransaction(
      LedgerEntry entry,
      CashFlow.Type type,
      String isin,
      Map<UUID, Instant> payoutTimes,
      Map<UUID, String> counterpartyIbans) {
    LedgerTransaction ledgerTransaction = entry.getTransaction();
    UUID externalReference = ledgerTransaction.getExternalReference();

    return Transaction.builder()
        .id(ledgerTransaction.getId())
        .amount(entry.getAmount().negate())
        .currency(EUR)
        .time(ledgerTransaction.getTransactionDate())
        .priceTime(ledgerTransaction.getTransactionDate())
        .settledTime(externalReference == null ? null : payoutTimes.get(externalReference))
        .counterpartyIban(
            externalReference == null ? null : counterpartyIbans.get(externalReference))
        .isin(isin)
        .type(type)
        .units(require(ledgerTransaction.findUserFundUnits(), "fundUnits", ledgerTransaction))
        .nav(
            toNavScale(
                require(ledgerTransaction.findNavPerUnit(), "navPerUnit", ledgerTransaction)))
        .build();
  }

  private static BigDecimal require(
      Optional<BigDecimal> value, String field, LedgerTransaction ledgerTransaction) {
    return value.orElseThrow(
        () ->
            new IllegalStateException(
                "Ledger transaction missing value: field=%s, transactionId=%s, transactionDate=%s"
                    .formatted(
                        field, ledgerTransaction.getId(), ledgerTransaction.getTransactionDate())));
  }

  private BigDecimal toNavScale(BigDecimal nav) {
    return nav.stripTrailingZeros().setScale(TKF100.getNavScale(), UNNECESSARY);
  }
}
