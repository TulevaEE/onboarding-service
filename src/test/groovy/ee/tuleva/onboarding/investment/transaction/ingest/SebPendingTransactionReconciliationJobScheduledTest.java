package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.config.ScheduledTest;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(SebPendingTransactionReconciliationJob.class)
@Import(PublicHolidays.class)
@ActiveProfiles("production")
class SebPendingTransactionReconciliationJobScheduledTest {

  @MockitoBean InvestmentReportService reportService;
  @MockitoBean SebPendingTransactionReconciliationService reconciliationService;
  @MockitoBean Clock clock;

  @Test
  void cronExpressionsResolve() {}
}
