package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.epis.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.CashFlow.Type.SUBTRACTION;
import static ee.tuleva.onboarding.epis.CashFlow.Type.TRANSFER_IN;
import static ee.tuleva.onboarding.epis.CashFlow.Type.TRANSFER_OUT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNIT_TRANSFER;
import static ee.tuleva.onboarding.ledger.UserAccount.REDEMPTIONS;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.RoundingMode.UNNECESSARY;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.account.transaction.SavingsTransactions;
import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.CashFlow;
import ee.tuleva.onboarding.ledger.LedgerEntry;
import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.LedgerTransaction;
import ee.tuleva.onboarding.ledger.UserAccount;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.SavingsFundConfiguration;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingService;
import ee.tuleva.onboarding.savings.fund.nav.NavCalendar;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SavingsFundTransactionService implements SavingsTransactions {

  private static final int CENT_SCALE = 2;

  private final LedgerService ledgerService;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final SavingsFundConfiguration savingsFundConfiguration;
  private final RedemptionRequestRepository redemptionRequestRepository;
  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final NavCalendar navCalendar;

  @Transactional
  @Override
  public List<Transaction> getTransactions(AuthenticatedPerson person) {
    if (!savingsFundOnboardingService.isOnboardingCompleted(PartyId.from(person))) {
      return List.of();
    }

    PartyId partyId = PartyId.from(person);
    String ownerCode = person.getRoleCode();
    PartyType partyType = LedgerRefs.partyType(person.getRoleType());
    String isin = savingsFundConfiguration.getIsin();

    List<LedgerEntry> subscriptionEntries = entries(ownerCode, partyType, SUBSCRIPTIONS);
    List<LedgerEntry> redemptionEntries = entries(ownerCode, partyType, REDEMPTIONS);
    Map<UUID, SavingFundPayment> payments = payments(subscriptionEntries, partyId);
    Map<UUID, RedemptionRequest> redemptionRequests =
        redemptionRequests(redemptionEntries, partyId);

    return Stream.concat(
            subscriptionEntries.stream().map(entry -> toSubscription(entry, isin, payments)),
            redemptionEntries.stream().map(entry -> toRedemption(entry, isin, redemptionRequests)))
        .sorted(reverseOrder())
        .toList();
  }

  private List<LedgerEntry> entries(
      String ownerCode, PartyType partyType, UserAccount userAccount) {
    return List.copyOf(
        ledgerService.getPartyAccount(ownerCode, partyType, userAccount).getEntries());
  }

  private Map<UUID, SavingFundPayment> payments(List<LedgerEntry> entries, PartyId partyId) {
    Set<UUID> paymentIds = externalReferences(entries);

    if (paymentIds.isEmpty()) {
      return Map.of();
    }

    Map<UUID, SavingFundPayment> byPaymentId = new HashMap<>();
    savingFundPaymentRepository
        .findAllById(paymentIds)
        .forEach(
            payment -> {
              if (partyId.equals(payment.getPartyId())) {
                byPaymentId.put(payment.getId(), payment);
              }
            });
    return Map.copyOf(byPaymentId);
  }

  private Map<UUID, RedemptionRequest> redemptionRequests(
      List<LedgerEntry> entries, PartyId partyId) {
    Set<UUID> requestIds = externalReferences(entries);

    if (requestIds.isEmpty()) {
      return Map.of();
    }

    Map<UUID, RedemptionRequest> byRequestId = new HashMap<>();
    redemptionRequestRepository
        .findAllById(requestIds)
        .forEach(
            request -> {
              if (belongsTo(request, partyId)) {
                byRequestId.put(request.getId(), request);
              }
            });
    return Map.copyOf(byRequestId);
  }

  private static boolean belongsTo(RedemptionRequest request, PartyId partyId) {
    return partyId.type() == request.getPartyType()
        && partyId.code().equals(request.getPartyCode());
  }

  private static Set<UUID> externalReferences(List<LedgerEntry> entries) {
    return entries.stream()
        .map(entry -> entry.getTransaction().getExternalReference())
        .filter(Objects::nonNull)
        .collect(toSet());
  }

  private Transaction toSubscription(
      LedgerEntry entry, String isin, Map<UUID, SavingFundPayment> payments) {
    SavingFundPayment payment = find(payments, entry);

    return transaction(entry, CONTRIBUTION_CASH, isin)
        .settledTime(entry.getTransaction().getTransactionDate())
        .applicationTime(payment == null ? null : payment.getReceivedBefore())
        .counterpartyIban(payment == null ? null : payment.getRemitterIban())
        .build();
  }

  private Transaction toRedemption(
      LedgerEntry entry, String isin, Map<UUID, RedemptionRequest> redemptionRequests) {
    RedemptionRequest request = find(redemptionRequests, entry);

    return transaction(entry, SUBTRACTION, isin)
        .settledTime(
            request == null || request.getProcessedAt() == null
                ? entry.getTransaction().getTransactionDate()
                : request.getProcessedAt())
        .applicationTime(request == null ? null : request.getRequestedAt())
        .counterpartyIban(request == null ? null : request.getCustomerIban())
        .build();
  }

  private static <T> @Nullable T find(Map<UUID, T> byExternalReference, LedgerEntry entry) {
    UUID externalReference = entry.getTransaction().getExternalReference();
    return externalReference == null ? null : byExternalReference.get(externalReference);
  }

  private Transaction.TransactionBuilder transaction(
      LedgerEntry entry, CashFlow.Type cashFlowType, String isin) {
    LedgerTransaction ledgerTransaction = entry.getTransaction();
    CashFlow.Type type = typeOf(entry, cashFlowType);

    Transaction.TransactionBuilder transaction =
        Transaction.builder()
            .id(ledgerTransaction.getId())
            .amount(entry.getAmount().negate())
            .currency(EUR)
            .time(ledgerTransaction.getTransactionDate())
            .isin(isin)
            .type(type)
            .units(require(ledgerTransaction.findUserFundUnits(), "fundUnits", ledgerTransaction))
            .nav(navOf(ledgerTransaction))
            .acquisitionCost(acquisitionCostOf(type, ledgerTransaction));

    ledgerTransaction
        .findNavDate()
        .ifPresent(
            navDate ->
                transaction
                    .navDate(navDate)
                    .priceCalculationDate(navCalendar.calculationDateOf(navDate)));

    return transaction;
  }

  private static @Nullable BigDecimal navOf(LedgerTransaction ledgerTransaction) {
    Optional<BigDecimal> navPerUnit = ledgerTransaction.findNavPerUnit();

    if (ledgerTransaction.getTransactionType() == UNIT_TRANSFER) {
      return navPerUnit.map(SavingsFundTransactionService::toNavScale).orElse(null);
    }

    return toNavScale(require(navPerUnit, "navPerUnit", ledgerTransaction));
  }

  private static @Nullable BigDecimal acquisitionCostOf(
      CashFlow.Type type, LedgerTransaction ledgerTransaction) {
    return type == TRANSFER_IN
        ? ledgerTransaction
            .findRecipientAcquisitionCost()
            .map(SavingsFundTransactionService::toCentScale)
            .orElse(null)
        : null;
  }

  private static BigDecimal toCentScale(BigDecimal amount) {
    return amount.stripTrailingZeros().setScale(CENT_SCALE, UNNECESSARY);
  }

  private static CashFlow.Type typeOf(LedgerEntry entry, CashFlow.Type cashFlowType) {
    LedgerTransaction ledgerTransaction = entry.getTransaction();

    if (ledgerTransaction.getTransactionType() != UNIT_TRANSFER) {
      return cashFlowType;
    }

    BigDecimal unitsGained =
        require(entry.findOwnersFundUnitsChange(), "fundUnits", ledgerTransaction);
    return unitsGained.signum() < 0 ? TRANSFER_OUT : TRANSFER_IN;
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

  private static BigDecimal toNavScale(BigDecimal nav) {
    return nav.stripTrailingZeros().setScale(TKF100.getNavScale(), UNNECESSARY);
  }
}
