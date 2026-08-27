package ee.tuleva.onboarding.notification.email.firstpayment;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.THIRD_PILLAR_PAYMENT_ARRIVED;

import ee.tuleva.onboarding.analytics.transaction.thirdpillar.FirstThirdPillarPayment;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.savings.fund.SavingsFundFees;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThirdPillarPaymentArrivedEmailService {

  private static final DateTimeFormatter PAYMENT_DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private final ThirdPillarPaymentArrivedClaims claims;
  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final SavingsFundFees savingsFundFees;

  public boolean send(FirstThirdPillarPayment payment) {
    if (!claims.claim(payment.personalCode())) {
      return false;
    }

    String templateName = THIRD_PILLAR_PAYMENT_ARRIVED.getTemplateName(payment.emailLanguage());
    var message =
        emailService.newMandrillMessage(
            payment.getEmail(), templateName, mergeVars(payment), tags(payment), null);

    return emailService
        .send(payment, message, templateName)
        .map(
            response -> {
              emailPersistenceService.save(
                  payment, response.getId(), THIRD_PILLAR_PAYMENT_ARRIVED, response.getStatus());
              return true;
            })
        .orElseGet(
            () -> {
              log.error(
                  "Payment arrived email failed to send, keeping claim: personalCode={}",
                  payment.personalCode());
              return false;
            });
  }

  private Map<String, Object> mergeVars(FirstThirdPillarPayment payment) {
    return Map.ofEntries(
        Map.entry("fname", payment.getFirstName()),
        Map.entry("lname", payment.getLastName()),
        Map.entry("paymentDate", payment.firstPaymentDate().format(PAYMENT_DATE_FORMAT)),
        Map.entry("hasTulevaUser", payment.hasTulevaUser()),
        Map.entry("leftSecondPillar", payment.leftSecondPillar()),
        Map.entry("suggestSecondPillar", payment.suggestSecondPillar()),
        Map.entry("suggestPaymentRate", payment.suggestPaymentRate()),
        Map.entry("suggestMembership", payment.suggestMembership()),
        Map.entry("suggestSavingsFund", payment.suggestSavingsFund()),
        Map.entry("suggestThirdPillarRecurringPayment", true),
        Map.entry("suggestThirdPillarRaise", false),
        Map.entry("suggestSavingsFundRecurringPayment", false),
        Map.entry(
            "savingsFundFee",
            savingsFundFees.ongoingChargesPercent(Locale.forLanguageTag(payment.emailLanguage()))));
  }

  private List<String> tags(FirstThirdPillarPayment payment) {
    List<String> tags = new ArrayList<>();
    tags.add("third_pillar_payment_arrived");
    tags.add(renderedNudgeTag(payment));
    return tags;
  }

  private String renderedNudgeTag(FirstThirdPillarPayment payment) {
    if (!payment.hasTulevaUser()) return "nudge_log_in";
    if (payment.suggestSecondPillar()) return "nudge_second_pillar";
    if (payment.suggestPaymentRate()) return "nudge_payment_rate";
    return "nudge_third_pillar_recurring";
  }
}
