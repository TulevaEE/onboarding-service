package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
class FirstPaymentReminderJobTest {

  private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
  private static final Instant OPENED_FROM = Instant.parse("2026-09-01T12:00:00Z");
  private static final Instant OPENED_UNTIL = Instant.parse("2026-09-24T12:00:00Z");

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Mock private FirstPaymentReminderRepository repository;
  @Mock private FirstPaymentReminderSender sender;
  @Mock private OperationsNotificationService notificationService;

  private FirstPaymentReminderJob job() {
    return new FirstPaymentReminderJob(clock, repository, sender, notificationService);
  }

  @Test
  void remindsSaversWhoOpenedAnAccountBetweenThirtyAndSevenDaysAgo() {
    var firstSaver = reminder("38812121215");
    var secondSaver = reminder("38001085718");
    given(repository.fetchForAdults(OPENED_FROM, OPENED_UNTIL))
        .willReturn(List.of(firstSaver, secondSaver));

    job().sendReminders();

    verify(sender).send(firstSaver);
    verify(sender).send(secondSaver);
  }

  @Test
  void remindsParentsAboutChildAccountsInTheSameRun() {
    var adult = reminder("38812121215");
    var childAccount = reminder("61506150006");
    given(repository.fetchForAdults(OPENED_FROM, OPENED_UNTIL)).willReturn(List.of(adult));
    given(repository.fetchForChildren(OPENED_FROM, OPENED_UNTIL)).willReturn(List.of(childAccount));

    job().sendReminders();

    verify(sender).send(adult);
    verify(sender).send(childAccount);
  }

  @Test
  void sendsNothingWhenThereAreSuspiciouslyManyRecipients() {
    given(repository.fetchForAdults(OPENED_FROM, OPENED_UNTIL)).willReturn(reminders(51));

    job().sendReminders();

    verifyNoInteractions(sender);
  }

  @Test
  void sendsEveryReminderWhenTheSegmentIsExactlyAtTheCap() {
    given(repository.fetchForAdults(OPENED_FROM, OPENED_UNTIL)).willReturn(reminders(50));

    job().sendReminders();

    verify(sender, times(50)).send(any());
    verifyNoInteractions(notificationService);
  }

  @Test
  void tellsOperationsWhenASegmentTripsTheCap() {
    given(repository.fetchForAdults(OPENED_FROM, OPENED_UNTIL)).willReturn(reminders(51));

    job().sendReminders();

    verify(notificationService).sendMessage(contains("adults"), eq(SAVINGS), eq(ERROR));
  }

  @Test
  void oneSegmentGoingWrongDoesNotHoldUpTheOther() {
    var adult = reminder("38812121215");
    given(repository.fetchForAdults(OPENED_FROM, OPENED_UNTIL)).willReturn(List.of(adult));
    given(repository.fetchForChildren(OPENED_FROM, OPENED_UNTIL)).willReturn(reminders(51));

    job().sendReminders();

    verify(sender).send(adult);
    verify(sender, times(1)).send(any());
  }

  @Test
  void keepsRemindingTheRestWhenOneReminderFails() {
    var failingSaver = reminder("38812121215");
    var nextSaver = reminder("38001085718");
    given(repository.fetchForAdults(OPENED_FROM, OPENED_UNTIL))
        .willReturn(List.of(failingSaver, nextSaver));
    willThrow(new RuntimeException("Mandrill is down")).given(sender).send(failingSaver);

    job().sendReminders();

    verify(sender).send(nextSaver);
  }

  @Test
  void doesNotReachBackToSaversTheManualCampaignAlreadyReminded() {
    var justAfterTheCampaign = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
    var campaignCutoff = Instant.parse("2026-08-11T21:00:00Z");
    var saver = reminder("38812121215");
    given(repository.fetchForAdults(campaignCutoff, Instant.parse("2026-08-25T12:00:00Z")))
        .willReturn(List.of(saver));

    new FirstPaymentReminderJob(justAfterTheCampaign, repository, sender, notificationService)
        .sendReminders();

    verify(sender).send(saver);
  }

  private List<FirstPaymentReminder> reminders(int count) {
    return IntStream.range(0, count).mapToObj(index -> reminder("saver-" + index)).toList();
  }

  private FirstPaymentReminder reminder(String personalCode) {
    return new FirstPaymentReminder(
        personalCode,
        "Saver",
        "Example",
        personalCode + "@example.com",
        Locale.of("et"),
        SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON,
        null);
  }
}
