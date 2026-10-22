package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonOnboardingAbandonmentReminderJobTest {

  private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
  private static final Instant VIEWED_FROM = Instant.parse("2026-10-02T12:00:00Z");
  private static final Instant VIEWED_UNTIL = Instant.parse("2026-10-29T12:00:00Z");

  @Mock private PersonOnboardingAbandonmentReminderRepository repository;
  @Mock private PersonOnboardingAbandonmentReminderSender sender;
  @Mock private OperationsNotificationService notificationService;

  private PersonOnboardingAbandonmentReminderJob job(Instant now) {
    return new PersonOnboardingAbandonmentReminderJob(
        Clock.fixed(now, ZoneOffset.UTC), repository, sender, notificationService);
  }

  @Test
  void remindsPeopleWhoLastOpenedTheSurveyBetweenThirtyAndThreeDaysAgo() {
    var firstSaver = reminder("38812121215");
    var secondSaver = reminder("38001085718");
    given(repository.fetch(VIEWED_FROM, VIEWED_UNTIL)).willReturn(List.of(firstSaver, secondSaver));

    job(NOW).sendReminders();

    verify(sender).send(firstSaver);
    verify(sender).send(secondSaver);
  }

  @Test
  void sendsEveryReminderWhenExactlyAtTheCap() {
    given(repository.fetch(VIEWED_FROM, VIEWED_UNTIL)).willReturn(reminders(200));

    job(NOW).sendReminders();

    verify(sender, times(200)).send(any());
    verifyNoInteractions(notificationService);
  }

  @Test
  void sendsNothingAndTellsOperationsWhenThereAreSuspiciouslyManyPeople() {
    given(repository.fetch(VIEWED_FROM, VIEWED_UNTIL)).willReturn(reminders(201));

    job(NOW).sendReminders();

    verifyNoInteractions(sender);
    verify(notificationService).sendMessage(anyString(), eq(SAVINGS), eq(ERROR));
  }

  @Test
  void keepsRemindingTheRestWhenOneReminderFails() {
    var failingSaver = reminder("38812121215");
    var nextSaver = reminder("38001085718");
    given(repository.fetch(VIEWED_FROM, VIEWED_UNTIL)).willReturn(List.of(failingSaver, nextSaver));
    willThrow(new RuntimeException("Mandrill is down")).given(sender).send(failingSaver);

    job(NOW).sendReminders();

    verify(sender).send(nextSaver);
  }

  private List<PersonOnboardingAbandonmentReminder> reminders(int count) {
    return IntStream.range(0, count).mapToObj(index -> reminder("saver-" + index)).toList();
  }

  private PersonOnboardingAbandonmentReminder reminder(String personalCode) {
    return new PersonOnboardingAbandonmentReminder(
        1L,
        new PersonImpl(personalCode, "Saver", "Example"),
        personalCode + "@example.com",
        Locale.of("et"));
  }
}
