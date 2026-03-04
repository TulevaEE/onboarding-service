package ee.tuleva.onboarding.investment.check.limit;

import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LimitCheckJobTest {

  @Mock LimitCheckService limitCheckService;
  @Mock LimitCheckNotifier limitCheckNotifier;
  @InjectMocks LimitCheckJob job;

  @Test
  void delegatesToServiceAndNotifier() {
    var results = List.of(mock(LimitCheckResult.class));
    when(limitCheckService.runChecks()).thenReturn(results);

    job.runLimitChecks();

    verify(limitCheckService).runChecks();
    verify(limitCheckNotifier).notify(results);
  }

  @Test
  void swallowsExceptions() {
    when(limitCheckService.runChecks()).thenThrow(new RuntimeException("DB down"));

    job.runLimitChecks();

    verify(limitCheckNotifier, never()).notify(any());
  }
}
