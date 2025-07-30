package ee.tuleva.onboarding.aml.risklevel;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledAmlRiskMetadataRefreshJobTest {

  @Mock private AmlRiskRepositoryService amlRiskRepositoryService;

  @InjectMocks private ScheduledAmlRiskMetadataRefreshJob scheduledAmlRiskMetadataRefreshJob;

  @Test
  void refreshAmlRiskMetadataShouldInvokeRepositoryService() {
    scheduledAmlRiskMetadataRefreshJob.refreshAmlRiskMetadata();
    verify(amlRiskRepositoryService, times(1)).refreshAmlRiskMetadataView();
  }
}
