package ee.tuleva.onboarding.investment.price;

import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndexValuesSnapshotJobTest {

  @Mock IndexValuesSnapshotService snapshotService;

  @InjectMocks IndexValuesSnapshotJob job;

  @Test
  void createSnapshot1130_callsService() {
    when(snapshotService.createSnapshot()).thenReturn(List.of());

    job.createSnapshot1130();

    verify(snapshotService).createSnapshot();
  }

  @Test
  void createSnapshot1530_callsService() {
    when(snapshotService.createSnapshot()).thenReturn(List.of());

    job.createSnapshot1530();

    verify(snapshotService).createSnapshot();
  }

  @Test
  void createSnapshot_doesNotThrowOnException() {
    when(snapshotService.createSnapshot()).thenThrow(new RuntimeException("Test exception"));

    job.createSnapshot();

    verify(snapshotService).createSnapshot();
  }
}
