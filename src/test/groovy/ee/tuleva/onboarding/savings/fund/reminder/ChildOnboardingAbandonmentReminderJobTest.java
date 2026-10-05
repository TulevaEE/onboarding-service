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
class ChildOnboardingAbandonmentReminderJobTest {

  private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
  private static final Instant STARTED_FROM = Instant.parse("2026-10-02T12:00:00Z");
  private static final Instant STARTED_UNTIL = Instant.parse("2026-10-29T12:00:00Z");

  @Mock private ChildOnboardingAbandonmentReminderRepository repository;
  @Mock private ChildOnboardingAbandonmentReminderSender sender;
  @Mock private OperationsNotificationService notificationService;

  private ChildOnboardingAbandonmentReminderJob job(Instant now) {
    return new ChildOnboardingAbandonmentReminderJob(
        Clock.fixed(now, ZoneOffset.UTC), repository, sender, notificationService);
  }

  @Test
  void remindsParentsWhoStartedAChildAccountBetweenThirtyAndThreeDaysAgo() {
    var firstParent = reminder("38812121215");
    var secondParent = reminder("38001085718");
    given(repository.fetch(STARTED_FROM, STARTED_UNTIL))
        .willReturn(List.of(firstParent, secondParent));

    job(NOW).sendReminders();

    verify(sender).send(firstParent);
    verify(sender).send(secondParent);
  }

  @Test
  void sendsEveryReminderWhenExactlyAtTheCap() {
    given(repository.fetch(STARTED_FROM, STARTED_UNTIL)).willReturn(reminders(100));

    job(NOW).sendReminders();

    verify(sender, times(100)).send(any());
    verifyNoInteractions(notificationService);
  }

  @Test
  void sendsNothingAndTellsOperationsWhenThereAreSuspiciouslyManyParents() {
    given(repository.fetch(STARTED_FROM, STARTED_UNTIL)).willReturn(reminders(101));

    job(NOW).sendReminders();

    verifyNoInteractions(sender);
    verify(notificationService).sendMessage(anyString(), eq(SAVINGS), eq(ERROR));
  }

  @Test
  void keepsRemindingTheRestWhenOneReminderFails() {
    var failingParent = reminder("38812121215");
    var nextParent = reminder("38001085718");
    given(repository.fetch(STARTED_FROM, STARTED_UNTIL))
        .willReturn(List.of(failingParent, nextParent));
    willThrow(new RuntimeException("Mandrill is down")).given(sender).send(failingParent);

    job(NOW).sendReminders();

    verify(sender).send(nextParent);
  }

  private List<ChildOnboardingAbandonmentReminder> reminders(int count) {
    return IntStream.range(0, count).mapToObj(index -> reminder("parent-" + index)).toList();
  }

  private ChildOnboardingAbandonmentReminder reminder(String personalCode) {
    return new ChildOnboardingAbandonmentReminder(
        1L,
        new PersonImpl(personalCode, "Parent", "Example"),
        personalCode + "@example.com",
        Locale.of("et"));
  }
}
