package ee.tuleva.onboarding.account;


import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.manager.FundManager;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SavingsFundStatementService {

  private final UserService userService;

  private static final String EUR = "EUR";
  private static final BigDecimal FEE_RATE = new BigDecimal("0.0049");
  private static final Fund SAVINGS_FUND = Fund.builder()
      .fundManager(new FundManager("Tuleva", 1L))
      .inceptionDate(LocalDate.of(2025, 10, 1))
      .isin("EE0000000000")
      .pillar(-1)
      .nameEnglish("Additional Savings Fund")
      .nameEstonian("Täiendav kogumisfond")
      .managementFeeRate(FEE_RATE)
      .ongoingChargesFigure(FEE_RATE)
      .status(Fund.FundStatus.ACTIVE)
      .build();

  public Optional<FundBalance> getAccountStatement(Person person) {
    User user = userService.findByPersonalCode(person.getPersonalCode()).orElseThrow();

    BigDecimal units = getUserUnits(user);
    BigDecimal value = getNAV().multiply(units);

    return Optional.of(FundBalance.builder()
        .fund(SAVINGS_FUND)
        .currency(EUR)
        .units(units)
        .value(value)
        .contributions(getUserDeposits(user))
        .subtractions(getUserWithdrawals(user))
        .build());
  }

  private BigDecimal getUserUnits(User user) {
    return BigDecimal.TEN;
  }

  private BigDecimal getUserDeposits(User user) {
    return BigDecimal.TEN;
  }

  private BigDecimal getUserWithdrawals(User user) {
    return BigDecimal.ZERO;
  }

  private BigDecimal getNAV() {
    return BigDecimal.ONE;
  }

}
