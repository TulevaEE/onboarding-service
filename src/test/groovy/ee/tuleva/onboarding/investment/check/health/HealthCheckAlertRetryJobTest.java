package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class HealthCheckAlertRetryJobTest {

  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 10, 30);
  private static final Instant FOUND_AT = Instant.parse("2026-10-31T07:00:00Z");
  private static final Instant NOW = Instant.parse("2026-10-31T07:01:00Z");
  private static final String MESSAGE =
      "Import warning: SEB 2026-10-30 — TUV100 not imported\n"
          + "Funds: TUK75 imported, TUK00 imported, TUV100 not in the report, TKF100 imported\n"
          + "\n⚠️ [WARNING] FUND_IN_REPORT TUV100: TUV100: not in SEB's position report for"
          + " navDate=2026-10-30. Ask SEB to resend the report with TUV100 before the 15:20 NAV";
  private static final RuntimeException NO_ANSWER =
      new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"));

  @Mock OperationsNotificationService notificationService;
  @Mock HealthCheckAlertRepository alertRepository;

  HealthCheckAlertRetryJob job;

  @BeforeEach
  void setUp() {
    job = new HealthCheckAlertRetryJob(notificationService, alertRepository, Clock.fixed(NOW, UTC));
  }

  @Test
  void postsAnAlertNotYetPostedSayingWhenItsStateWasFoundAndMarksItPosted() {
    givenNotPosted(alert(1L, FOUND_AT));

    job.postAlertsNotYetPosted();

    then(notificationService)
        .should()
        .sendMessage(
            "Import warning: SEB 2026-10-30 — TUV100 not imported"
                + " (found 2026-10-31 09:00, posted late)\n"
                + "Funds: TUK75 imported, TUK00 imported, TUV100 not in the report, TKF100"
                + " imported\n"
                + "\n⚠️ [WARNING] FUND_IN_REPORT TUV100: TUV100: not in SEB's position report for"
                + " navDate=2026-10-30. Ask SEB to resend the report with TUV100 before the 15:20"
                + " NAV",
            INVESTMENT);
    then(alertRepository).should().markPosted(1L, NOW);
  }

  @Test
  void givesUpAnAlertNotPostedWithinADayOfItsStateBeingFoundWithoutPostingIt() {
    givenNotPosted(alert(1L, NOW.minus(Duration.ofDays(1)).minusSeconds(1)));

    job.postAlertsNotYetPosted();

    then(alertRepository).should().giveUp(1L);
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void anAlertFoundExactlyADayAgoIsStillPosted() {
    givenNotPosted(alert(1L, NOW.minus(Duration.ofDays(1))));

    job.postAlertsNotYetPosted();

    then(notificationService).should().sendMessage(anyString(), any());
    then(alertRepository).should(never()).giveUp(anyLong());
  }

  @ParameterizedTest
  @MethodSource("refusalsOfThePayloadItself")
  void givesUpAnAlertWhosePayloadSlackRefusesAndGoesOnToTheNextOne(RuntimeException refusal) {
    var refused = alert(1L, FOUND_AT);
    var next = alert(2L, FOUND_AT.plusSeconds(60));
    givenNotPosted(refused, next);
    willThrow(refusal).given(notificationService).sendMessage(refused.lateMessage(), INVESTMENT);

    job.postAlertsNotYetPosted();

    then(alertRepository).should().giveUp(1L);
    then(notificationService).should().sendMessage(next.lateMessage(), INVESTMENT);
    then(alertRepository).should().markPosted(2L, NOW);
  }

  static Stream<RuntimeException> refusalsOfThePayloadItself() {
    return Stream.of(
        slack(BAD_REQUEST, "invalid_payload"),
        slack(BAD_REQUEST, "no_text"),
        slack(BAD_REQUEST, "too_many_attachments"),
        slack(CONTENT_TOO_LARGE, ""));
  }

  @ParameterizedTest
  @MethodSource("failuresWhereSlackTookNothing")
  void keepsAnAlertSlackDidNotTakeForTheNextRunAndPostsNoLaterAlertBeforeIt(
      RuntimeException failure) {
    var failed = alert(1L, FOUND_AT);
    var later = alert(2L, FOUND_AT.plusSeconds(60));
    givenNotPosted(failed, later);
    willThrow(failure).given(notificationService).sendMessage(failed.lateMessage(), INVESTMENT);

    job.postAlertsNotYetPosted();

    then(notificationService).should(never()).sendMessage(later.lateMessage(), INVESTMENT);
    then(alertRepository).should().findNotPosted();
    then(alertRepository).shouldHaveNoMoreInteractions();
  }

  static Stream<RuntimeException> failuresWhereSlackTookNothing() {
    return Stream.of(
        new ResourceAccessException("I/O error", new ConnectException("Connection refused")),
        slack(BAD_REQUEST, "no_team"),
        slack(NOT_FOUND, "channel_not_found"),
        slack(TOO_MANY_REQUESTS, "rate_limited"),
        HttpServerErrorException.create(
            INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], UTF_8));
  }

  @Test
  void countsAnAlertSlackGaveNoAnswerToAndPostsNoLaterAlertBeforeItsNextTry() {
    var unanswered = alert(1L, FOUND_AT);
    var later = alert(2L, FOUND_AT.plusSeconds(60));
    givenNotPosted(unanswered, later);
    willThrow(NO_ANSWER)
        .given(notificationService)
        .sendMessage(unanswered.lateMessage(), INVESTMENT);

    job.postAlertsNotYetPosted();

    then(alertRepository).should().recordUnansweredPost(1L);
    then(alertRepository).should(never()).giveUpAfterUnansweredPost(anyLong());
    then(notificationService).should(never()).sendMessage(later.lateMessage(), INVESTMENT);
  }

  @Test
  void givesUpAnAlertSlackGivesNoAnswerToForTheSecondTimeSinceItMayAlreadyBeInTheChannel() {
    var unansweredBefore = unansweredOnce(1L, FOUND_AT);
    var later = alert(2L, FOUND_AT.plusSeconds(60));
    givenNotPosted(unansweredBefore, later);
    willThrow(NO_ANSWER)
        .given(notificationService)
        .sendMessage(unansweredBefore.lateMessage(), INVESTMENT);

    job.postAlertsNotYetPosted();

    then(alertRepository).should().giveUpAfterUnansweredPost(1L);
    then(alertRepository).should(never()).recordUnansweredPost(anyLong());
    then(notificationService).should(never()).sendMessage(later.lateMessage(), INVESTMENT);
  }

  @Test
  void postsAtMostFourAlertsARunSoTheirPostsFitTheLock() {
    givenNotPosted(
        LongStream.rangeClosed(1, 5)
            .mapToObj(id -> alert(id, FOUND_AT.plusSeconds(id)))
            .toArray(HealthCheckAlert[]::new));

    job.postAlertsNotYetPosted();

    then(notificationService).should(times(4)).sendMessage(anyString(), any());
    then(alertRepository).should(never()).markPosted(5L, NOW);
  }

  @Test
  void aRetriedAlertThatCannotBeMarkedPostedDoesNotStopTheRun() {
    givenNotPosted(alert(1L, FOUND_AT), alert(2L, FOUND_AT.plusSeconds(60)));
    given(alertRepository.markPosted(1L, NOW))
        .willThrow(new DataAccessResourceFailureException("database down"));

    job.postAlertsNotYetPosted();

    then(alertRepository).should().markPosted(2L, NOW);
  }

  @Test
  void anAlertThatCannotBeMarkedGivenUpDoesNotStopTheRun() {
    givenNotPosted(alert(1L, NOW.minus(Duration.ofDays(2))), alert(2L, FOUND_AT));
    given(alertRepository.giveUp(1L))
        .willThrow(new DataAccessResourceFailureException("database down"));

    job.postAlertsNotYetPosted();

    then(alertRepository).should().markPosted(2L, NOW);
  }

  private void givenNotPosted(HealthCheckAlert... alerts) {
    given(alertRepository.findNotPosted()).willReturn(List.of(alerts));
  }

  private static HealthCheckAlert alert(long id, Instant foundAt) {
    return new HealthCheckAlert(id, SEB, REPORT_DATE, MESSAGE, foundAt, 0);
  }

  private static HealthCheckAlert unansweredOnce(long id, Instant foundAt) {
    return new HealthCheckAlert(id, SEB, REPORT_DATE, MESSAGE, foundAt, 1);
  }

  private static HttpClientErrorException slack(HttpStatus status, String body) {
    return HttpClientErrorException.create(
        status, status.getReasonPhrase(), HttpHeaders.EMPTY, body.getBytes(UTF_8), UTF_8);
  }
}
