package ee.tuleva.onboarding.taxwin;

import static ee.tuleva.onboarding.taxwin.TaxDataConsent.CONSENT_SERVICE_SIGNATURE_WINDOW;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.AWAITING_SIGNATURE;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.INVALID;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.SIGNED;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class TaxDataRetentionJob {

  private static final Duration INCOME_WITHOUT_REFRESH_RETENTION = Duration.ofDays(30);

  private final TaxDataConsentRepository consentRepository;
  private final TaxIncomeMonthRepository incomeMonthRepository;
  private final TaxIncomeCorrectionRepository correctionRepository;
  private final Clock clock;

  @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "TaxDataRetentionJob_deleteExpiredData",
      lockAtMostFor = "10m",
      lockAtLeastFor = "1m")
  @Transactional
  public void deleteExpiredData() {
    Instant now = clock.instant();
    int consents =
        consentRepository.deleteByStatusInAndCreatedAtBefore(
            List.of(AWAITING_SIGNATURE, SIGNED, INVALID),
            now.minus(CONSENT_SERVICE_SIGNATURE_WINDOW));
    Instant refreshedSince = now.minus(INCOME_WITHOUT_REFRESH_RETENTION);
    int incomeMonths = incomeMonthRepository.deleteWithoutConsentRefreshedSince(refreshedSince);
    int corrections = correctionRepository.deleteWithoutConsentRefreshedSince(refreshedSince);
    log.info(
        "Deleted expired tax data: consents={}, incomeMonths={}, corrections={}",
        consents,
        incomeMonths,
        corrections);
  }
}
