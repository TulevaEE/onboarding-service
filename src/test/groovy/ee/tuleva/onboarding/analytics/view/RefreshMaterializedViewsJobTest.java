package ee.tuleva.onboarding.analytics.view;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshMaterializedViewsJobTest {

  @Mock private MaterializedViewRepository materializedViewRepository;

  @InjectMocks private RefreshMaterializedViewsJob job;

  @Test
  @DisplayName("refreshViews should call repository to refresh all views")
  void refreshViews_callsRepositoryToRefreshAllViews() {
    // when
    job.refreshViews();

    // then
    verify(materializedViewRepository).refreshAllViews();
  }
}
