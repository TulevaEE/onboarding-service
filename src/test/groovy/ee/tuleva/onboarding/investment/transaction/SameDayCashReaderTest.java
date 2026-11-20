package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.BankStatementBalances;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class SameDayCashReaderTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

  private final BankAccounts bankAccounts = org.mockito.Mockito.mock(BankAccounts.class);
  private final BankStatementBalances balances =
      org.mockito.Mockito.mock(BankStatementBalances.class);
  private final SameDayCashReader reader =
      new SameDayCashReader(
          bankAccounts,
          balances,
          org.mockito.Mockito.mock(FundNavQueryService.class),
          org.mockito.Mockito.mock(InvestmentParameterRepository.class),
          Clock.fixed(Instant.parse("2026-11-20T08:30:00Z"), ZoneId.of("Europe/Tallinn")));

  @Test
  void aDatabaseFailureWhileReadingTheBalanceFailsTheRunInsteadOfPosingAsAFallback() {
    given(bankAccounts.findAll(TUK75))
        .willReturn(
            List.of(new BankAccount("EE111111111111111111", FUND_INVESTMENT_EUR, TUK75, "client")));
    given(balances.currentDayReport(any(), any()))
        .willThrow(new DataAccessResourceFailureException("connection lost"));

    assertThatThrownBy(() -> reader.read(TUK75, TODAY, null, null))
        .isInstanceOf(DataAccessResourceFailureException.class);
  }
}
