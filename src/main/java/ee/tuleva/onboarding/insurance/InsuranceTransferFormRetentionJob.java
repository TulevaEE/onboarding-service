package ee.tuleva.onboarding.insurance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class InsuranceTransferFormRetentionJob {

  private final InsuranceTransferFormRepository repository;
  private final Clock clock;

  @Scheduled(cron = "0 15 3 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "InsuranceTransferFormRetentionJob_clearExpiredDocuments",
      lockAtMostFor = "10m",
      lockAtLeastFor = "1m")
  @Transactional
  public void clearExpiredDocuments() {
    final Duration SIGNED_FORM_RETENTION = Duration.ofDays(90);
    final Duration UNSIGNED_FORM_RETENTION = Duration.ofDays(7);
    Instant now = clock.instant();
    int signedForms =
        repository.clearDocumentsOfFormsSignedBefore(now.minus(SIGNED_FORM_RETENTION));
    int unsignedForms =
        repository.clearDocumentsOfUnsignedFormsCreatedBefore(now.minus(UNSIGNED_FORM_RETENTION));
    log.info(
        "Cleared expired insurance transfer form documents: signedForms={}, unsignedForms={}",
        signedForms,
        unsignedForms);
  }
}
