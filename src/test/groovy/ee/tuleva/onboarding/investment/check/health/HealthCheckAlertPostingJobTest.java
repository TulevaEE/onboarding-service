package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.GONE;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.REQUEST_TIMEOUT;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class HealthCheckAlertPostingJobTest {

  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 10, 30);
  private static final Instant FOUND_AT = Instant.parse("2026-10-31T07:00:00Z");
  private static final Instant NOW = Instant.parse("2026-10-31T07:00:30Z");
  private static final Instant FOUND_TWO_HOURS_AGO = NOW.minus(Duration.ofHours(2));
  private static final String MESSAGE =
      "Import warning: SEB 2026-10-30 — TUV100 not imported\n"
          + "Funds: TUK75 imported, TUK00 imported, TUV100 not in the report, TKF100 imported\n"
          + "\n⚠️ [WARNING] FUND_IN_REPORT TUV100: TUV100: not in SEB's position report for"
          + " navDate=2026-10-30. Ask SEB to resend the report with TUV100 before the 15:20 NAV";

  @Mock OperationsNotificationService notificationService;
  @Mock HealthCheckAlertRepository alertRepository;

  @BeforeEach
  void attemptsCanBeCounted() {
    lenient().doReturn(true).when(alertRepository).claimAttempt(anyLong(), anyInt(), any());
  }

  @Test
  void countsTheAttemptItSawBeforePostingAWaitingAlertWithItsNormalTextAndThenMarksItPosted() {
    givenNotPosted(tried(1L, FOUND_AT, 2, NOW.minus(Duration.ofMinutes(2))));

    jobAt(NOW).postAlertsNotYetPosted();

    var order = inOrder(alertRepository, notificationService);
    order.verify(alertRepository).claimAttempt(1L, 2, NOW);
    order.verify(notificationService).sendMessage(MESSAGE, INVESTMENT);
    order.verify(alertRepository).markPosted(1L, NOW);
  }

  @Test
  void anAlertAnotherRunClaimedMeanwhileIsNotPostedAndStopsTheRun() {
    givenNotPosted(waiting(1L, FOUND_AT), waiting(2L, FOUND_AT.plusSeconds(1)));
    given(alertRepository.claimAttempt(1L, 0, NOW)).willReturn(false);

    jobAt(NOW).postAlertsNotYetPosted();

    then(notificationService).shouldHaveNoInteractions();
    then(alertRepository).should(never()).claimAttempt(eq(2L), anyInt(), any());
  }

  @Test
  void eachPostIsTimedWhenItGoesOutSoAPostLateInALongRunIsCountedAndLabelledThen() {
    var clock = new AdvancingClock(NOW);
    var foundTwoMinutesBeforeTheRun = NOW.minus(Duration.ofMinutes(2));
    givenNotPosted(
        LongStream.rangeClosed(1, 4)
            .mapToObj(
                id ->
                    new HealthCheckAlert(
                        id, SEB, REPORT_DATE, "alert " + id, foundTwoMinutesBeforeTheRun, 0, null))
            .toArray(HealthCheckAlert[]::new));
    willAnswer(
            invocation -> {
              clock.advance(Duration.ofSeconds(70));
              return null;
            })
        .given(notificationService)
        .sendMessage(anyString(), any());

    new HealthCheckAlertPostingJob(notificationService, alertRepository, clock)
        .postAlertsNotYetPosted();

    then(alertRepository).should().claimAttempt(3L, 0, NOW.plus(Duration.ofSeconds(140)));
    then(alertRepository).should().claimAttempt(4L, 0, NOW.plus(Duration.ofSeconds(210)));
    then(notificationService).should().sendMessage("alert 3", INVESTMENT);
    then(notificationService)
        .should()
        .sendMessage("alert 4 (found 2026-10-31 08:58, posted late)", INVESTMENT);
  }

  @Test
  void anAlertPostedMoreThanFiveMinutesAfterItsStateWasFoundSaysWhenItWasFound() {
    givenNotPosted(waiting(1L, FOUND_AT));

    jobAt(Instant.parse("2026-10-31T07:20:30Z")).postAlertsNotYetPosted();

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
  }

  @Test
  void anAlertWhoseAttemptCannotBeCountedIsNotPostedAndStopsTheRun() {
    givenNotPosted(waiting(1L, FOUND_AT), waiting(2L, FOUND_AT.plusSeconds(1)));
    given(alertRepository.claimAttempt(1L, 0, NOW))
        .willThrow(new DataAccessResourceFailureException("database down"));

    jobAt(NOW).postAlertsNotYetPosted();

    then(notificationService).shouldHaveNoInteractions();
  }

  @ParameterizedTest
  @MethodSource("failuresBeforeTheRequestWentOut")
  void aPostSlackNeverReceivedGetsItsAttemptAndItsEarlierAttemptTimeBackAndStopsTheRun(
      RuntimeException notSent) {
    var earlierAttempt = NOW.minus(Duration.ofMinutes(2));
    var refused = tried(1L, FOUND_AT, 2, earlierAttempt);
    var later = waiting(2L, FOUND_AT.plusSeconds(1));
    givenNotPosted(refused, later);
    willThrow(notSent).given(notificationService).sendMessage(MESSAGE, INVESTMENT);

    jobAt(NOW).postAlertsNotYetPosted();

    then(alertRepository).should().refundAttempt(1L, 3, earlierAttempt);
    then(alertRepository).should(never()).claimAttempt(eq(2L), anyInt(), any());
    then(alertRepository).should(never()).giveUp(anyLong());
    then(alertRepository).should(never()).markPosted(anyLong(), any());
  }

  @Test
  void aFirstPostSlackNeverReceivedLeavesTheAlertAsIfNeverTried() {
    givenNotPosted(waiting(1L, FOUND_AT));
    willThrow(io(new ConnectException("Connection refused")))
        .given(notificationService)
        .sendMessage(MESSAGE, INVESTMENT);

    jobAt(NOW).postAlertsNotYetPosted();

    then(alertRepository).should().refundFirstAttempt(1L);
    then(alertRepository).should(never()).refundAttempt(anyLong(), anyInt(), any());
  }

  static Stream<RuntimeException> failuresBeforeTheRequestWentOut() {
    return Stream.of(
        io(new ConnectException("Connection refused")),
        io(new ConnectTimeoutException("Connect timed out")),
        io(new HttpConnectTimeoutException("HTTP connect timed out")),
        io(new UnknownHostException("hooks.slack.com")),
        new IllegalStateException("No webhook for slack channel INVESTMENT"),
        slack(BAD_REQUEST, "no_team"),
        slack(UNAUTHORIZED, ""),
        slack(FORBIDDEN, "action_prohibited"),
        slack(NOT_FOUND, "channel_not_found"),
        slack(GONE, "channel_is_archived"),
        slack(TOO_MANY_REQUESTS, "rate_limited"));
  }

  @ParameterizedTest
  @MethodSource("refusalsOfThePayloadItself")
  void givesUpAnAlertWhosePayloadSlackRefusesAndGoesOnToTheNextOne(RuntimeException refusal) {
    var refused = waiting(1L, FOUND_AT);
    var next = new HealthCheckAlert(2L, SEB, REPORT_DATE, "next", FOUND_AT, 0, null);
    givenNotPosted(refused, next);
    willThrow(refusal).given(notificationService).sendMessage(MESSAGE, INVESTMENT);

    jobAt(NOW).postAlertsNotYetPosted();

    then(alertRepository).should().giveUp(1L);
    then(notificationService).should().sendMessage("next", INVESTMENT);
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
  @MethodSource("failuresSlackMayHaveReceived")
  void anUncertainPostKeepsItsAttemptAndStopsTheRunSoNoLaterAlertOvertakesIt(
      RuntimeException uncertain) {
    givenNotPosted(waiting(1L, FOUND_AT), waiting(2L, FOUND_AT.plusSeconds(1)));
    willThrow(uncertain).given(notificationService).sendMessage(MESSAGE, INVESTMENT);

    jobAt(NOW).postAlertsNotYetPosted();

    then(alertRepository).should().findNotPosted();
    then(alertRepository).should().claimAttempt(1L, 0, NOW);
    then(alertRepository).shouldHaveNoMoreInteractions();
    then(notificationService).should(times(1)).sendMessage(anyString(), any());
  }

  static Stream<RuntimeException> failuresSlackMayHaveReceived() {
    return Stream.of(
        io(new SocketTimeoutException("Read timed out")),
        io(new SocketException("Connection reset")),
        io(new NoHttpResponseException("hooks.slack.com:443 failed to respond")),
        HttpServerErrorException.create(
            INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], UTF_8),
        slack(REQUEST_TIMEOUT, ""),
        slack(BAD_REQUEST, "invalid_blocks"),
        new RestClientException("anything else"));
  }

  @ParameterizedTest
  @CsvSource({"1, 1", "2, 2", "3, 4", "4, 8", "5, 16", "6, 32", "7, 60", "12, 60"})
  void anAlertIsTriedAgainOnceTheGapAfterItsAttemptsHasPassed(int attempts, int gapMinutes) {
    givenNotPosted(
        tried(1L, FOUND_TWO_HOURS_AGO, attempts, NOW.minus(Duration.ofMinutes(gapMinutes))));

    jobAt(NOW).postAlertsNotYetPosted();

    then(notificationService).should().sendMessage(anyString(), any());
  }

  @ParameterizedTest
  @CsvSource({"1, 1", "2, 2", "3, 4", "4, 8", "5, 16", "6, 32", "7, 60", "12, 60"})
  void anAlertWhoseGapHasNotPassedIsNotTriedAndNoLaterAlertOvertakesIt(
      int attempts, int gapMinutes) {
    var lastAttempt = NOW.minus(Duration.ofMinutes(gapMinutes)).plusSeconds(1);
    givenNotPosted(tried(1L, FOUND_TWO_HOURS_AGO, attempts, lastAttempt), waiting(2L, FOUND_AT));

    jobAt(NOW).postAlertsNotYetPosted();

    then(alertRepository).should(never()).claimAttempt(anyLong(), anyInt(), any());
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void givesUpAnAlertNotPostedWithinADayOfItsStateBeingFoundWithoutPostingIt() {
    givenNotPosted(waiting(1L, NOW.minus(Duration.ofDays(1)).minusSeconds(1)));

    jobAt(NOW).postAlertsNotYetPosted();

    then(alertRepository).should().giveUp(1L);
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void anAlertFoundExactlyADayAgoIsStillPosted() {
    givenNotPosted(waiting(1L, NOW.minus(Duration.ofDays(1))));

    jobAt(NOW).postAlertsNotYetPosted();

    then(notificationService).should().sendMessage(anyString(), any());
    then(alertRepository).should(never()).giveUp(anyLong());
  }

  @Test
  void postsAtMostFourAlertsARunSoTheirPostsFitTheLock() {
    givenNotPosted(
        LongStream.rangeClosed(1, 5)
            .mapToObj(id -> waiting(id, FOUND_AT.plusSeconds(id)))
            .toArray(HealthCheckAlert[]::new));

    jobAt(NOW).postAlertsNotYetPosted();

    then(notificationService).should(times(4)).sendMessage(anyString(), any());
    then(alertRepository).should(never()).claimAttempt(eq(5L), anyInt(), any());
  }

  @Test
  void aPostedAlertThatCannotBeMarkedPostedDoesNotStopTheRun() {
    givenNotPosted(waiting(1L, FOUND_AT), waiting(2L, FOUND_AT.plusSeconds(1)));
    given(alertRepository.markPosted(1L, NOW))
        .willThrow(new DataAccessResourceFailureException("database down"));

    jobAt(NOW).postAlertsNotYetPosted();

    then(alertRepository).should().markPosted(2L, NOW);
  }

  @Test
  void anAlertThatCannotBeMarkedGivenUpDoesNotStopTheRun() {
    givenNotPosted(waiting(1L, NOW.minus(Duration.ofDays(2))), waiting(2L, FOUND_AT));
    given(alertRepository.giveUp(1L))
        .willThrow(new DataAccessResourceFailureException("database down"));

    jobAt(NOW).postAlertsNotYetPosted();

    then(alertRepository).should().markPosted(2L, NOW);
  }

  private HealthCheckAlertPostingJob jobAt(Instant now) {
    return new HealthCheckAlertPostingJob(
        notificationService, alertRepository, Clock.fixed(now, UTC));
  }

  private void givenNotPosted(HealthCheckAlert... alerts) {
    given(alertRepository.findNotPosted()).willReturn(List.of(alerts));
  }

  private static HealthCheckAlert waiting(long id, Instant foundAt) {
    return new HealthCheckAlert(id, SEB, REPORT_DATE, MESSAGE, foundAt, 0, null);
  }

  private static HealthCheckAlert tried(
      long id, Instant foundAt, int attempts, @Nullable Instant lastAttemptAt) {
    return new HealthCheckAlert(id, SEB, REPORT_DATE, MESSAGE, foundAt, attempts, lastAttemptAt);
  }

  private static ResourceAccessException io(IOException cause) {
    return new ResourceAccessException("I/O error on POST request: " + cause.getMessage(), cause);
  }

  private static HttpClientErrorException slack(HttpStatus status, String body) {
    return HttpClientErrorException.create(
        status, status.getReasonPhrase(), HttpHeaders.EMPTY, body.getBytes(UTF_8), UTF_8);
  }

  private static final class AdvancingClock extends Clock {

    private Instant now;

    AdvancingClock(Instant start) {
      now = start;
    }

    void advance(Duration by) {
      now = now.plus(by);
    }

    @Override
    public ZoneId getZone() {
      return UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
