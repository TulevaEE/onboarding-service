package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
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
class AccountOpenedEmailJobTest {

  private static final Instant NINE_IN_THE_MORNING_IN_TALLINN =
      Instant.parse("2026-10-08T06:00:00Z");
  private static final Instant START_OF_TODAY_IN_TALLINN = Instant.parse("2026-10-07T21:00:00Z");
  private static final Instant THREE_DAYS_BEFORE = Instant.parse("2026-10-04T21:00:00Z");

  private static final OpenedAccount CHILD =
      new OpenedAccount("61506150006", "Kati", "Tamm", null, false, true, false);
  private static final OpenedAccount ADULT = account("38812121215");
  private static final OpenedAccount FOREIGNER = account("PNOGB-1234567890");

  @Mock private OpenedAccountRepository repository;
  @Mock private ChildAccountOpenedEmailSender childSender;
  @Mock private AdultAccountOpenedEmailSender adultSender;
  @Mock private OperationsNotificationService notificationService;

  private AccountOpenedEmailJob job() {
    return new AccountOpenedEmailJob(
        Clock.fixed(NINE_IN_THE_MORNING_IN_TALLINN, ZoneOffset.UTC),
        repository,
        childSender,
        adultSender,
        notificationService);
  }

  @Test
  void welcomesAMinorsAccountThroughTheParentAndAnAdultsDirectly() {
    givenOpened(List.of(CHILD, ADULT));

    job().sendEmails();

    verify(childSender).send(CHILD);
    verify(adultSender).send(ADULT);
    verify(childSender, never()).send(ADULT);
    verify(adultSender, never()).send(CHILD);
  }

  @Test
  void treatsACodeThatIsNotAnEstonianPersonalCodeAsAnAdult() {
    givenOpened(List.of(FOREIGNER));

    job().sendEmails();

    verify(adultSender).send(FOREIGNER);
    verifyNoInteractions(childSender);
  }

  @Test
  void leavesOutAccountsNobodyCanBeWelcomedFor() {
    var unrepresentedMinor =
        new OpenedAccount("61506150006", "Kati", "Tamm", null, false, false, false);
    var adultWithoutEmail =
        new OpenedAccount("38812121215", "Mari", "Tamm", " ", false, false, false);
    var adultUnderGuardianship =
        new OpenedAccount("38812121215", "Mari", "Tamm", "mari@example.com", false, true, false);
    givenOpened(List.of(unrepresentedMinor, adultWithoutEmail, adultUnderGuardianship));

    job().sendEmails();

    verifyNoInteractions(childSender, adultSender);
  }

  @Test
  void welcomesAnAdultWhoPrefersEnglish() {
    var adultPreferringEnglish =
        new OpenedAccount("38812121215", "Mari", "Tamm", "mari@example.com", true, false, false);
    givenOpened(List.of(adultPreferringEnglish));

    job().sendEmails();

    verify(adultSender).send(adultPreferringEnglish);
  }

  @Test
  void countsOnlyAccountsThatCanBeWelcomedTowardsTheCap() {
    var adultWithoutEmail =
        new OpenedAccount("38812121215", "Mari", "Tamm", null, false, false, false);
    var accounts = new java.util.ArrayList<>(adults(100));
    accounts.add(adultWithoutEmail);
    givenOpened(accounts);

    job().sendEmails();

    verify(adultSender, times(100)).send(ADULT);
    verifyNoInteractions(notificationService);
  }

  @Test
  void sendsEveryEmailWhenExactlyAtTheCap() {
    givenOpened(adults(100));

    job().sendEmails();

    verify(adultSender, times(100)).send(any());
    verifyNoInteractions(notificationService);
  }

  @Test
  void sendsNothingAndTellsOperationsWhenThereAreSuspiciouslyManyAccounts() {
    givenOpened(adults(101));

    job().sendEmails();

    verifyNoInteractions(childSender, adultSender);
    verify(notificationService).sendMessage(anyString(), eq(SAVINGS), eq(ERROR));
  }

  @Test
  void keepsWelcomingTheRestWhenOneEmailFails() {
    givenOpened(List.of(CHILD, ADULT));
    willThrow(new RuntimeException("Mandrill is down")).given(childSender).send(CHILD);

    job().sendEmails();

    verify(adultSender).send(ADULT);
  }

  private void givenOpened(List<OpenedAccount> accounts) {
    given(repository.fetch(THREE_DAYS_BEFORE, START_OF_TODAY_IN_TALLINN)).willReturn(accounts);
  }

  private static List<OpenedAccount> adults(int count) {
    return IntStream.range(0, count).mapToObj(index -> ADULT).toList();
  }

  private static OpenedAccount account(String code) {
    return new OpenedAccount(code, "Kati", "Tamm", "kati@example.com", false, false, false);
  }
}
