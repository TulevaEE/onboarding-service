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
import java.math.BigDecimal;
import java.util.List;
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

  @Transactional
  public List<Transaction> getTransactions(AuthenticatedPerson person) {
    String ownerCode = person.getRoleCode();
    PartyType partyType = PartyType.from(person.getRoleType());

    if (!savingsFundOnboardingService.isOnboardingCompleted(ownerCode)) {
      return List.of();
    }

    String isin = savingsFundConfiguration.getIsin();

    List<Transaction> subscriptions =
        mapEntries(ownerCode, partyType, SUBSCRIPTIONS, CONTRIBUTION_CASH, isin);
    List<Transaction> redemptions =
        mapEntries(ownerCode, partyType, REDEMPTIONS, SUBTRACTION, isin);

    return Stream.concat(subscriptions.stream(), redemptions.stream())
        .sorted(reverseOrder())
        .toList();
  }

  private List<Transaction> mapEntries(
      String ownerCode,
      PartyType partyType,
      UserAccount userAccount,
      CashFlow.Type type,
      String isin) {
    return ledgerService.getPartyAccount(ownerCode, partyType, userAccount).getEntries().stream()
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
        .nav(toNavScale(ledgerTransaction.findNavPerUnit().orElseThrow()))
        .build();
  }

  private BigDecimal toNavScale(BigDecimal nav) {
    return nav.stripTrailingZeros().setScale(TKF100.getNavScale(), UNNECESSARY);
  }
}
