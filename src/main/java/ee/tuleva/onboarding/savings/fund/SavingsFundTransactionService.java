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
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestRepository;
import java.math.BigDecimal;
import java.time.Instant;
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

  @Transactional
  public List<Transaction> getTransactions(AuthenticatedPerson person) {
    String ownerCode = person.getRoleCode();
    PartyType partyType = PartyType.from(person.getRoleType());

    if (!savingsFundOnboardingService.isOnboardingCompleted(person.toPartyId())) {
      return List.of();
    }

    String isin = savingsFundConfiguration.getIsin();

    List<Transaction> subscriptions =
        entries(ownerCode, partyType, SUBSCRIPTIONS).stream()
            .map(entry -> toTransaction(entry, CONTRIBUTION_CASH, isin, Map.of()))
            .toList();

    List<LedgerEntry> redemptionEntries = entries(ownerCode, partyType, REDEMPTIONS);
    Map<UUID, Instant> payoutTimes = payoutTimes(redemptionEntries);
    List<Transaction> redemptions =
        redemptionEntries.stream()
            .map(entry -> toTransaction(entry, SUBTRACTION, isin, payoutTimes))
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

  private Map<UUID, Instant> payoutTimes(List<LedgerEntry> redemptionEntries) {
    Set<UUID> requestIds =
        redemptionEntries.stream()
            .map(entry -> entry.getTransaction().getExternalReference())
            .filter(Objects::nonNull)
            .collect(toSet());

    if (requestIds.isEmpty()) {
      return Map.of();
    }

    Map<UUID, Instant> byRequestId = new HashMap<>();
    redemptionRequestRepository
        .findAllById(requestIds)
        .forEach(
            request -> {
              if (request.getProcessedAt() != null) {
                byRequestId.put(request.getId(), request.getProcessedAt());
              }
            });
    return Map.copyOf(byRequestId);
  }

  private Transaction toTransaction(
      LedgerEntry entry, CashFlow.Type type, String isin, Map<UUID, Instant> payoutTimes) {
    LedgerTransaction ledgerTransaction = entry.getTransaction();
    UUID externalReference = ledgerTransaction.getExternalReference();

    return Transaction.builder()
        .id(ledgerTransaction.getId())
        .amount(entry.getAmount().negate())
        .currency(EUR)
        .time(ledgerTransaction.getTransactionDate())
        .priceTime(ledgerTransaction.getTransactionDate())
        .settledTime(externalReference == null ? null : payoutTimes.get(externalReference))
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
