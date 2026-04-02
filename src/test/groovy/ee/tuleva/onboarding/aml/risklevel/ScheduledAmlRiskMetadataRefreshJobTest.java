package ee.tuleva.onboarding.aml.risklevel;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledAmlRiskMetadataRefreshJobTest {

  @Mock private AmlRiskReader amlRiskReader;

  @Mock private TkfRiskReader tkfRiskReader;

  @InjectMocks private ScheduledAmlRiskMetadataRefreshJob scheduledAmlRiskMetadataRefreshJob;

  @Test
  void refreshesAmlAndTkfViews() {
    scheduledAmlRiskMetadataRefreshJob.refreshAmlRiskMetadata();

    verify(amlRiskReader).refreshAmlRiskMetadataView();
    verify(tkfRiskReader).refreshMaterializedView();
  }

  @Test
  void tkfRefreshStillRunsWhenAmlRefreshFails() {
    willThrow(new RuntimeException("AML refresh failure"))
        .given(amlRiskReader)
        .refreshAmlRiskMetadataView();

    scheduledAmlRiskMetadataRefreshJob.refreshAmlRiskMetadata();

    verify(tkfRiskReader).refreshMaterializedView();
  }

  @Test
  void amlRefreshStillRunsWhenTkfRefreshFails() {
    willThrow(new RuntimeException("TKF refresh failure"))
        .given(tkfRiskReader)
        .refreshMaterializedView();

    scheduledAmlRiskMetadataRefreshJob.refreshAmlRiskMetadata();

    verify(amlRiskReader).refreshAmlRiskMetadataView();
  }
}
