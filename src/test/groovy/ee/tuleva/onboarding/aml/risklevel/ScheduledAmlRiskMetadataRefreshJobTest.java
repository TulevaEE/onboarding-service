package ee.tuleva.onboarding.aml.risklevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class ScheduledAmlRiskMetadataRefreshJobTest {

  @Mock private AmlRiskReader amlRiskReader;

  @Mock private TkfRiskReader tkfRiskReader;

  @Mock private CompanyRiskReader companyRiskReader;

  @InjectMocks private ScheduledAmlRiskMetadataRefreshJob scheduledAmlRiskMetadataRefreshJob;

  @Test
  void refreshScheduleAvoidsMorningPressureAndNavWindows() throws NoSuchMethodException {
    Scheduled scheduled =
        ScheduledAmlRiskMetadataRefreshJob.class
            .getMethod("refreshAmlRiskMetadata")
            .getAnnotation(Scheduled.class);

    assertThat(scheduled.cron()).isEqualTo("0 0 12,16 * * ?");
    assertThat(scheduled.zone()).isEqualTo("Europe/Tallinn");
  }

  @Test
  void refreshesAmlTkfAndCompanyViews() {
    scheduledAmlRiskMetadataRefreshJob.refreshAmlRiskMetadata();

    verify(amlRiskReader).refreshAmlRiskMetadataView();
    verify(tkfRiskReader).refreshMaterializedView();
    verify(companyRiskReader).refreshMaterializedView();
  }

  @Test
  void tkfAndCompanyRefreshStillRunWhenAmlRefreshFails() {
    willThrow(new RuntimeException("AML refresh failure"))
        .given(amlRiskReader)
        .refreshAmlRiskMetadataView();

    scheduledAmlRiskMetadataRefreshJob.refreshAmlRiskMetadata();

    verify(tkfRiskReader).refreshMaterializedView();
    verify(companyRiskReader).refreshMaterializedView();
  }

  @Test
  void amlAndCompanyRefreshStillRunWhenTkfRefreshFails() {
    willThrow(new RuntimeException("TKF refresh failure"))
        .given(tkfRiskReader)
        .refreshMaterializedView();

    scheduledAmlRiskMetadataRefreshJob.refreshAmlRiskMetadata();

    verify(amlRiskReader).refreshAmlRiskMetadataView();
    verify(companyRiskReader).refreshMaterializedView();
  }

  @Test
  void amlAndTkfRefreshStillRunWhenCompanyRefreshFails() {
    willThrow(new RuntimeException("Company refresh failure"))
        .given(companyRiskReader)
        .refreshMaterializedView();

    scheduledAmlRiskMetadataRefreshJob.refreshAmlRiskMetadata();

    verify(amlRiskReader).refreshAmlRiskMetadataView();
    verify(tkfRiskReader).refreshMaterializedView();
  }
}
