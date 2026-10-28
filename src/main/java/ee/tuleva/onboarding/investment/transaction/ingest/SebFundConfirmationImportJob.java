package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.JobRunSchedule.IMPORT_BUSINESS_HOURS;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("production")
@RequiredArgsConstructor
class SebFundConfirmationImportJob {

  static final String JOB_ACTOR = "seb-fund-confirmation-import-job";

  private final SebFundConfirmationS3Source s3Source;
  private final SebFundConfirmationPdfParser parser;
  private final SebFundConfirmationVerificationService verificationService;
  private final SebFundConfirmationMissingFinder missingFinder;
  private final SebFundConfirmationAuditRecorder auditRecorder;
  private final SebFundConfirmationNavDateWriter navDateWriter;
  private final SebFundConfirmationDigest digest;

  @Scheduled(cron = IMPORT_BUSINESS_HOURS, zone = TIMEZONE)
  @SchedulerLock(
      name = "SebFundConfirmationImportJob",
      lockAtMostFor = "15m",
      lockAtLeastFor = "1m")
  public void run() {
    List<SebFundConfirmationCheckFailure> failures = new ArrayList<>();
    List<String> keys = recentKeys(failures);
    Uploads uploads = new Uploads();
    keys.forEach(key -> read(key, uploads, failures));
    Map<String, SebFundConfirmation> latestByReference = uploads.latestByReference();

    List<SebFundConfirmationOutcome> outcomes =
        latestByReference.values().stream()
            .map(confirmation -> verify(confirmation, failures))
            .flatMap(Optional::stream)
            .toList();
    outcomes.forEach(outcome -> record(outcome, failures));
    outcomes.forEach(outcome -> writeNavDate(outcome, failures));

    digest.publish(
        outcomes,
        missing(uploads.confirmedReferences(), failures),
        uploads.unreadable(),
        uploads.misnamed(),
        failures);
    log.info(
        "SEB fund confirmation check completed: objectCount={}, tradeCount={}, verifiedCount={},"
            + " unreadableCount={}, misnamedCount={}, failureCount={}",
        keys.size(),
        latestByReference.size(),
        outcomes.size(),
        uploads.unreadable().size(),
        uploads.misnamed().size(),
        failures.size());
  }

  private void read(String key, Uploads uploads, List<SebFundConfirmationCheckFailure> failures) {
    String nameReference = SebFundConfirmationS3Source.tradeReference(key);
    Optional<SebFundConfirmation> confirmation =
        download(key, failures).flatMap(bytes -> parse(key, bytes, uploads.unreadable()));
    uploads
        .confirmedReferences()
        .add(confirmation.map(SebFundConfirmation::tradeReference).orElse(nameReference));
    confirmation.ifPresent(
        read -> {
          if (!nameReference.equals(read.tradeReference())) {
            uploads
                .misnamed()
                .add(new MisnamedSebFundConfirmation(key, nameReference, read.tradeReference()));
          }
          uploads.latestByReference().put(read.tradeReference(), read);
        });
  }

  private List<String> recentKeys(List<SebFundConfirmationCheckFailure> failures) {
    try {
      return s3Source.recentKeys();
    } catch (RuntimeException e) {
      log.error("SEB fund confirmation listing failed: error={}", e.getMessage(), e);
      failures.add(
          new SebFundConfirmationCheckFailure("kinnituste nimekirja lugemine S3-st", reason(e)));
      return List.of();
    }
  }

  private Optional<byte[]> download(String key, List<SebFundConfirmationCheckFailure> failures) {
    try {
      Optional<byte[]> bytes = s3Source.get(key);
      if (bytes.isEmpty()) {
        log.warn("SEB fund confirmation PDF disappeared before fetch: key={}", key);
      }
      return bytes;
    } catch (RuntimeException e) {
      log.error(
          "SEB fund confirmation PDF failed to download: key={}, error={}", key, e.getMessage(), e);
      failures.add(
          new SebFundConfirmationCheckFailure("kinnituse " + key + " allalaadimine", reason(e)));
      return Optional.empty();
    }
  }

