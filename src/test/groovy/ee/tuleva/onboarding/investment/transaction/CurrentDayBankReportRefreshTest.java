package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayReportsForFundRequested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(CurrentDayBankReportRefresh.class)
@RecordApplicationEvents
class CurrentDayBankReportRefreshTest {

  @Autowired CurrentDayBankReportRefresh refresh;
  @Autowired ApplicationEvents events;

  @Test
  void asksTheBankingModuleForTheFundsCurrentDayReports() {
    refresh.requestFor(TKF100);

    assertThat(events.stream(FetchSebCurrentDayReportsForFundRequested.class))
        .containsExactly(new FetchSebCurrentDayReportsForFundRequested(TKF100));
  }
}
