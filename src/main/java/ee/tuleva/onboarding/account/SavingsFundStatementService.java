package ee.tuleva.onboarding.account;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.UserAccount.*;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.savings.fund.SavingsFundConfiguration;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
import ee.tuleva.onboarding.savings.fund.nav.FundNavProvider;
import java.math.BigDecimal;
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

  public FundBalance getAccountStatement(Person person) {
    String ownerCode = person.getPersonalCode();

    if (!savingsFundOnboardingService.isOnboardingCompleted(ownerCode)) {
      throw new IllegalStateException("User is not onboarded: personalCode=" + ownerCode);
    }

    BigDecimal units = getUnits(ownerCode);
    BigDecimal value = getNAV().multiply(units).setScale(2, HALF_UP);

    BigDecimal reservedUnits = getReservedUnits(ownerCode);
    BigDecimal reservedValue = getNAV().multiply(reservedUnits).setScale(2, HALF_UP);

    return FundBalance.builder()
        .fund(getSavingsFund())
        .currency(EUR.name())
        .units(units)
        .value(value)
        .unavailableUnits(reservedUnits)
        .unavailableValue(reservedValue)
        .contributions(getSubscriptions(ownerCode))
        .subtractions(getRedemptions(ownerCode))
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

  private BigDecimal getUnits(String ownerCode) {
    return ledgerService.getPartyAccount(ownerCode, FUND_UNITS).getBalance().negate();
  }

  private BigDecimal getReservedUnits(String ownerCode) {
    return ledgerService.getPartyAccount(ownerCode, FUND_UNITS_RESERVED).getBalance().negate();
  }

  private BigDecimal getSubscriptions(String ownerCode) {
    return ledgerService.getPartyAccount(ownerCode, SUBSCRIPTIONS).getBalance().negate();
  }

  private BigDecimal getRedemptions(String ownerCode) {
    return ledgerService.getPartyAccount(ownerCode, REDEMPTIONS).getBalance().negate();
  }

  private BigDecimal getNAV() {
    return navProvider.getDisplayNav(TKF100);
  }
}
