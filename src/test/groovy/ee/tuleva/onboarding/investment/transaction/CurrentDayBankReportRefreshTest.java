package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayTransactionsRequested;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(CurrentDayBankReportRefresh.class)
@RecordApplicationEvents
class CurrentDayBankReportRefreshTest {

  @Autowired CurrentDayBankReportRefresh refresh;
  @Autowired ApplicationEvents events;
  @MockitoBean BankAccounts bankAccounts;

  @Test
  void requestsTheCurrentDayReportOfEveryAccountOfTheFund() {
    var deposit = new BankAccount("EE111111111111111111", DEPOSIT_EUR, TKF100, "client");
    var investment = new BankAccount("EE222222222222222222", FUND_INVESTMENT_EUR, TKF100, "client");
    given(bankAccounts.findAll(TKF100)).willReturn(List.of(deposit, investment));

    refresh.requestFor(TKF100);

    assertThat(events.stream(FetchSebCurrentDayTransactionsRequested.class))
        .containsExactly(
            new FetchSebCurrentDayTransactionsRequested(deposit),
            new FetchSebCurrentDayTransactionsRequested(investment));
  }
}
