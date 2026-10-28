package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.config.ScheduledTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(SebFundConfirmationImportJob.class)
@ActiveProfiles("production")
class SebFundConfirmationImportJobScheduledTest {

  @MockitoBean SebFundConfirmationS3Source s3Source;
  @MockitoBean SebFundConfirmationPdfParser parser;
  @MockitoBean SebFundConfirmationVerificationService verificationService;
  @MockitoBean SebFundConfirmationMissingFinder missingFinder;
  @MockitoBean SebFundConfirmationAuditRecorder auditRecorder;
  @MockitoBean SebFundConfirmationDigest digest;
  @MockitoBean SebFundConfirmationNavDateWriter navDateWriter;

  @Test
  void cronExpressionsResolve() {}
}
