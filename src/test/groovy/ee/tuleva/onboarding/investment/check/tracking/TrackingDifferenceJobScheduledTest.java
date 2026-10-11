package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.config.ScheduledTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(TrackingDifferenceJob.class)
@ActiveProfiles("production")
class TrackingDifferenceJobScheduledTest {

  @MockitoBean TrackingDifferenceService trackingDifferenceService;
  @MockitoBean TrackingDifferenceNotifier trackingDifferenceNotifier;

  @Test
  void cronExpressionsResolve() {}
}