  private Optional<SebFundConfirmation> parse(
      String key, byte[] bytes, List<UnreadableSebFundConfirmation> unreadable) {
    try {
      return Optional.of(parser.parse(bytes));
    } catch (RuntimeException e) {
      log.error(
          "SEB fund confirmation PDF failed to parse: key={}, error={}", key, e.getMessage(), e);
      unreadable.add(new UnreadableSebFundConfirmation(key, reason(e)));
      return Optional.empty();
    }
  }

  private Optional<SebFundConfirmationOutcome> verify(
      SebFundConfirmation confirmation, List<SebFundConfirmationCheckFailure> failures) {
    try {
      return Optional.of(
          new SebFundConfirmationOutcome(confirmation, verificationService.verify(confirmation)));
    } catch (RuntimeException e) {
      log.error(
          "SEB fund confirmation verification failed: tradeReference={}, error={}",
          confirmation.tradeReference(),
          e.getMessage(),
          e);
      failures.add(
          new SebFundConfirmationCheckFailure(
              "tehingu " + confirmation.tradeReference() + " kontroll", reason(e)));
      return Optional.empty();
    }
  }

  private void record(
      SebFundConfirmationOutcome outcome, List<SebFundConfirmationCheckFailure> failures) {
    String reference = outcome.confirmation().tradeReference();
    try {
      auditRecorder.recordVerified(outcome, JOB_ACTOR);
    } catch (RuntimeException e) {
      log.error(
          "SEB fund confirmation result not recorded: tradeReference={}, error={}",
          reference,
          e.getMessage(),
          e);
      failures.add(
          new SebFundConfirmationCheckFailure(
              "tehingu " + reference + " tulemuse salvestamine", reason(e)));
    }
  }

  private void writeNavDate(
      SebFundConfirmationOutcome outcome, List<SebFundConfirmationCheckFailure> failures) {
    String reference = outcome.confirmation().tradeReference();
    try {
      navDateWriter.write(outcome);
    } catch (OptimisticLockingFailureException e) {
      log.info(
          "SEB fund confirmation pricing date left for the next run, execution changed meanwhile:"
              + " tradeReference={}",
          reference);
    } catch (RuntimeException e) {
      log.error(
          "SEB fund confirmation pricing date not written: tradeReference={}, error={}",
          reference,
          e.getMessage(),
          e);
      failures.add(
          new SebFundConfirmationCheckFailure(
              "tehingu " + reference + " hinnapäeva salvestamine", reason(e)));
    }
  }

  private List<MissingSebFundConfirmation> missing(
      Set<String> confirmedReferences, List<SebFundConfirmationCheckFailure> failures) {
    try {
      SebFundConfirmationMissingScan scan = missingFinder.find(Set.copyOf(confirmedReferences));
      scan.incompleteReportsFailure().ifPresent(failures::add);
      return scan.missing().stream()
          .filter(trade -> !auditRecorder.wasVerified(trade.tradeReference()))
          .toList();
    } catch (RuntimeException e) {
      log.error("SEB fund confirmation missing check failed: error={}", e.getMessage(), e);
      failures.add(new SebFundConfirmationCheckFailure("puuduvate kinnituste otsing", reason(e)));
      return List.of();
    }
  }

  private static String reason(RuntimeException e) {
    return Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName());
  }

  private record Uploads(
      Map<String, SebFundConfirmation> latestByReference,
      Set<String> confirmedReferences,
      List<UnreadableSebFundConfirmation> unreadable,
      List<MisnamedSebFundConfirmation> misnamed) {

    Uploads() {
      this(new LinkedHashMap<>(), new HashSet<>(), new ArrayList<>(), new ArrayList<>());
    }
  }
}
