package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.mandate.email.EmailVariablesAttachments.getNameMergeVars;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_COMPANY_ONBOARDED;

import ee.tuleva.onboarding.kyb.survey.LatestKybSurveyInputs;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@NullMarked
@RequiredArgsConstructor
public class LegalEntityOnboardedEmailSender {

  private static final Locale LOCALE = Locale.of("et");
  private static final List<String> TAGS = List.of("savings_fund");

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final UserService userService;
  private final LatestKybSurveyInputs latestKybSurveyInputs;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onLegalEntityOnboarded(LegalEntityOnboardedEvent event) {
    var registryCode = event.getCompany().registryCode().value();
    var applicant = findApplicant(registryCode);

    if (applicant.isEmpty()) {
      log.warn(
          "Cannot resolve the applicant, skipping the company onboarded email: registryCode={}",
          registryCode);
      return;
    }

    send(applicant.get(), event.getCompany().name());
  }

  private Optional<User> findApplicant(String registryCode) {
    try {
      var inputs = latestKybSurveyInputs.findByRegistryCode(registryCode);
      return userService.findByPersonalCode(inputs.personalCode().value());
    } catch (RuntimeException e) {
      log.error("Failed to resolve the applicant: registryCode={}", registryCode, e);
      return Optional.empty();
    }
  }

  private void send(User applicant, String companyName) {
    if (applicant.getEmail() == null) {
      log.warn(
          "Applicant has no email, skipping the company onboarded email: userId={}",
          applicant.getId());
      return;
    }

    var templateName = SAVINGS_FUND_COMPANY_ONBOARDED.getTemplateName(LOCALE);
    var mergeVars = new HashMap<String, Object>(getNameMergeVars(applicant));
    mergeVars.put("recipientName", companyName);

    var message =
        emailService.newMandrillMessage(applicant.getEmail(), templateName, mergeVars, TAGS, null);

    emailService
        .send(applicant, message, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    applicant,
                    response.getId(),
                    SAVINGS_FUND_COMPANY_ONBOARDED,
                    response.getStatus()));
  }
}
