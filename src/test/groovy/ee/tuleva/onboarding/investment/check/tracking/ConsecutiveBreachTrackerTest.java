package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsecutiveBreachTrackerTest {

  // A Friday, so the three working days before it are the three calendar days before it.
  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 4, 10);
  private static final LocalDate THURSDAY = LocalDate.of(2026, 4, 9);
  private static final LocalDate WEDNESDAY = LocalDate.of(2026, 4, 8);
  private static final LocalDate TUESDAY = LocalDate.of(2026, 4, 7);

  @Mock private TrackingDifferenceEventRepository eventRepository;
  @Mock private TrackingDifferenceCalculator calculator;

  private ConsecutiveBreachTracker tracker;

  @BeforeEach
  void setUp() {
    tracker = new ConsecutiveBreachTracker(eventRepository, calculator, new PublicHolidays());
  }

  @Test
  void streakStopsAtAWorkingDayThatWasNeverChecked() {
    given(calculator.escalationLookbackDays(CHECK_DATE)).willReturn(10);
    given(eventRepository.findMostRecentEvents(TUK75, MODEL_PORTFOLIO, CHECK_DATE, 10))
        .willReturn(List.of(breachEvent(THURSDAY), breachEvent(TUESDAY)));

    var info = tracker.countConsecutiveBreaches(TUK75, MODEL_PORTFOLIO, CHECK_DATE);

    // Wednesday was a working day with no check, so nothing says the breach persisted through it.
    // Counting two here would join two separate breaches across the hole and escalate on a streak
    // that was never observed.
    assertThat(info.count()).isEqualTo(1);
    assertThat(info.truncated()).isFalse();
    assertThat(info.unavailable()).isFalse();
  }

  @Test
  void streakRunsThroughContiguousWorkingDays() {
    given(calculator.escalationLookbackDays(CHECK_DATE)).willReturn(10);
    given(eventRepository.findMostRecentEvents(TUK75, MODEL_PORTFOLIO, CHECK_DATE, 10))
        .willReturn(List.of(breachEvent(THURSDAY), breachEvent(WEDNESDAY), breachEvent(TUESDAY)));

    var info = tracker.countConsecutiveBreaches(TUK75, MODEL_PORTFOLIO, CHECK_DATE);

    assertThat(info.count()).isEqualTo(3);
    assertThat(info.truncated()).isFalse();
  }

  @Test
  void aStreakThatFillsTheWholeLookbackWindowIsReportedAsALowerBound() {
    given(calculator.escalationLookbackDays(CHECK_DATE)).willReturn(2);
    given(eventRepository.findMostRecentEvents(TUK75, MODEL_PORTFOLIO, CHECK_DATE, 2))
        .willReturn(List.of(breachEvent(THURSDAY), breachEvent(WEDNESDAY)));

    var info = tracker.countConsecutiveBreaches(TUK75, MODEL_PORTFOLIO, CHECK_DATE);

    // The window bounds the query, not the breach: every row fetched was a breach, so the streak
    // may run further back than the window can see.
    assertThat(info.count()).isEqualTo(2);
    assertThat(info.truncated()).isTrue();
  }

  @Test
  void aStreakThatEndsInsideTheWindowIsNotALowerBound() {
    given(calculator.escalationLookbackDays(CHECK_DATE)).willReturn(2);
    given(eventRepository.findMostRecentEvents(TUK75, MODEL_PORTFOLIO, CHECK_DATE, 2))
        .willReturn(List.of(breachEvent(THURSDAY), nonBreachEvent(WEDNESDAY)));

    var info = tracker.countConsecutiveBreaches(TUK75, MODEL_PORTFOLIO, CHECK_DATE);

    assertThat(info.count()).isEqualTo(1);
    assertThat(info.truncated()).isFalse();
  }

  @Test
  void aStreakThatCouldNotBeCountedIsNotReportedAsNoStreak() {
    given(calculator.escalationLookbackDays(CHECK_DATE)).willReturn(10);
    willThrow(new RuntimeException("database unavailable"))
        .given(eventRepository)
        .findMostRecentEvents(any(), any(), any(), eq(10));

    var info = tracker.countConsecutiveBreaches(TUK75, MODEL_PORTFOLIO, CHECK_DATE);

    // "No streak" and "we could not work out the streak" are different facts, and reporting the
    // second as the first suppresses the escalation with nobody told.
    assertThat(info.count()).isZero();
    assertThat(info.unavailable()).isTrue();
    assertThat(info.truncated()).isFalse();
  }

  @Test
  void aFailedCountIsCarriedOntoTheResultEvenOnADayThatDidNotBreach() {
    var result =
        tracker.updateConsecutiveCount(
            nonBreachingResult(),
            new ConsecutiveBreachTracker.ConsecutiveBreachInfo(
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                java.util.Map.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                false,
                true));

    assertThat(result.escalationCountUnavailable()).isTrue();
    assertThat(result.consecutiveBreachDays()).isZero();
  }

  private TrackingDifferenceResult nonBreachingResult() {
    return TrackingDifferenceResult.builder()
        .fund(TUK75)
        .checkType(MODEL_PORTFOLIO)
        .checkDate(CHECK_DATE)
        .fundReturn(new BigDecimal("0.001"))
        .benchmarkReturn(new BigDecimal("0.001"))
        .trackingDifference(BigDecimal.ZERO)
        .breach(false)
        .build();
  }

  private TrackingDifferenceEvent breachEvent(LocalDate date) {
    return TrackingDifferenceEvent.builder()
        .fund(TUK75)
        .checkDate(date)
        .checkType(MODEL_PORTFOLIO)
        .trackingDifference(new BigDecimal("0.0020"))
        .fundReturn(new BigDecimal("0.01"))
        .benchmarkReturn(new BigDecimal("0.008"))
        .breach(true)
        .consecutiveBreachDays(1)
        .build();
  }

  private TrackingDifferenceEvent nonBreachEvent(LocalDate date) {
    return TrackingDifferenceEvent.builder()
        .fund(TUK75)
        .checkDate(date)
        .checkType(MODEL_PORTFOLIO)
        .trackingDifference(BigDecimal.ZERO)
        .fundReturn(new BigDecimal("0.001"))
        .benchmarkReturn(new BigDecimal("0.001"))
        .breach(false)
        .consecutiveBreachDays(0)
        .build();
  }
}
