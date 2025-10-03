package ee.tuleva.onboarding.account;

import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE;
import static ee.tuleva.onboarding.ledger.UserAccount.*;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.manager.FundManager;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
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

  private static final BigDecimal FEE_RATE = new BigDecimal("0.0049");
  private static final Fund SAVINGS_FUND =
      Fund.builder()
          .fundManager(new FundManager("Tuleva", 1L))
          .inceptionDate(LocalDate.of(2025, 10, 1))
          .isin("EE0000000000")
          .pillar(null)
          .nameEnglish("Tuleva Additional Savings Fund")
          .nameEstonian("Tuleva täiendav kogumisfond")
          .managementFeeRate(FEE_RATE)
          .ongoingChargesFigure(FEE_RATE)
          .status(ACTIVE)
          .build();

  public FundBalance getAccountStatement(Person person) {
    User user = userService.findByPersonalCode(person.getPersonalCode()).orElseThrow();

    if (!savingsFundOnboardingService.isOnboardingCompleted(user)) {
      throw new IllegalStateException(
          "User is not onboarded: personalCode=" + user.getPersonalCode());
    }

    BigDecimal units = getUserFundUnits(user);
    BigDecimal value = getNAV().multiply(units);

    return FundBalance.builder()
        .fund(SAVINGS_FUND)
        .currency(Currency.EUR.name())
        .units(units)
        .value(value)
        .contributions(getUserSubscriptions(user))
        .subtractions(getUserRedemptions(user))
        .build();
  }

  private BigDecimal getUserFundUnits(User user) {
    BigDecimal balance = ledgerService.getUserAccount(user, FUND_UNITS).getBalance().negate();
    BigDecimal reservedBalance =
        ledgerService.getUserAccount(user, FUND_UNITS_RESERVED).getBalance().negate();
    return balance.add(reservedBalance);
  }

  private BigDecimal getUserSubscriptions(User user) {
    return ledgerService.getUserAccount(user, SUBSCRIPTIONS).getBalance().negate();
  }

  private BigDecimal getUserRedemptions(User user) {
    return ledgerService.getUserAccount(user, REDEMPTIONS).getBalance();
  }

  private BigDecimal getNAV() {
    // TODO fetch NAV
    return BigDecimal.ONE;
  }
}
