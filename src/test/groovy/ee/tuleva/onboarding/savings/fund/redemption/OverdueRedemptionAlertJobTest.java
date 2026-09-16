package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.SCREENING_UNAVAILABLE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.FAILED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.IN_REVIEW;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.REDEEMED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.SavingFundDeadlinesService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OverdueRedemptionAlertJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final List<RedemptionRequest.Status> OPEN_STATUSES =
      List.of(RESERVED, IN_REVIEW, VERIFIED, REDEEMED, FAILED);
  private static final UUID REQUEST_ID = UUID.fromString("98894b5d-0326-45f6-80a7-994086571773");
  private static final Instant THURSDAY_EVENING = Instant.parse("2026-08-27T19:19:35Z");
  private static final Instant FRIDAY_0900 = Instant.parse("2026-08-28T06:00:00Z");
  private static final Instant FRIDAY_1500 = Instant.parse("2026-08-28T12:00:00Z");
  private static final Instant SATURDAY_0900 = Instant.parse("2026-08-29T06:00:00Z");
  private static final Instant MONDAY_0900 = Instant.parse("2026-08-31T06:00:00Z");
  private static final Instant TUESDAY_0900 = Instant.parse("2026-09-01T06:00:00Z");
  private static final Instant WEDNESDAY_0900 = Instant.parse("2026-09-02T06:00:00Z");
  private static final Instant THURSDAY_2230 = Instant.parse("2026-08-27T19:30:00Z");

  @Mock private RedemptionRequestRepository repository;
  @Mock private OperationsNotificationService notificationService;

  private OverdueRedemptionAlertJob jobAt(Instant now) {
    var clock = Clock.fixed(now, TALLINN);
    var publicHolidays = new PublicHolidays();
    return new OverdueRedemptionAlertJob(
        clock,
        publicHolidays,
        repository,
        new SavingFundDeadlinesService(publicHolidays, clock),
        notificationService);
  }

  private static RedemptionRequest thursdayEveningRequest(RedemptionRequest.Status status) {
    return redemptionRequestFixture()
        .id(REQUEST_ID)
        .status(status)
        .requestedAt(THURSDAY_EVENING)
        .build();
  }

  @Test
  void aHeldRequestIsNotOverdueOnTheMorningOfItsDealingDay() {
    given(repository.findByStatusIn(OPEN_STATUSES))
        .willReturn(List.of(thursdayEveningRequest(IN_REVIEW)));

    jobAt(FRIDAY_0900).alertOverdueRedemptions();

    verifyNoInteractions(notificationService);
  }

  @Test
  void aHeldRequestIsOverdueAtFifteenHundredOnItsDealingDay() {
    var request = thursdayEveningRequest(IN_REVIEW);
    request.setHoldReason(SCREENING_UNAVAILABLE);
    given(repository.findByStatusIn(OPEN_STATUSES)).willReturn(List.of(request));

    jobAt(FRIDAY_1500).alertOverdueRedemptions();

    verify(notificationService)
        .sendMessage(
            "AML: 1 redemption request(s) waiting past their deadline:\n"
                + "id=98894b5d-0326-45f6-80a7-994086571773, status=IN_REVIEW, amount=10.00 EUR,"
                + " requested=2026-08-27 22:19, decisionCutoff=2026-08-28 16:00,"
                + " workingDaysWaiting=1, reason=SCREENING_UNAVAILABLE",
            AML,
            ERROR);
  }

  @Test
  void aReservedRequestIsNotReportedWithinTheFirstFifteenMinutes() {
    given(repository.findByStatusIn(OPEN_STATUSES))
        .willReturn(List.of(thursdayEveningRequest(RESERVED)));

    jobAt(THURSDAY_EVENING.plusSeconds(10 * 60)).alertOverdueRedemptions();

    verifyNoInteractions(notificationService);
  }

  @Test
  void aReservedRequestStillUnverifiedAfterFifteenMinutesIsReported() {
    given(repository.findByStatusIn(OPEN_STATUSES))
        .willReturn(List.of(thursdayEveningRequest(RESERVED)));

    jobAt(FRIDAY_0900).alertOverdueRedemptions();

    verify(notificationService)
        .sendMessage(
            "AML: 1 redemption request(s) waiting past their deadline:\n"
                + "id=98894b5d-0326-45f6-80a7-994086571773, status=RESERVED, amount=10.00 EUR,"
                + " requested=2026-08-27 22:19, decisionCutoff=2026-08-28 16:00,"
                + " workingDaysWaiting=1",
            AML,
            ERROR);
  }

  @Test
  void aVerifiedRequestWaitingForItsOwnBatchIsNotOverdue() {
    given(repository.findByStatusIn(OPEN_STATUSES))
        .willReturn(List.of(thursdayEveningRequest(VERIFIED)));

    jobAt(MONDAY_0900).alertOverdueRedemptions();

    verifyNoInteractions(notificationService);
  }

  @Test
  void aVerifiedRequestTheBatchDidNotPayIsOverdueTheNextMorning() {
    given(repository.findByStatusIn(OPEN_STATUSES))
        .willReturn(List.of(thursdayEveningRequest(VERIFIED)));

    jobAt(TUESDAY_0900).alertOverdueRedemptions();

    verify(notificationService)
        .sendMessage(
            "AML: 1 redemption request(s) waiting past their deadline:\n"
                + "id=98894b5d-0326-45f6-80a7-994086571773, status=VERIFIED, amount=10.00 EUR,"
                + " requested=2026-08-27 22:19, decisionCutoff=2026-08-28 16:00,"
                + " workingDaysWaiting=3",
            AML,
            ERROR);
  }

  @Test
  void aRequestApprovedAfterTheCutoffIsOverdueEvenThoughTheBatchDefersItAnotherDay() {
    var request = thursdayEveningRequest(VERIFIED);
    request.setReviewedAt(Instant.parse("2026-08-31T14:33:41Z"));
    given(repository.findByStatusIn(OPEN_STATUSES)).willReturn(List.of(request));

    jobAt(TUESDAY_0900).alertOverdueRedemptions();

    verify(notificationService)
        .sendMessage(
            "AML: 1 redemption request(s) waiting past their deadline:\n"
                + "id=98894b5d-0326-45f6-80a7-994086571773, status=VERIFIED, amount=10.00 EUR,"
                + " requested=2026-08-27 22:19, decisionCutoff=2026-08-28 16:00,"
                + " workingDaysWaiting=3",
            AML,
            ERROR);
  }

  @Test
  void aFailedPayoutIsAlwaysOverdueAndShowsItsError() {
    var request = thursdayEveningRequest(FAILED);
    request.setHoldReason(SCREENING_UNAVAILABLE);
    request.setErrorReason("java.lang.IllegalStateException: Beneficiary name not resolvable");
    given(repository.findByStatusIn(OPEN_STATUSES)).willReturn(List.of(request));

    jobAt(FRIDAY_0900).alertOverdueRedemptions();

    verify(notificationService)
        .sendMessage(
            "AML: 1 redemption request(s) waiting past their deadline:\n"
                + "id=98894b5d-0326-45f6-80a7-994086571773, status=FAILED, amount=10.00 EUR,"
                + " requested=2026-08-27 22:19, decisionCutoff=2026-08-28 16:00,"
                + " workingDaysWaiting=1,"
                + " error=java.lang.IllegalStateException: Beneficiary name not resolvable",
            AML,
            ERROR);
  }

  @Test
  void aRedeemedRequestNotConfirmedByTheNextWorkingDayIsOverdue() {
    given(repository.findByStatusIn(OPEN_STATUSES))
        .willReturn(List.of(thursdayEveningRequest(REDEEMED)));

    jobAt(TUESDAY_0900).alertOverdueRedemptions();
    verifyNoInteractions(notificationService);

    jobAt(WEDNESDAY_0900).alertOverdueRedemptions();
    verify(notificationService)
        .sendMessage(
            "AML: 1 redemption request(s) waiting past their deadline:\n"
                + "id=98894b5d-0326-45f6-80a7-994086571773, status=REDEEMED, amount=10.00 EUR,"
                + " requested=2026-08-27 22:19, decisionCutoff=2026-08-28 16:00,"
                + " workingDaysWaiting=4",
            AML,
            ERROR);
  }

  @Test
  void aFailingSlackSendDoesNotEscapeTheJob() {
    given(repository.findByStatusIn(OPEN_STATUSES))
        .willReturn(List.of(thursdayEveningRequest(FAILED)));
    willThrow(new IllegalStateException("Slack unavailable"))
        .given(notificationService)
        .sendMessage(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());

    jobAt(FRIDAY_0900).alertOverdueRedemptions();
  }

  @Test
  void doesNothingOnANonWorkingDay() {
    jobAt(SATURDAY_0900).alertOverdueRedemptions();

    verifyNoInteractions(repository, notificationService);
  }
}
