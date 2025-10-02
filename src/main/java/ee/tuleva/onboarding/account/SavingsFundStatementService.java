package ee.tuleva.onboarding.account;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.UserAccount.FUND_UNITS;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.UserAccount.FUND_UNITS_RESERVED;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.manager.FundManager;
import ee.tuleva.onboarding.ledger.LedgerService;
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

  private static final String EUR = "EUR";
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
          .status(Fund.FundStatus.ACTIVE)
          .build();

  public FundBalance getAccountStatement(Person person) {
    User user = userService.findByPersonalCode(person.getPersonalCode()).orElseThrow();

    BigDecimal units = getUserUnits(user);
    BigDecimal value = getNAV().multiply(units);

    return FundBalance.builder()
        .fund(SAVINGS_FUND)
        .currency(EUR)
        .units(units)
        .value(value)
        .contributions(value)
        .subtractions(getUserWithdrawals(user))
        .build();
  }

  private BigDecimal getUserUnits(User user) {
    BigDecimal balance = ledgerService.getUserAccount(user, FUND_UNITS, FUND_UNIT).getBalance();
    BigDecimal reservedBalance =
        ledgerService.getUserAccount(user, FUND_UNITS_RESERVED, FUND_UNIT).getBalance();
    return balance.add(reservedBalance);
  }

  private BigDecimal getUserDeposits(User user) {
    // TODO get deposits from ledger
    return BigDecimal.ZERO;
  }

  private BigDecimal getUserWithdrawals(User user) {
    // TODO get withdrawals from ledger
    return BigDecimal.ZERO;
  }

  private BigDecimal getNAV() {
    // TODO fetch NAV
    return BigDecimal.ONE;
  }
}
