package ee.tuleva.onboarding.hackathon;

import static ee.tuleva.onboarding.mandate.email.EmailVariablesAttachments.getNameMergeVars;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.HACKATHON_REGISTRATION;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
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
    if (user.getEmail() == null || user.getEmail().isBlank()) {
      log.warn(
          "User profile has no email, hackathon confirmation will not be sent: userId={}",
          user.getId());
      return;
    }

    String templateName = HACKATHON_REGISTRATION.getTemplateName(locale);

    MandrillMessage message =
        emailService.newMandrillMessage(
            registration.getEmail(),
            templateName,
            getNameMergeVars(user),
            List.of("hackathon"),
            null);

    emailService
        .send(user, message, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    user, response.getId(), HACKATHON_REGISTRATION, response.getStatus()));
  }
}
