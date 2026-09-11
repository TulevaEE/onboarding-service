package ee.tuleva.onboarding.hackathon;

import static ee.tuleva.onboarding.mandate.EmailVariablesAttachments.getNameMergeVars;
import static ee.tuleva.onboarding.notification.email.EmailType.HACKATHON_REGISTRATION;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class HackathonEmailService {

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;

  public void sendRegistrationConfirmation(
      User user, HackathonRegistration registration, Locale locale) {
    String templateName = HACKATHON_REGISTRATION.getTemplateName(locale);

    MandrillMessage message =
        emailService.newMandrillMessage(
            registration.getEmail(), templateName, getNameMergeVars(user), List.of("hackathon"));

    emailService
        .send(user, message, templateName)
        .ifPresentOrElse(
            status ->
                emailPersistenceService.save(
                    user, status.getId(), HACKATHON_REGISTRATION, status.getStatus()),
            () ->
                log.error(
                    "Hackathon confirmation email was not delivered: userId={}, templateName={}",
                    user.getId(),
                    templateName));
  }
}
