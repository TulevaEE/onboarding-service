package ee.tuleva.onboarding.savings.fund;

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

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChildAccountOpenedEmailJobTest {

  private static final Instant NINE_IN_THE_MORNING_IN_TALLINN =
      Instant.parse("2026-10-08T06:00:00Z");
  private static final Instant START_OF_TODAY_IN_TALLINN = Instant.parse("2026-10-07T21:00:00Z");
  private static final Instant THREE_DAYS_BEFORE = Instant.parse("2026-10-04T21:00:00Z");

  @Mock private OpenedChildAccountRepository repository;
  @Mock private ChildAccountOpenedEmailSender sender;
  @Mock private OperationsNotificationService notificationService;

  private ChildAccountOpenedEmailJob job() {
    return new ChildAccountOpenedEmailJob(
        Clock.fixed(NINE_IN_THE_MORNING_IN_TALLINN, ZoneOffset.UTC),
        repository,
        sender,
        notificationService);
  }

  @Test
  void welcomesAccountsThatOpenedBeforeTodayInTallinn() {
    var first = account("61506150006");
    var second = account("51506150004");
    given(repository.fetch(THREE_DAYS_BEFORE, START_OF_TODAY_IN_TALLINN))
        .willReturn(List.of(first, second));

    job().sendEmails();

    verify(sender).send(first);
    verify(sender).send(second);
  }

  @Test
  void sendsEveryEmailWhenExactlyAtTheCap() {
    given(repository.fetch(THREE_DAYS_BEFORE, START_OF_TODAY_IN_TALLINN)).willReturn(accounts(100));

    job().sendEmails();

    verify(sender, times(100)).send(any());
    verifyNoInteractions(notificationService);
  }

  @Test
  void sendsNothingAndTellsOperationsWhenThereAreSuspiciouslyManyAccounts() {
    given(repository.fetch(THREE_DAYS_BEFORE, START_OF_TODAY_IN_TALLINN)).willReturn(accounts(101));

    job().sendEmails();

    verifyNoInteractions(sender);
    verify(notificationService).sendMessage(anyString(), eq(SAVINGS), eq(ERROR));
  }

  @Test
  void keepsWelcomingTheRestWhenOneEmailFails() {
    var failing = account("61506150006");
    var next = account("51506150004");
    given(repository.fetch(THREE_DAYS_BEFORE, START_OF_TODAY_IN_TALLINN))
        .willReturn(List.of(failing, next));
    willThrow(new RuntimeException("Mandrill is down")).given(sender).send(failing);

    job().sendEmails();

    verify(sender).send(next);
  }

  private List<OpenedChildAccount> accounts(int count) {
    return IntStream.range(0, count).mapToObj(index -> account("child-" + index)).toList();
  }

  private OpenedChildAccount account(String childCode) {
    return new OpenedChildAccount(childCode, "Kati", "Tamm", false);
  }
}
