package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.config.ScheduledTest;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(StuckPaymentAlertJob.class)
class StuckPaymentAlertJobScheduledTest {

  @MockitoBean SavingFundPaymentRepository paymentRepository;
  @MockitoBean Clock clock;

  @Test
  void cronExpressionsResolve() {}
}
