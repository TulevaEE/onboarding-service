package ee.tuleva.onboarding.notification.email;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.notification.email.EmailType.THIRD_PILLAR_SUGGEST_SECOND;
import static ee.tuleva.onboarding.notification.email.SecondPillarLetterScheduler.Trigger.MANDATE;
import static ee.tuleva.onboarding.notification.email.SecondPillarLetterScheduler.Trigger.PAYMENT_ARRIVED;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecondPillarLetterSchedulerTest {

  private final EmailService emailService = mock(EmailService.class);
  private final EmailPersistenceService emailPersistenceService =
      mock(EmailPersistenceService.class);
  private final Instant now = Instant.parse("2026-08-18T10:00:00Z");
  private final User recipient = sampleUser().firstName("mari").lastName("maasikas").build();
  private final MandrillMessage letter = new MandrillMessage();

  private final SecondPillarLetterScheduler scheduler =
      new SecondPillarLetterScheduler(
          emailService, emailPersistenceService, Clock.fixed(now, ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    given(
            emailService.newMandrillMessage(
                recipient.getEmail(),
                "third_pillar_suggest_second_et",
                Map.of("fname", "Mari", "lname", "Maasikas"),
                List.of("pillar_3.1", "suggest_2", "payment_arrived")))
        .willReturn(letter);
  }

  @Test
  void schedulesTheLetterThreeDaysOutAndRecordsTheNudgeItRenders() {
    var scheduled = mock(MandrillMessageStatus.class);
    given(scheduled.getId()).willReturn("letter-id");
    given(scheduled.getStatus()).willReturn("scheduled");
    given(emailService.send(recipient, letter, "third_pillar_suggest_second_et", now.plus(3, DAYS)))
        .willReturn(Optional.of(scheduled));

    scheduler.schedule(recipient, Locale.of("et"), PAYMENT_ARRIVED);

    verify(emailPersistenceService)
        .save(
            recipient,
            "letter-id",
            THIRD_PILLAR_SUGGEST_SECOND,
            "scheduled",
            "nudge_second_pillar");
  }

  @Test
  void tagsTheLetterWithWhatTriggeredIt() {
    given(
            emailService.newMandrillMessage(
                recipient.getEmail(),
                "third_pillar_suggest_second_en",
                Map.of("fname", "Mari", "lname", "Maasikas"),
                List.of("pillar_3.1", "suggest_2", "mandate")))
        .willReturn(letter);

    scheduler.schedule(recipient, Locale.ENGLISH, MANDATE);

    verify(emailService)
        .send(recipient, letter, "third_pillar_suggest_second_en", now.plus(3, DAYS));
  }

  @Test
  void doesNotScheduleAnotherLetterForSomeoneWhoAlreadyHasOne() {
    given(emailPersistenceService.hasPendingOrSentEmail(recipient, THIRD_PILLAR_SUGGEST_SECOND))
        .willReturn(true);

    scheduler.schedule(recipient, Locale.of("et"), PAYMENT_ARRIVED);

    verify(emailService, never()).send(any(), any(), any(), any());
    verify(emailPersistenceService, never()).save(any(), any(), any(), any(), any());
  }

  @Test
  void recordsNothingWhenMandrillDoesNotAcceptTheLetter() {
    given(emailService.send(any(), any(), any(), any())).willReturn(Optional.empty());

    scheduler.schedule(recipient, Locale.of("et"), PAYMENT_ARRIVED);

    verify(emailPersistenceService, never()).save(any(), any(), any(), any(), any());
  }
}
