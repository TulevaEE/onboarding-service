package ee.tuleva.onboarding.accounting;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Profile("production")
@RequiredArgsConstructor
class GeneralLedgerBackgroundSync {

  private final GeneralLedgerSyncJob generalLedgerSyncJob;

  @Async
  public void start() {
    generalLedgerSyncJob.sync();
  }
}
