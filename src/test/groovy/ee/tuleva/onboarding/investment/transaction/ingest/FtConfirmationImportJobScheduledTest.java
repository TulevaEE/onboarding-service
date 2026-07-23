package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.config.ScheduledTest;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(FtConfirmationImportJob.class)
@ActiveProfiles("production")
class FtConfirmationImportJobScheduledTest {

  @MockitoBean FtConfirmationS3Source s3Source;
  @MockitoBean FtConfirmationPdfParser parser;
  @MockitoBean FtConfirmationVerificationService verificationService;
  @MockitoBean Clock clock;

  @Test
  void cronExpressionsResolve() {}
}
