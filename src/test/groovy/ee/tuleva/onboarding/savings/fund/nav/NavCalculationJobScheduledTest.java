package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueIndexingJob;
import ee.tuleva.onboarding.config.ScheduledTest;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.event.PipelineNotifier;
import ee.tuleva.onboarding.investment.event.PipelineTracker;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(NavCalculationJob.class)
@Import(PublicHolidays.class)
@ActiveProfiles("production")
class NavCalculationJobScheduledTest {

  @MockitoBean NavCalculationService navCalculationService;
  @MockitoBean NavPublisher navPublisher;
  @MockitoBean NavReportRepository navReportRepository;
  @MockitoBean FundValueIndexingJob fundValueIndexingJob;
  @MockitoBean Clock clock;
  @MockitoBean PipelineTracker pipelineTracker;
  @MockitoBean PipelineNotifier pipelineNotifier;

  @Test
  void cronExpressionsResolve() {}
}
