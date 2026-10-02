package ee.tuleva.onboarding.accounting;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GeneralLedgerSyncJobTest {

  @Mock GeneralLedgerSync generalLedgerSync;
  @InjectMocks GeneralLedgerSyncJob job;

  @Test
  void oneEntityFailingDoesNotStopTheOthers() {
    given(generalLedgerSync.entities()).willReturn(List.of("FIRST_ENTITY", "SECOND_ENTITY"));
    given(generalLedgerSync.sync("FIRST_ENTITY"))
        .willThrow(new IllegalStateException("General ledger source unreachable"));

    job.sync();

    then(generalLedgerSync).should().sync("SECOND_ENTITY");
  }
}
