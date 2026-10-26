package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.BANK_BALANCE;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.BankStatementBalances;
import ee.tuleva.onboarding.banking.statement.BankStatementParseException;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankBalanceCheckerTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 10, 23);
  private static final BankAccount TUV100_INVESTMENT =
      new BankAccount("EE111111111111111111", FUND_INVESTMENT_EUR, TUV100, "client");

  @Mock BankAccounts bankAccounts;
  @Mock BankStatementBalances bankStatementBalances;

  @InjectMocks BankBalanceChecker checker;

  @Test
  void warnsThatTheNavIsTooHighWhenTheReportHasMoreCashThanTheBank() {
    given(bankAccounts.findAll(TUV100)).willReturn(List.of(TUV100_INVESTMENT));
    given(bankStatementBalances.closingBalance(TUV100_INVESTMENT, NAV_DATE))
        .willReturn(Optional.of(new BigDecimal("-250.000")));

    assertThat(checker.check(TUV100, NAV_DATE, List.of(cash("750.00"))))
        .containsExactly(
            new HealthCheckFinding(
                TUV100,
                BANK_BALANCE,
                WARNING,
                "Report cash 750.00, bank closing balance -250.00 on 2026-10-23, difference"
                    + " 1000.00, NAV too high. Have SEB correct the report's cash before the NAV"));
  }

  @Test
  void comparesInWholeCentsSoASubCentDifferenceDoesNotWarnButHalfACentDoes() {
    given(bankAccounts.findAll(TUV100)).willReturn(List.of(TUV100_INVESTMENT));
    given(bankStatementBalances.closingBalance(TUV100_INVESTMENT, NAV_DATE))
        .willReturn(Optional.of(new BigDecimal("1000.00")));

    assertThat(checker.check(TUV100, NAV_DATE, List.of(cash("1000.0049")))).isEmpty();
    assertThat(checker.check(TUV100, NAV_DATE, List.of(cash("1000.005"))))
        .containsExactly(
            new HealthCheckFinding(
                TUV100,
                BANK_BALANCE,
                WARNING,
                "Report cash 1000.01, bank closing balance 1000.00 on 2026-10-23, difference"
                    + " 0.01, NAV too high. Have SEB correct the report's cash before the NAV"));
  }

  @Test
  void addsUpEveryCashRowAndSkipsRowsWithoutAValue() {
    given(bankAccounts.findAll(TUV100)).willReturn(List.of(TUV100_INVESTMENT));
    given(bankStatementBalances.closingBalance(TUV100_INVESTMENT, NAV_DATE))
        .willReturn(Optional.of(new BigDecimal("1000.00")));

    assertThat(checker.check(TUV100, NAV_DATE, List.of(cash("600.00"), cash("400.00"), cash(null))))
        .isEmpty();
  }

  @Test
  void cannotRunWhenTheDaysStatementCannotBeReadAndPassesOnceItCan() {
    given(bankAccounts.findAll(TUV100)).willReturn(List.of(TUV100_INVESTMENT));
    willThrow(new BankStatementParseException("Raw XML is null or empty"))
        .given(bankStatementBalances)
        .closingBalance(TUV100_INVESTMENT, NAV_DATE);

    assertThat(checker.check(TUV100, NAV_DATE, List.of(cash("1000.00"))))
        .containsExactly(
            new HealthCheckFinding(
                TUV100, BANK_BALANCE, NOT_RUN, "Bank statement for 2026-10-23 could not be read"));

    willReturn(Optional.of(new BigDecimal("1000.00")))
        .given(bankStatementBalances)
        .closingBalance(TUV100_INVESTMENT, NAV_DATE);

    assertThat(checker.check(TUV100, NAV_DATE, List.of(cash("1000.00")))).isEmpty();
  }

  @Test
  void hasNothingToSayAboutAFundWithNoInvestmentAccount() {
    given(bankAccounts.findAll(TKF100))
        .willReturn(List.of(new BankAccount("EE222222222222222222", DEPOSIT_EUR, TKF100, "c")));

    assertThat(checker.check(TKF100, NAV_DATE, List.of(cash("1000.00")))).isEmpty();
    verifyNoInteractions(bankStatementBalances);
  }

  private static FundPosition cash(@Nullable String marketValue) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(TUV100)
        .accountType(CASH)
        .accountName("Cash account in SEB Pank")
        .marketValue(marketValue == null ? null : new BigDecimal(marketValue))
        .build();
  }
}
