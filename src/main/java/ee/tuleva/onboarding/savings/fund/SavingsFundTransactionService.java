package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.SUBTRACTION;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.UserAccount.REDEMPTIONS;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static java.math.RoundingMode.UNNECESSARY;
import static java.util.Comparator.reverseOrder;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final RedemptionRequestRepository redemptionRequestRepository;

  @Transactional
  public List<Transaction> getTransactions(AuthenticatedPerson person) {
    String ownerCode = person.getRoleCode();
    PartyType partyType = PartyType.from(person.getRoleType());

    if (!savingsFundOnboardingService.isOnboardingCompleted(person.toPartyId())) {
      return List.of();
    }

    String isin = savingsFundConfiguration.getIsin();

    List<LedgerEntry> subscriptionEntries = entriesOf(ownerCode, partyType, SUBSCRIPTIONS);
    List<LedgerEntry> redemptionEntries = entriesOf(ownerCode, partyType, REDEMPTIONS);

    List<Transaction> subscriptions =
        mapEntries(
            subscriptionEntries,
            CONTRIBUTION_CASH,
            isin,
            remitterIbans(referencesOf(subscriptionEntries)));
    List<Transaction> redemptions =
        mapEntries(
            redemptionEntries, SUBTRACTION, isin, customerIbans(referencesOf(redemptionEntries)));

    return Stream.concat(subscriptions.stream(), redemptions.stream())
        .sorted(reverseOrder())
        .toList();
  }

  private List<LedgerEntry> entriesOf(
      String ownerCode, PartyType partyType, UserAccount userAccount) {
    return List.copyOf(
        ledgerService.getPartyAccount(ownerCode, partyType, userAccount).getEntries());
  }

  private Set<UUID> referencesOf(List<LedgerEntry> entries) {
    return entries.stream()
        .map(entry -> entry.getTransaction().getExternalReference())
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private Map<UUID, String> remitterIbans(Set<UUID> paymentIds) {
    if (paymentIds.isEmpty()) {
      return Map.of();
    }
    return savingFundPaymentRepository.findRemitterIbansByIds(paymentIds);
  }

  private Map<UUID, String> customerIbans(Set<UUID> requestIds) {
    if (requestIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, String> ibans = new HashMap<>();
    redemptionRequestRepository
        .findAllById(requestIds)
        .forEach(request -> ibans.put(request.getId(), request.getCustomerIban()));
    return ibans;
  }

  private List<Transaction> mapEntries(
      List<LedgerEntry> entries, CashFlow.Type type, String isin, Map<UUID, String> ibans) {
    return entries.stream().map(entry -> toTransaction(entry, type, isin, ibans)).toList();
  }

  private Transaction toTransaction(
      LedgerEntry entry, CashFlow.Type type, String isin, Map<UUID, String> ibans) {
    LedgerTransaction ledgerTransaction = entry.getTransaction();
    UUID externalReference = ledgerTransaction.getExternalReference();

    return Transaction.builder()
        .counterpartyIban(externalReference == null ? null : ibans.get(externalReference))
        .id(ledgerTransaction.getId())
        .amount(entry.getAmount().negate())
        .currency(EUR)
        .time(ledgerTransaction.getTransactionDate())
        .isin(isin)
        .type(type)
        .units(ledgerTransaction.findUserFundUnits().orElseThrow())
        .nav(toNavScale(ledgerTransaction.findNavPerUnit().orElseThrow()))
        .build();
  }

  private BigDecimal toNavScale(BigDecimal nav) {
    return nav.stripTrailingZeros().setScale(TKF100.getNavScale(), UNNECESSARY);
  }
}
