package ee.tuleva.onboarding.investment.report.publishing;

import ee.tuleva.onboarding.config.ScheduledTest;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(InvestmentReportPublicationCheckJob.class)
@ActiveProfiles("production")
class InvestmentReportPublicationCheckJobScheduledTest {

  @MockitoBean InvestmentReportPublicationCheck check;
  @MockitoBean InvestmentReportPublicationNotifier notifier;
  @MockitoBean Clock clock;

  @Test
  void cronExpressionsResolve() {}
}
