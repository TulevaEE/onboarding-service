package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.JobRunSchedule.IMPORT_BUSINESS_HOURS;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationBatchResult;
import java.time.Clock;
import java.util.ArrayList;
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
  private final FtUnconfirmedTradeCheck unconfirmedTradeCheck;
  private final FtUnreadableConfirmationWarning unreadableWarning;
  private final Clock clock;

  @Scheduled(cron = IMPORT_BUSINESS_HOURS, zone = TIMEZONE)
  @SchedulerLock(name = "FtConfirmationImportJob", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  public void run() {
    log.info("Starting FT confirmation import: now={}", clock.instant());
    List<String> keys = s3Source.list();
    List<FtUnreadableConfirmation> unreadable = new ArrayList<>();
    List<FtConfirmation> confirmations =
        keys.stream()
            .map(key -> fetch(key).flatMap(bytes -> parse(key, bytes, unreadable)))
            .flatMap(Optional::stream)
            .toList();
    verifyAll(confirmations, keys.size());
    warnAbout(unreadable);
    checkUnconfirmedTrades(confirmations);
  }

  private void warnAbout(List<FtUnreadableConfirmation> unreadable) {
    try {
      unreadableWarning.publish(unreadable);
    } catch (RuntimeException e) {
      log.error(
          "FT unreadable confirmation warning failed: unreadableCount={}, error={}",
          unreadable.size(),
          e.getMessage(),
          e);
    }
  }

  private void verifyAll(List<FtConfirmation> confirmations, int objectCount) {
    if (confirmations.isEmpty()) {
      log.info("No FT confirmation PDFs could be read: objectCount={}", objectCount);
      return;
    }
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

  private void checkUnconfirmedTrades(List<FtConfirmation> confirmations) {
    try {
      unconfirmedTradeCheck.check(confirmations, JOB_ACTOR);
    } catch (RuntimeException e) {
      log.error("FT unconfirmed trade check failed: error={}", e.getMessage(), e);
    }
  }

  private Optional<byte[]> fetch(String key) {
    try {
      Optional<byte[]> bytes = s3Source.get(key);
      if (bytes.isEmpty()) {
        log.warn("FT confirmation PDF disappeared before fetch: key={}", key);
      }
      return bytes;
    } catch (RuntimeException e) {
      log.error(
          "FT confirmation PDF could not be fetched, skipping: key={}, error={}",
          key,
          e.getMessage(),
          e);
      return Optional.empty();
    }
  }

  private Optional<FtConfirmation> parse(
      String key, byte[] bytes, List<FtUnreadableConfirmation> unreadable) {
    try {
      FtConfirmation confirmation = parser.parse(bytes);
      log.info(
          "Parsed FT confirmation: key={}, fund={}, isin={}, type={}",
          key,
          confirmation.fund(),
          confirmation.isin(),
          confirmation.type());
      return Optional.of(confirmation);
    } catch (RuntimeException e) {
      log.error(
          "FT confirmation PDF could not be read, skipping: key={}, error={}",
          key,
          e.getMessage(),
          e);
      unreadable.add(new FtUnreadableConfirmation(key, reason(e)));
      return Optional.empty();
    }
  }

  private static String reason(RuntimeException e) {
    return e.getMessage() == null ? e.toString() : e.getMessage();
  }
}
