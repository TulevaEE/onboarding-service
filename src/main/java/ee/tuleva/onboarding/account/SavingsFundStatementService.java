package ee.tuleva.onboarding.account;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.UserAccount.*;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
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

  public FundBalance getAccountStatement(AuthenticatedPerson person) {
    String ownerCode = person.getRoleCode();
    PartyType partyType = PartyType.from(person.getRoleType());

    if (!savingsFundOnboardingService.isOnboardingCompleted(ownerCode)) {
      throw new IllegalStateException("Not onboarded: code=" + ownerCode);
    }

    BigDecimal units = getUnits(ownerCode, partyType);
    BigDecimal value = getNAV().multiply(units).setScale(2, HALF_UP);

    BigDecimal reservedUnits = getReservedUnits(ownerCode, partyType);
    BigDecimal reservedValue = getNAV().multiply(reservedUnits).setScale(2, HALF_UP);

    return FundBalance.builder()
        .fund(getSavingsFund())
        .currency(EUR.name())
        .units(units)
        .value(value)
        .unavailableUnits(reservedUnits)
        .unavailableValue(reservedValue)
        .contributions(getSubscriptions(ownerCode, partyType))
        .subtractions(getRedemptions(ownerCode, partyType))
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

  private BigDecimal getUnits(String ownerCode, PartyType partyType) {
    return ledgerService.getPartyAccount(ownerCode, partyType, FUND_UNITS).getBalance().negate();
  }

  private BigDecimal getReservedUnits(String ownerCode, PartyType partyType) {
    return ledgerService
        .getPartyAccount(ownerCode, partyType, FUND_UNITS_RESERVED)
        .getBalance()
        .negate();
  }

  private BigDecimal getSubscriptions(String ownerCode, PartyType partyType) {
    return ledgerService.getPartyAccount(ownerCode, partyType, SUBSCRIPTIONS).getBalance().negate();
  }

  private BigDecimal getRedemptions(String ownerCode, PartyType partyType) {
    return ledgerService.getPartyAccount(ownerCode, partyType, REDEMPTIONS).getBalance().negate();
  }

  private BigDecimal getNAV() {
    return navProvider.getDisplayNav(TKF100);
  }
}
