package ee.tuleva.onboarding.hackathon;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.hackathon.HackathonChallenge.FAIR_LENDING;
import static ee.tuleva.onboarding.hackathon.HackathonParticipation.LOOKING_FOR_TEAM;
import static ee.tuleva.onboarding.hackathon.HackathonRole.PARTICIPANT;
import static ee.tuleva.onboarding.hackathon.HackathonSkill.SOFTWARE_DEVELOPMENT;
import static java.util.Optional.empty;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HackathonEmailServiceTest {

  @Mock private ee.tuleva.onboarding.notification.email.EmailService emailService;
  @Mock private EmailPersistenceService emailPersistenceService;

  @InjectMocks private HackathonEmailService hackathonEmailService;

  private final HackathonRegistration registration =
      new HackathonRegistrationRequest(
              "participant@example.com",
              null,
              PARTICIPANT,
              List.of(SOFTWARE_DEVELOPMENT),
              List.of(FAIR_LENDING),
              LOOKING_FOR_TEAM,
              null,
              null)
          .toRegistration(999L, Instant.parse("2026-08-12T10:00:00Z"));

  @Test
  void sendRegistrationConfirmation_sendsTheLocalizedTemplateToTheRegistrationEmail() {
    User user = sampleUser().build();
    MandrillMessage message = new MandrillMessage();
    given(
            emailService.newMandrillMessage(
                "participant@example.com",
                "hackathon_registration_et",
                Map.of("fname", user.getFirstName(), "lname", user.getLastName()),
                List.of("hackathon"),
                null))
        .willReturn(message);
    given(emailService.send(user, message, "hackathon_registration_et")).willReturn(empty());

    hackathonEmailService.sendRegistrationConfirmation(user, registration, Locale.of("et"));

    verify(emailService).send(user, message, "hackathon_registration_et");
    verifyNoInteractions(emailPersistenceService);
  }

  @Test
  void sendRegistrationConfirmation_withoutAProfileEmail_stillSendsToTheRegistrationEmail() {
    User user = sampleUser().email(null).build();
    MandrillMessage message = new MandrillMessage();
    given(
            emailService.newMandrillMessage(
                "participant@example.com",
                "hackathon_registration_et",
                Map.of("fname", user.getFirstName(), "lname", user.getLastName()),
                List.of("hackathon"),
                null))
        .willReturn(message);
    given(emailService.send(user, message, "hackathon_registration_et")).willReturn(empty());

    hackathonEmailService.sendRegistrationConfirmation(user, registration, Locale.of("et"));

    verify(emailService).send(user, message, "hackathon_registration_et");
    verifyNoInteractions(emailPersistenceService);
  }
}
