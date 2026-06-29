package ee.tuleva.onboarding.investment.instrument;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;

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
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class InstrumentValidationListener {

  private final InstrumentDataValidator validator;
  private final ModelPortfolioAllocationRepository allocationRepository;
  private final EmailService emailService;
  private final Clock clock;

  @EventListener(InstrumentCacheRefreshedEvent.class)
  public void onCacheRefreshed() {
    var today = LocalDate.now(clock);
    var allFindings = new ArrayList<FundFindings>();

    for (var fund : TulevaFund.values()) {
      if (!fund.hasNavCalculation()) {
        continue;
      }

      var allocations = allocationRepository.findLatestByFundAsOf(fund, today);
      if (allocations.isEmpty()) {
        continue;
      }

      var effectiveDate = allocations.getFirst().getEffectiveDate();
      var findings = validator.validate(fund, effectiveDate);

      if (!findings.isEmpty()) {
        allFindings.add(new FundFindings(fund, effectiveDate, findings));
      }
    }

    if (allFindings.isEmpty()) {
      return;
    }

    var hasFailures =
        allFindings.stream()
            .flatMap(f -> f.findings().stream())
            .anyMatch(f -> f.severity() == Severity.FAIL);

    if (hasFailures) {
      sendAlert(allFindings);
    }
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
