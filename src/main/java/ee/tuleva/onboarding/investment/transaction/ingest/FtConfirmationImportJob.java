package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.JobRunSchedule.IMPORT_BUSINESS_HOURS;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationBatchResult;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@NullMarked
@Slf4j
@Component
@Profile({"production", "staging"})
@RequiredArgsConstructor
class FtConfirmationImportJob {

  static final String JOB_ACTOR = "ft-confirmation-import-job";

  private final FtConfirmationS3Source s3Source;
  private final FtConfirmationPdfParser parser;
  private final FtConfirmationVerificationService verificationService;
  private final Clock clock;

  @Scheduled(cron = IMPORT_BUSINESS_HOURS, zone = TIMEZONE)
  @SchedulerLock(name = "FtConfirmationImportJob", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  public void run() {
    log.info("Starting FT confirmation import: now={}", clock.instant());
    List<String> keys = s3Source.list();
    if (keys.isEmpty()) {
      log.info("No FT confirmation PDFs found");
      return;
    }

    List<FtConfirmation> confirmations =
        keys.stream().map(this::parse).flatMap(Optional::stream).toList();
    if (confirmations.isEmpty()) {
      log.info("No FT confirmation PDFs could be parsed: objectCount={}", keys.size());
      return;
    }

    verifyAll(confirmations, keys.size());
  }

  private void verifyAll(List<FtConfirmation> confirmations, int objectCount) {
    try {
      List<FtConfirmationBatchResult> results =
          verificationService.verifyAll(confirmations, JOB_ACTOR);
      log.info(
          "FT confirmation import completed: objectCount={}, parsedCount={}, verifiedCount={}",
          objectCount,
          confirmations.size(),
          results.size());
    } catch (RuntimeException e) {
      log.error(
          "FT confirmation verification failed: parsedCount={}, error={}",
          confirmations.size(),
          e.getMessage(),
          e);
    }
  }

  private Optional<FtConfirmation> parse(String key) {
    Optional<byte[]> bytes = s3Source.get(key);
    if (bytes.isEmpty()) {
      log.warn("FT confirmation PDF disappeared before fetch: key={}", key);
      return Optional.empty();
    }
    try {
      FtConfirmation confirmation = parser.parse(bytes.get());
      log.info(
          "Parsed FT confirmation: key={}, fund={}, isin={}, type={}",
          key,
          confirmation.fund(),
          confirmation.isin(),
          confirmation.type());
      return Optional.of(confirmation);
    } catch (RuntimeException e) {
      log.error(
          "FT confirmation PDF failed to parse, skipping: key={}, error={}",
          key,
          e.getMessage(),
          e);
      return Optional.empty();
    }
  }
}
