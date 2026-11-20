package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@NullMarked
@Slf4j
@Component
@RequiredArgsConstructor
class FtUnreadableConfirmationWarning {

  private final FtConfirmationS3Source s3Source;
  private final FtConfirmationDigest digest;

  @Value("${transaction-registry.ft-confirmation.alerts-from}")
  private final LocalDate alertsFrom;

  void publish(List<FtUnreadableConfirmation> unreadable) {
    digest.publish(
        unreadable.stream()
            .filter(this::uploadedSinceAlertsFrom)
            .map(FtCheckOutcome.class::cast)
            .toList());
  }

  private boolean uploadedSinceAlertsFrom(FtUnreadableConfirmation pdf) {
    try {
      return s3Source
          .lastModified(pdf.key())
          .map(
              uploadedAt ->
                  !uploadedAt.isBefore(alertsFrom.atStartOfDay(ZoneId.of(TIMEZONE)).toInstant()))
          .orElse(true);
    } catch (RuntimeException e) {
      log.error(
          "FT confirmation PDF upload time could not be read, warning anyway: key={}, error={}",
          pdf.key(),
          e.getMessage(),
          e);
      return true;
    }
  }
}
