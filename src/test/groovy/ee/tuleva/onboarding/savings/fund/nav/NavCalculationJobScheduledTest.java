package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueIndexingJob;
import ee.tuleva.onboarding.config.ScheduledTest;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(NavCalculationJob.class)
@ActiveProfiles("production")
class NavCalculationJobScheduledTest {

  @MockitoBean NavCalculationService navCalculationService;
  @MockitoBean NavPublisher navPublisher;
  @MockitoBean PublicHolidays publicHolidays;
  @MockitoBean FundValueIndexingJob fundValueIndexingJob;
  @MockitoBean Clock clock;

  @Test
  void cronExpressionsResolve() {}
}
