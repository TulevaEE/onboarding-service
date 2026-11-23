package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationImportJob.LOCK;

import ee.tuleva.onboarding.investment.event.JobBusyException;
import ee.tuleva.onboarding.investment.event.RunSebFundConfirmationBackfillRequested;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("production")
@RequiredArgsConstructor
class SebFundConfirmationBackfillJob {

  private static final Duration A_STOPPED_BACKFILL_HOLDS_THE_IMPORT_LOCK_AT_MOST =
      Duration.ofMinutes(30);

  private final SebFundConfirmationS3Source s3Source;
  private final SebFundConfirmationPdfParser parser;
  private final SebFundConfirmationRecorder confirmationRecorder;
  private final SebFundConfirmationNavDateWriter navDateWriter;
  private final LockProvider lockProvider;
  private final Clock clock;

  @EventListener(RunSebFundConfirmationBackfillRequested.class)
  void backfill() {
    SimpleLock lock =
        lockProvider
            .lock(
                new LockConfiguration(
                    clock.instant(),
                    LOCK,
                    A_STOPPED_BACKFILL_HOLDS_THE_IMPORT_LOCK_AT_MOST,
                    Duration.ZERO))
            .orElseThrow(
                () ->
                    new JobBusyException(
                        "SEB fund confirmation backfill waits for the import job: lock=" + LOCK));
    try {
      recordEveryConfirmationInS3();
    } finally {
      lock.unlock();
    }
  }

  private void recordEveryConfirmationInS3() {
    List<String> keys = s3Source.allKeys();
    List<String> unreadable = new ArrayList<>();
    List<String> failed = new ArrayList<>();
    Map<String, SebFundConfirmationUpload> latestByReference = new LinkedHashMap<>();
    keys.forEach(
        key ->
            read(key, unreadable, failed)
                .ifPresent(upload -> latestByReference.put(upload.tradeReference(), upload)));
    latestByReference.values().forEach(upload -> keep(upload, failed));
    log.info(
        "SEB fund confirmation backfill completed: objectCount={}, tradeCount={},"
            + " unreadableKeys={}, failedKeys={}",
        keys.size(),
        latestByReference.size(),
        unreadable,
        failed);
    if (!unreadable.isEmpty() || !failed.isEmpty()) {
      throw new SebFundConfirmationBackfillIncompleteException(unreadable, failed);
    }
  }

  private Optional<SebFundConfirmationUpload> read(
      String key, List<String> unreadable, List<String> failed) {
    Optional<byte[]> bytes;
    try {
      bytes = s3Source.get(key);
    } catch (RuntimeException e) {
      log.error("SEB fund confirmation backfill could not download: key={}", key, e);
      failed.add(key);
      return Optional.empty();
    }
    try {
      return bytes.map(pdf -> new SebFundConfirmationUpload(key, parser.parse(pdf)));
    } catch (RuntimeException e) {
      log.error("SEB fund confirmation backfill could not read: key={}", key, e);
      unreadable.add(key);
      return Optional.empty();
    }
  }

  private void keep(SebFundConfirmationUpload upload, List<String> failed) {
    try {
      confirmationRecorder.record(upload);
      navDateWriter.write(upload.tradeReference());
    } catch (RuntimeException e) {
      log.error(
          "SEB fund confirmation backfill could not record or date: key={}, tradeReference={}",
          upload.key(),
          upload.tradeReference(),
          e);
      failed.add(upload.key());
    }
  }
}
