package ee.tuleva.onboarding.account;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.UserAccount.*;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.UserAccount;
import ee.tuleva.onboarding.savings.fund.SavingsFundConfiguration;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
import ee.tuleva.onboarding.savings.fund.nav.FundNavProvider;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsFundStatementService {

  private final LedgerService ledgerService;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final FundNavProvider navProvider;
  private final FundRepository fundRepository;
  private final SavingsFundConfiguration savingsFundConfiguration;

  public Optional<FundBalance> getAccountStatement(AuthenticatedPerson person) {
    String ownerCode = person.getRoleCode();
    PartyType partyType = PartyType.from(person.getRoleType());

    if (savingsFundOnboardingService.isOnboardingCompleted(person.toPartyId())) {
      return Optional.of(
          statement(
              account ->
                  ledgerService.getPartyAccount(ownerCode, partyType, account).getBalance()));
    }
    return ledgerService
        .findPartyAccount(ownerCode, partyType, FUND_UNITS)
        .map(
            fundUnitsAccount ->
                statement(
                    account ->
                        account == FUND_UNITS
                            ? fundUnitsAccount.getBalance()
                            : ledgerService
                                .findPartyAccount(ownerCode, partyType, account)
                                .map(LedgerAccount::getBalance)
                                .orElse(BigDecimal.ZERO)));
  }

  private FundBalance statement(Function<UserAccount, BigDecimal> balances) {
    BigDecimal nav = getNAV();

    BigDecimal units = balances.apply(FUND_UNITS).negate();
    BigDecimal value = nav.multiply(units).setScale(2, HALF_UP);

    BigDecimal reservedUnits = balances.apply(FUND_UNITS_RESERVED).negate();
    BigDecimal reservedValue = nav.multiply(reservedUnits).setScale(2, HALF_UP);

    return FundBalance.builder()
        .fund(getSavingsFund())
        .currency(EUR.name())
        .units(units)
        .value(value)
        .unavailableUnits(reservedUnits)
        .unavailableValue(reservedValue)
        .contributions(balances.apply(SUBSCRIPTIONS).negate())
        .subtractions(balances.apply(REDEMPTIONS).negate())
        .build();
  }

  private Fund getSavingsFund() {
    Fund fund = fundRepository.findByIsin(savingsFundConfiguration.getIsin());
    if (fund == null) {
      throw new IllegalStateException(
          "Savings fund not found: isin=" + savingsFundConfiguration.getIsin());
    }
    return fund;
  }

  private BigDecimal getNAV() {
    return navProvider.getDisplayNav(TKF100);
  }
}
