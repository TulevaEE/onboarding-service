package ee.tuleva.onboarding.savings.fund.documents;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class SavingsFundDocumentsRefreshJobTest {

  private final SavingsFundDocumentsService service = mock(SavingsFundDocumentsService.class);
  private final SavingsFundDocumentsRefreshJob job = new SavingsFundDocumentsRefreshJob(service);

  @Test
  void refresh_delegatesToService() {
    job.refresh();

    verify(service).refresh();
  }
}
