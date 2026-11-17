package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.mandate.EmailVariablesAttachments.getNameMergeVars;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_COMPANY_ONBOARDED;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_COMPANY_WAITING_RELATED;
import static java.util.Locale.ENGLISH;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import ee.tuleva.onboarding.auth.role.CompanyRoles;
import ee.tuleva.onboarding.kyb.survey.LatestKybSurveyInputs;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.email.EmailType;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@NullMarked
@RequiredArgsConstructor
public class LegalEntityApplicantEmailSender {

  private static final Locale ESTONIAN = Locale.of("et");
  private static final List<String> TAGS = List.of("savings_fund");

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final UserService userService;
  private final LatestKybSurveyInputs latestKybSurveyInputs;
  private final CompanyRoles companyRoles;
  private final OpenedAccountRepository openedAccounts;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = REQUIRES_NEW)
  public void onLegalEntityOnboarded(LegalEntityOnboardedEvent event) {
    var company = event.getCompany();
    emailApplicant(company.registryCode().value(), company.name(), SAVINGS_FUND_COMPANY_ONBOARDED);
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = REQUIRES_NEW)
  public void onLegalEntityWaitingForRelatedPersons(
      LegalEntityWaitingForRelatedPersonsEvent event) {
    emailApplicant(
        event.registryCode().value(), event.name(), SAVINGS_FUND_COMPANY_WAITING_RELATED);
  }

  private void emailApplicant(String registryCode, String companyName, EmailType emailType) {
    try {
      var applicant = findApplicant(registryCode);

      if (applicant.isEmpty()) {
        log.warn(
            "Cannot resolve the applicant, skipping the company email: registryCode={}, emailType={}",
            registryCode,
            emailType);
        return;
      }

      send(applicant.get(), registryCode, companyName, emailType);
    } catch (RuntimeException e) {
      log.error(
          "Failed to send the company email: registryCode={}, emailType={}",
          registryCode,
          emailType,
          e);
    }
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

  private void send(User applicant, String registryCode, String companyName, EmailType emailType) {
    if (applicant.getEmail() == null) {
      log.warn(
          "Applicant has no email, skipping the company email: userId={}, emailType={}",
          applicant.getId(),
          emailType);
      return;
    }

    var templateName = emailType.getTemplateName(localeOf(applicant));
    var mergeVars = new HashMap<String, Object>(getNameMergeVars(applicant));
    mergeVars.put("recipientName", companyName);
    companyRoles
        .findCompany(registryCode)
        .ifPresent(company -> mergeVars.put("recipientAccountId", company.id().toString()));

    var message =
        emailService.newMandrillMessage(applicant.getEmail(), templateName, mergeVars, TAGS);

    emailService
        .send(applicant, message, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    applicant, response.getId(), emailType, response.getStatus()));
  }

  private Locale localeOf(User applicant) {
    return openedAccounts.prefersEnglish(applicant.getPersonalCode()) ? ENGLISH : ESTONIAN;
  }
}
