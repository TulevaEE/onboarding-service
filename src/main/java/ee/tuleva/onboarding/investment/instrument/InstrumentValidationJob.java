package ee.tuleva.onboarding.investment.instrument;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.instrument.InstrumentDataValidator.Severity;
import ee.tuleva.onboarding.investment.instrument.InstrumentDataValidator.ValidationFinding;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class InstrumentValidationJob {

  private final InstrumentDataValidator validator;
  private final ModelPortfolioAllocationRepository allocationRepository;
  private final EmailService emailService;
  private final Clock clock;

  @Scheduled(cron = "0 10 * * * *", zone = TIMEZONE)
  @SchedulerLock(name = "InstrumentValidationJob", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  void run() {
    var allFindings = collectFindings();

    var hasFailures =
        allFindings.stream()
            .flatMap(fundFindings -> fundFindings.findings().stream())
            .anyMatch(finding -> finding.severity() == Severity.FAIL);

    if (hasFailures) {
      sendAlert(allFindings);
    }
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

  private void sendAlert(List<FundFindings> allFindings) {
    var subject = "[FAIL] Instrument validation findings";
    var body = buildBody(allFindings);

    MandrillMessage message = new MandrillMessage();
    message.setFromEmail("funds@tuleva.ee");
    message.setFromName("Tuleva");
    message.setSubject(subject);
    message.setText(body);
    message.setPreserveRecipients(true);

    Recipient recipient = new Recipient();
    recipient.setEmail("funds@tuleva.ee");
    recipient.setType(TO);
    message.setTo(List.of(recipient));

    boolean sent = emailService.sendSystemEmail(message);
    if (sent) {
      log.info("Sent instrument validation alert: findings={}", allFindings.size());
    } else {
      log.error("Failed to send instrument validation alert");
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
