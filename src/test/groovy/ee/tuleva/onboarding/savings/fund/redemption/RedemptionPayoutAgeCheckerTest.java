package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.WARNING;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYOUT_OVERDUE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionCutoff.TALLINN;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.CANCELLED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.PROCESSED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.REDEEMED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.banking.check.payment.PaymentCheckService;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedemptionPayoutAgeCheckerTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 4, 13);

  @Mock RedemptionRequestRepository redemptionRequestRepository;
  @Mock PaymentCheckService paymentCheckService;

  private RedemptionPayoutAgeChecker checker() {
    return new RedemptionPayoutAgeChecker(
        redemptionRequestRepository,
        new RedemptionPayoutDeadline(new PublicHolidays()),
        paymentCheckService);
  }

  @Test
  void aPayoutStillWithinTheBoundIsNotReported() {
    var request = stillWaiting(orderedOn(MONDAY));
    givenNotYetInitiated(request);

    // Ordered Monday, so the warning is due on Wednesday. Tuesday is too early.
    checker().checkOverduePayouts(MONDAY.plusDays(1));

    verify(paymentCheckService, never()).record(any(), any(), anyString(), anyString());
  }

  // Warned at T+2, a working day before the sisekord 16 § 1.1.3 bound, so there is a day left to
  // act rather than a breach to report.
  @Test
  void aPayoutThatHasReachedTwoWorkingDaysWithoutBeingInitiatedIsReported() {
    var request = stillWaiting(orderedOn(MONDAY));
    givenNotYetInitiated(request);

    checker().checkOverduePayouts(MONDAY.plusDays(2));

    verify(paymentCheckService)
        .record(
            PAYOUT_OVERDUE,
            WARNING,
            request.getId() + ":2026-04-15",
            "ordered 2026-04-13, still VERIFIED, and the payout must be initiated by 2026-04-16");
  }

  // Money owed to a client should not go quiet on its own, so the key carries the day and the
  // finding is reported again every working day it stays unpaid.
  @Test
  void anOverduePayoutIsReportedAgainTheNextDay() {
    var request = stillWaiting(orderedOn(MONDAY));
    givenNotYetInitiated(request);

    checker().checkOverduePayouts(MONDAY.plusDays(2));
    checker().checkOverduePayouts(MONDAY.plusDays(3));

    verify(paymentCheckService)
        .record(any(), any(), eq(request.getId() + ":2026-04-15"), anyString());
    verify(paymentCheckService)
        .record(any(), any(), eq(request.getId() + ":2026-04-16"), anyString());
  }

  // A request whose payout was initiated, cancelled or already paid is nobody's problem. Whether
  // the bank then executed an initiated payout is the reconciler's question, not this one.
  @Test
  void onlyRequestsWhosePayoutWasNeverInitiatedAreLookedAt() {
    givenNotYetInitiated();

    checker().checkOverduePayouts(MONDAY.plusDays(2));

    verify(redemptionRequestRepository)
        .findByStatusIn(RedemptionPayoutAgeChecker.NOT_YET_INITIATED);
    assertThat(RedemptionPayoutAgeChecker.NOT_YET_INITIATED)
        .doesNotContain(REDEEMED, PROCESSED, CANCELLED);
  }

  // The order is given when the client places it. Processing anchors on COALESCE(reviewedAt,
  // requestedAt), so a review restarts that clock -- the sisekord clock does not, and a request
  // sitting in review is exactly what this is meant to surface. Reviewed a day late, the bound
  // would still be a day away if it followed the review.
  @Test
  void aReviewDoesNotRestartTheClientsClock() {
    var request =
        redemptionRequestFixture()
            .id(UUID.randomUUID())
            .status(VERIFIED)
            .requestedAt(orderedOn(MONDAY))
            .reviewedAt(orderedOn(MONDAY.plusDays(1)))
            .build();
    givenNotYetInitiated(request);

    checker().checkOverduePayouts(MONDAY.plusDays(2));

    verify(paymentCheckService).record(any(), any(), anyString(), anyString());
  }

  private void givenNotYetInitiated(RedemptionRequest... requests) {
    given(redemptionRequestRepository.findByStatusIn(RedemptionPayoutAgeChecker.NOT_YET_INITIATED))
        .willReturn(List.of(requests));
  }

  private static RedemptionRequest stillWaiting(Instant requestedAt) {
    return redemptionRequestFixture()
        .id(UUID.randomUUID())
        .status(VERIFIED)
        .requestedAt(requestedAt)
        .build();
  }

  private static Instant orderedOn(LocalDate date) {
    return ZonedDateTime.of(date, LocalTime.of(10, 0), TALLINN).toInstant();
  }
}
