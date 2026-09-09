package ee.tuleva.onboarding.notification.email.firstpayment;

import static ee.tuleva.onboarding.notification.email.EmailType.THIRD_PILLAR_PAYMENT_ARRIVED;

import ee.tuleva.onboarding.auth.principal.Names;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.nudge.NudgeContext;
import ee.tuleva.onboarding.nudge.NudgeDecision;
import ee.tuleva.onboarding.nudge.NudgeDecisionService;
import ee.tuleva.onboarding.nudge.NudgeKey;
import ee.tuleva.onboarding.user.UserService;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThirdPillarPaymentArrivedEmailService {

  private static final DateTimeFormatter PAYMENT_DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final String LOG_IN_NUDGE = "nudge_log_in";

  private final ThirdPillarPaymentArrivedClaims claims;
  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final UserService userService;
  private final NudgeDecisionService nudgeDecisionService;

  public boolean send(FirstThirdPillarPayment payment) {
    if (!claims.claim(payment.personalCode())) {
      return false;
    }

    Optional<NudgeDecision> decision = decisionFor(payment);
    String nudge =
        payment.hasTulevaUser()
            ? decision.map(NudgeDecision::tag).orElse(NudgeKey.NONE.getTag())
            : LOG_IN_NUDGE;
    String templateName = THIRD_PILLAR_PAYMENT_ARRIVED.getTemplateName(payment.emailLanguage());
    var message =
        emailService.newMandrillMessage(
            payment.getEmail(),
            templateName,
            mergeVars(payment, decision),
            List.of("third_pillar_payment_arrived", nudge));

    return emailService
        .send(payment, message, templateName)
        .map(
            response -> {
              emailPersistenceService.save(
                  payment,
                  response.getId(),
                  THIRD_PILLAR_PAYMENT_ARRIVED,
                  response.getStatus(),
                  nudge);
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

  private Optional<NudgeDecision> decisionFor(FirstThirdPillarPayment payment) {
    if (!payment.hasTulevaUser()) {
      return Optional.empty();
    }
    try {
      return userService
          .findByPersonalCode(payment.personalCode())
          .map(
              user -> nudgeDecisionService.decide(user, NudgeContext.THIRD_PILLAR_PAYMENT_ARRIVED));
    } catch (RuntimeException e) {
      log.warn("Sending the payment arrived email without a nudge, the decision failed", e);
      return Optional.empty();
    }
  }

  private Map<String, Object> mergeVars(
      FirstThirdPillarPayment payment, Optional<NudgeDecision> decision) {
    Map<String, Object> vars = new HashMap<>();
    vars.put("fname", Names.formatted(payment.getFirstName()));
    vars.put("lname", Names.formatted(payment.getLastName()));
    vars.put("paymentDate", payment.firstPaymentDate().format(PAYMENT_DATE_FORMAT));
    vars.put("hasTulevaUser", payment.hasTulevaUser());
    decision.ifPresent(
        nudge -> vars.putAll(nudge.mergeVars(Locale.forLanguageTag(payment.emailLanguage()))));
    return vars;
  }
}
