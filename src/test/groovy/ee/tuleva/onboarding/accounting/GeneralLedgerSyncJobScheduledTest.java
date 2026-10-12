package ee.tuleva.onboarding.accounting;

import ee.tuleva.onboarding.config.ScheduledTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(GeneralLedgerSyncJob.class)
@ActiveProfiles("production")
class GeneralLedgerSyncJobScheduledTest {

  @MockitoBean GeneralLedgerSync generalLedgerSync;

  @Test
  void cronExpressionResolves() {}
}
