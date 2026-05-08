package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.config.ScheduledTest;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(NavReconciliationJob.class)
@Import(PublicHolidays.class)
@ActiveProfiles("production")
class NavReconciliationJobScheduledTest {

  @MockitoBean NavReconciliationService reconciliationService;
  @MockitoBean Clock clock;

  @Test
  void cronExpressionsResolve() {}
}
