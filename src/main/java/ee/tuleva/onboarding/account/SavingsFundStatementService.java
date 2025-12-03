package ee.tuleva.onboarding.account;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE;
import static ee.tuleva.onboarding.ledger.UserAccount.*;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.manager.FundManager;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
import ee.tuleva.onboarding.savings.fund.nav.SavingsFundNavProvider;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsFundStatementService {

  private final UserService userService;
  private final LedgerService ledgerService;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final SavingsFundNavProvider navProvider;

  // TODO: fetch from the funds table in the database
  private static final Fund SAVINGS_FUND =
      Fund.builder()
          .fundManager(new FundManager("Tuleva", 1L))
          .inceptionDate(LocalDate.of(2025, 10, 1))
          .isin("EE0000000000")
          .pillar(null)
          .nameEnglish("Tuleva Additional Savings Fund")
          .nameEstonian("Tuleva täiendav kogumisfond")
          .managementFeeRate(new BigDecimal("0.0019"))
          .ongoingChargesFigure(new BigDecimal("0.0029"))
          .status(ACTIVE)
          .build();

  public FundBalance getAccountStatement(Person person) {
    User user = userService.findByPersonalCode(person.getPersonalCode()).orElseThrow();

    if (!savingsFundOnboardingService.isOnboardingCompleted(user)) {
      throw new IllegalStateException(
          "User is not onboarded: personalCode=" + user.getPersonalCode());
    }

    BigDecimal units = getUnits(user);
    BigDecimal value = getNAV().multiply(units).setScale(2, HALF_UP);

    BigDecimal reservedUnits = getReservedUnits(user);
    BigDecimal reservedValue = getNAV().multiply(reservedUnits).setScale(2, HALF_UP);

    return FundBalance.builder()
        .fund(SAVINGS_FUND)
        .currency(EUR.name())
        .units(units)
        .value(value)
        .unavailableUnits(reservedUnits)
        .unavailableValue(reservedValue)
        .contributions(getSubscriptions(user))
        .subtractions(getRedemptions(user))
        .build();
  }

  private BigDecimal getUnits(User user) {
    return ledgerService.getUserAccount(user, FUND_UNITS).getBalance().negate();
  }

  private BigDecimal getReservedUnits(User user) {
    return ledgerService.getUserAccount(user, FUND_UNITS_RESERVED).getBalance().negate();
  }

  private BigDecimal getSubscriptions(User user) {
    return ledgerService.getUserAccount(user, SUBSCRIPTIONS).getBalance().negate();
  }

  private BigDecimal getRedemptions(User user) {
    return ledgerService.getUserAccount(user, REDEMPTIONS).getBalance();
  }

  private BigDecimal getNAV() {
    return navProvider.getCurrentNav();
  }
}
