package ee.tuleva.onboarding.analytics.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class RefreshMaterializedViewsJobTest {

  @Mock private MaterializedViewRepository materializedViewRepository;

  @InjectMocks private RefreshMaterializedViewsJob job;

  @Test
  void refreshesAllViews() {
    job.refreshViews();

    verify(materializedViewRepository).refreshAllViews();
  }

  @Test
  void lockExpiryAllowsSameDayRerunAfterAKilledRun() throws NoSuchMethodException {
    SchedulerLock lock =
        RefreshMaterializedViewsJob.class
            .getMethod("refreshViews")
            .getAnnotation(SchedulerLock.class);

    assertThat(lock.lockAtMostFor()).isEqualTo("4h");
  }

  @Test
  void runsDailyBeforeBusinessHours() throws NoSuchMethodException {
    Scheduled scheduled =
        RefreshMaterializedViewsJob.class.getMethod("refreshViews").getAnnotation(Scheduled.class);

    assertThat(scheduled.cron()).isEqualTo("0 0 7 * * ?");
    assertThat(scheduled.zone()).isEqualTo("Europe/Tallinn");
  }
}
