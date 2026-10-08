package ee.tuleva.onboarding.mandate.email;

import static ee.tuleva.onboarding.locale.LocaleConfiguration.DEFAULT_LOCALE;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.SIGNED;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.mandate.batch.MandateBatchRepository;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MissedMandateBatchEmails {

  private final MandateBatchRepository mandateBatchRepository;
  private final MandateProcessorService mandateProcessor;
  private final EmailPersistenceService emailPersistenceService;
  private final MandateBatchEmailService mandateBatchEmailService;
  private final Clock clock;

  public List<Long> find(int days) {
    return missedBatches(days).stream().map(MissedMandateBatchEmails::idOf).toList();
  }

  public MissedEmailResend resend(int days) {
    var sent = new ArrayList<Long>();
    var failed = new ArrayList<Long>();
    missedBatches(days)
        .forEach(batch -> (emailSent(batch, DEFAULT_LOCALE) ? sent : failed).add(idOf(batch)));
    log.info("Resent missed mandate batch emails: sent={}, failed={}", sent, failed);
    return new MissedEmailResend(sent, failed);
  }

  private List<MandateBatch> missedBatches(int days) {
    final Duration TIME_THE_PROCESSING_POLLER_MAY_STILL_BE_COMPLETING_A_BATCH =
        Duration.ofMinutes(15);
    Instant now = clock.instant();
    return mandateBatchRepository
        .findAllByStatusAndCreatedDateBetweenOrderByCreatedDate(
            SIGNED,
            now.minus(Duration.ofDays(days)),
            now.minus(TIME_THE_PROCESSING_POLLER_MAY_STILL_BE_COMPLETING_A_BATCH))
        .stream()
        .filter(this::acceptedByPensionikeskus)
        .filter(batch -> !emailPersistenceService.hasEmailsForMandateBatch(idOf(batch)))
        .toList();
  }

  private boolean acceptedByPensionikeskus(MandateBatch batch) {
    return batch.getMandates().stream().allMatch(mandateProcessor::hasSucceeded);
  }

  private boolean emailSent(MandateBatch batch, Locale locale) {
    try {
      mandateBatchEmailService.sendMandateBatch(
          batch.getMandates().getFirst().getUser(), batch, locale);
    } catch (RuntimeException e) {
      log.error("Missed mandate batch email resend failed: mandateBatchId={}", batch.getId(), e);
      return false;
    }
    return emailPersistenceService.hasEmailsForMandateBatch(idOf(batch));
  }

  private static Long idOf(MandateBatch batch) {
    return requireNonNull(batch.getId(), "Mandate batch is not yet persisted");
  }
}
