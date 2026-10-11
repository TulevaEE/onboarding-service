package ee.tuleva.onboarding.banking.seb.fetcher;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.config.ScheduledTest;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(SebStatementFetchingScheduler.class)
class SebStatementFetchingSchedulerScheduledTest {

  @MockitoBean BankAccounts bankAccounts;
  @MockitoBean StatementCoverage statementCoverage;
  @MockitoBean Clock clock;

  @Test
  void cronExpressionsResolve() {}
}
