package ee.tuleva.onboarding.accounting;

import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GeneralLedgerBackgroundSyncTest {

  @Mock GeneralLedgerSyncJob generalLedgerSyncJob;
  @InjectMocks GeneralLedgerBackgroundSync backgroundSync;

  @Test
  void startingRunsTheNightlyJobUnderItsLock() {
    backgroundSync.start();

    then(generalLedgerSyncJob).should().sync();
  }
}
