package ee.tuleva.onboarding.investment.instrument;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static java.util.stream.Collectors.toSet;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.instrument.InstrumentDataValidator.Severity;
import ee.tuleva.onboarding.investment.instrument.InstrumentDataValidator.ValidationFinding;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class InstrumentValidationJob {

  private static final Duration MAX_CACHE_AGE = Duration.ofHours(3);
  private static final String ALERT_SUBJECT = "[FAIL] Instrument validation findings";
  private static final String CLEARED_SUBJECT = "[OK] Instrument validation findings cleared";
  private static final String STALE_CACHE_SUBJECT = "[STALE] Instrument reference cache";
  private static final String FUNDS_EMAIL = "funds@tuleva.ee";

  private final InstrumentDataValidator validator;
  private final ModelPortfolioAllocationRepository allocationRepository;
  private final EmailService emailService;
  private final InstrumentReferenceService instrumentReferenceService;
  private final Clock clock;

  private final AtomicReference<Set<String>> lastAlertedFindings = new AtomicReference<>(Set.of());
  private final AtomicReference<LocalDate> lastAlertDate = new AtomicReference<>();
  private final AtomicReference<LocalDate> lastStaleCacheAlertDate = new AtomicReference<>();

  @Scheduled(cron = "0 10 * * * *", zone = TIMEZONE)
  @SchedulerLock(name = "InstrumentValidationJob", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  void run() {
    alertOnStaleCache();
    alertOnFindings(collectFindings());
  }

  private void alertOnStaleCache() {
    Instant lastRefreshedAt = instrumentReferenceService.getLastRefreshedAt();
    Duration age = Duration.between(lastRefreshedAt, clock.instant());

    if (age.compareTo(MAX_CACHE_AGE) <= 0) {
      return;
    }

    var today = LocalDate.now(clock);
    if (today.equals(lastStaleCacheAlertDate.get())) {
      log.warn(
          "Instrument reference cache still stale, already alerted today: lastRefreshedAt={}, ageMinutes={}",
          lastRefreshedAt,
          age.toMinutes());
      return;
    }

    log.error(
        "Instrument reference cache is stale: lastRefreshedAt={}, ageMinutes={}",
        lastRefreshedAt,
        age.toMinutes());

    sendEmail(
        STALE_CACHE_SUBJECT,
        "The instrument reference cache has not refreshed successfully.\n\nlastRefreshedAt: %s\nage: %d minutes\n"
            .formatted(lastRefreshedAt, age.toMinutes()));
    lastStaleCacheAlertDate.set(today);
  }

  private void alertOnFindings(List<FundFindings> allFindings) {
    var findingKeys = alertableFindingKeys(allFindings);
    var today = LocalDate.now(clock);

    if (findingKeys.isEmpty()) {
      if (!lastAlertedFindings.getAndSet(Set.of()).isEmpty()) {
        lastAlertDate.set(null);
        sendEmail(CLEARED_SUBJECT, "Instrument data validation no longer reports any failure.\n");
      }
      return;
    }

    if (findingKeys.equals(lastAlertedFindings.get()) && today.equals(lastAlertDate.get())) {
      log.info(
          "Suppressing unchanged instrument validation alert: findings={}, lastAlertDate={}",
          findingKeys.size(),
          lastAlertDate.get());
      return;
    }

    sendEmail(ALERT_SUBJECT, buildBody(allFindings));
    lastAlertedFindings.set(findingKeys);
    lastAlertDate.set(today);
  }

  private static Set<String> alertableFindingKeys(List<FundFindings> allFindings) {
    var hasFailures =
        allFindings.stream()
            .flatMap(fundFindings -> fundFindings.findings().stream())
            .anyMatch(finding -> finding.severity() == Severity.FAIL);

    if (!hasFailures) {
      return Set.of();
    }

    return allFindings.stream()
        .flatMap(
            fundFindings ->
                fundFindings.findings().stream()
                    .map(
                        finding ->
                            "%s|%s|%s|%s"
                                .formatted(
                                    fundFindings.fund().getCode(),
                                    fundFindings.effectiveDate(),
                                    finding.severity(),
                                    finding.message())))
        .collect(toSet());
  }

  private List<FundFindings> collectFindings() {
    var today = LocalDate.now(clock);
    var allFindings = new ArrayList<FundFindings>();

    for (var fund : TulevaFund.values()) {
      if (!fund.hasNavCalculation()) {
        continue;
      }

      for (var effectiveDate : todaysAndUpcomingEffectiveDates(fund, today)) {
        var findings = validator.validate(fund, effectiveDate);
        if (!findings.isEmpty()) {
          allFindings.add(new FundFindings(fund, effectiveDate, findings));
        }
      }
    }

    return allFindings;
  }

  private Set<LocalDate> todaysAndUpcomingEffectiveDates(TulevaFund fund, LocalDate today) {
    var effectiveDates = new LinkedHashSet<LocalDate>();
    allocationRepository.findLatestByFundAsOf(fund, today).stream()
        .findFirst()
        .ifPresent(allocation -> effectiveDates.add(allocation.getEffectiveDate()));
    effectiveDates.addAll(allocationRepository.findFutureEffectiveDates(fund, today));
    return effectiveDates;
  }

  private void sendEmail(String subject, String body) {
    MandrillMessage message = new MandrillMessage();
    message.setFromEmail(FUNDS_EMAIL);
    message.setFromName("Tuleva");
    message.setSubject(subject);
    message.setText(body);
    message.setPreserveRecipients(true);

    Recipient recipient = new Recipient();
    recipient.setEmail(FUNDS_EMAIL);
    recipient.setType(TO);
    message.setTo(List.of(recipient));

    boolean sent = emailService.sendSystemEmail(message);
    if (sent) {
      log.info("Sent instrument validation email: subject={}", subject);
    } else {
      log.error("Failed to send instrument validation email: subject={}", subject);
    }
  }

  private static String buildBody(List<FundFindings> allFindings) {
    var sb = new StringBuilder("Instrument data validation found issues:\n\n");
    for (var fundFindings : allFindings) {
      sb.append(
          "Fund: %s (effective_date: %s)\n"
              .formatted(fundFindings.fund().getCode(), fundFindings.effectiveDate()));
      for (var finding : fundFindings.findings()) {
        sb.append("  [%s] %s\n".formatted(finding.severity(), finding.message()));
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  private record FundFindings(
      TulevaFund fund, LocalDate effectiveDate, List<ValidationFinding> findings) {}
}
