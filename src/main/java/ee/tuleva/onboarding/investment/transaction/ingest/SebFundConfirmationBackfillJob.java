package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationImportJob.LOCK;

import ee.tuleva.onboarding.investment.event.JobBusyException;
import ee.tuleva.onboarding.investment.event.RunSebFundConfirmationBackfillRequested;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
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
    LeftOutKeys leftOut = new LeftOutKeys();
    Map<String, SebFundConfirmationUpload> latestByReference = new LinkedHashMap<>();
    keys.forEach(
        key ->
            read(key, leftOut)
                .ifPresent(upload -> latestByReference.put(upload.tradeReference(), upload)));
    Map<String, String> recordedReferenceByKey = new HashMap<>();
    latestByReference
        .values()
        .forEach(
            upload -> {
              if (kept(upload, leftOut)) {
                recordedReferenceByKey.put(upload.key(), upload.tradeReference());
              }
            });
    leftOut
        .vanished()
        .forEach(
            key -> failUnlessRecordedFromANewerUpload(key, keys, recordedReferenceByKey, leftOut));
    log.info(
        "SEB fund confirmation backfill completed: objectCount={}, tradeCount={},"
            + " unreadableKeys={}, failedKeys={}",
        keys.size(),
        latestByReference.size(),
        leftOut.unreadable(),
        leftOut.failed());
    if (leftOut.leftAnyPdfOut()) {
      throw new SebFundConfirmationBackfillIncompleteException(
          leftOut.unreadable(), leftOut.failed());
    }
  }

  private Optional<SebFundConfirmationUpload> read(String key, LeftOutKeys leftOut) {
    Optional<byte[]> bytes;
    try {
      bytes = s3Source.get(key);
    } catch (RuntimeException e) {
      log.error("SEB fund confirmation backfill could not download: key={}", key, e);
      leftOut.failed().add(key);
      return Optional.empty();
    }
    if (bytes.isEmpty()) {
      leftOut.vanished().add(key);
      return Optional.empty();
    }
    try {
      return Optional.of(new SebFundConfirmationUpload(key, parser.parse(bytes.get())));
    } catch (RuntimeException e) {
      log.error("SEB fund confirmation backfill could not read: key={}", key, e);
      leftOut.unreadable().add(key);
      return Optional.empty();
    }
  }

  private boolean kept(SebFundConfirmationUpload upload, LeftOutKeys leftOut) {
    try {
      confirmationRecorder.record(upload);
      navDateWriter.write(upload.tradeReference());
      return true;
    } catch (RuntimeException e) {
      log.error(
          "SEB fund confirmation backfill could not record or date: key={}, tradeReference={}",
          upload.key(),
          upload.tradeReference(),
          e);
      leftOut.failed().add(upload.key());
      return false;
    }
  }

  private static void failUnlessRecordedFromANewerUpload(
      String vanishedKey,
      List<String> keysOldestFirst,
      Map<String, String> recordedReferenceByKey,
      LeftOutKeys leftOut) {
    String reference = SebFundConfirmationS3Source.tradeReference(vanishedKey);
    boolean recordedFromANewerUpload =
        keysOldestFirst
            .subList(keysOldestFirst.indexOf(vanishedKey) + 1, keysOldestFirst.size())
            .stream()
            .anyMatch(newerKey -> reference.equals(recordedReferenceByKey.get(newerKey)));
    if (recordedFromANewerUpload) {
      log.warn(
          "SEB fund confirmation PDF vanished before the backfill fetched it, its trade was"
              + " recorded from a newer upload: key={}, tradeReference={}",
          vanishedKey,
          reference);
      return;
    }
    log.error(
        "SEB fund confirmation PDF vanished before the backfill fetched it: key={}, tradeReference={}",
        vanishedKey,
        reference);
    leftOut.failed().add(vanishedKey);
  }

  private record LeftOutKeys(List<String> unreadable, List<String> vanished, List<String> failed) {

    LeftOutKeys() {
      this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    boolean leftAnyPdfOut() {
      return !unreadable.isEmpty() || !failed.isEmpty();
    }
  }
}
