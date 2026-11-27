package ee.tuleva.onboarding.investment.transaction.ingest;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class SebFundConfirmationRecorder {

  private final SebFundConfirmationRepository repository;
  private final SebFundConfirmationAuditRecorder auditRecorder;
  private final Clock clock;

  @Transactional
  void record(SebFundConfirmationUpload upload) {
    RecordedSebFundConfirmation latest = RecordedSebFundConfirmation.of(upload);
    repository
        .findByTradeReference(latest.tradeReference())
        .ifPresentOrElse(
            recorded -> replaceUnlessItSaysTheSame(recorded, latest),
            () -> repository.insert(latest, clock.instant()));
  }

  private void replaceUnlessItSaysTheSame(
      RecordedSebFundConfirmation recorded, RecordedSebFundConfirmation latest) {
    if (latest.saysTheSameAs(recorded)) {
      return;
    }
    repository.update(latest, clock.instant());
    auditRecorder.recordReplaced(recorded, latest);
  }
}
