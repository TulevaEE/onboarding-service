package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaleFundReturnDetectorTest {

  private static final LocalDate FROM = LocalDate.of(2026, 4, 1);
  private static final LocalDate TO = LocalDate.of(2026, 4, 30);
  private static final LocalDate THURSDAY = LocalDate.of(2026, 4, 9);
  private static final LocalDate FRIDAY = LocalDate.of(2026, 4, 10);
  private static final LocalDate MONDAY = LocalDate.of(2026, 4, 13);
  private static final BigDecimal NAV_WHEN_CHECKED = new BigDecimal("10.10");
  private static final BigDecimal PREVIOUS_NAV = new BigDecimal("10.00");
  private static final BigDecimal CORRECTED_NAV = new BigDecimal("10.12");
  private static final BigDecimal RETURN_WHEN_CHECKED = new BigDecimal("0.010000");

  @Mock TrackingDifferenceEventRepository eventRepository;
  @Mock FundNavQueryService fundNavQueryService;

  private StaleFundReturnDetector detector;

  @BeforeEach
  void setUp() {
    detector =
        new StaleFundReturnDetector(eventRepository, fundNavQueryService, new PublicHolidays());
  }

  @Test
  void anEventWhoseFundReturnTheCurrentNavStillGivesIsNotStale() {
    givenStoredEvents(event(FRIDAY, RETURN_WHEN_CHECKED));
    givenNav(FRIDAY, NAV_WHEN_CHECKED);
    givenNav(THURSDAY, PREVIOUS_NAV);

    assertThat(detector.staleCheckDates(TUK75, FROM, TO)).isEmpty();
  }

  @Test
  void anEventIsStaleOnceTheNavOfItsCheckDateWasCorrectedAfterTheCheckRan() {
    givenStoredEvents(event(FRIDAY, RETURN_WHEN_CHECKED));
    givenNav(FRIDAY, CORRECTED_NAV);
    givenNav(THURSDAY, PREVIOUS_NAV);

    assertThat(detector.staleCheckDates(TUK75, FROM, TO)).containsExactly(FRIDAY);
  }

  @Test
  void anEventIsStaleOnceTheNavOfThePreviousWorkingDayWasCorrectedAfterTheCheckRan() {
    givenStoredEvents(event(MONDAY, RETURN_WHEN_CHECKED));
    givenNav(MONDAY, NAV_WHEN_CHECKED);
    givenNav(FRIDAY, new BigDecimal("9.99"));

    assertThat(detector.staleCheckDates(TUK75, FROM, TO)).containsExactly(MONDAY);
  }

  @Test
  void aDifferenceInTheLastDecimalOfTheReturnIsStaleBecauseBothSidesComeFromTheSameFormula() {
    givenStoredEvents(event(FRIDAY, new BigDecimal("0.010001")));
    givenNav(FRIDAY, NAV_WHEN_CHECKED);
    givenNav(THURSDAY, PREVIOUS_NAV);

    assertThat(detector.staleCheckDates(TUK75, FROM, TO)).containsExactly(FRIDAY);
  }

  @Test
  void aStoredReturnThatDiffersOnlyInTrailingZerosIsNotStale() {
    givenStoredEvents(event(FRIDAY, new BigDecimal("0.01")));
    givenNav(FRIDAY, NAV_WHEN_CHECKED);
    givenNav(THURSDAY, PREVIOUS_NAV);

    assertThat(detector.staleCheckDates(TUK75, FROM, TO)).isEmpty();
  }

  @Test
  void onlyTheStaleDatesAreReturnedInCheckDateOrder() {
    givenStoredEvents(
        event(THURSDAY, RETURN_WHEN_CHECKED),
        event(FRIDAY, RETURN_WHEN_CHECKED),
        event(MONDAY, RETURN_WHEN_CHECKED));
    givenNav(LocalDate.of(2026, 4, 8), PREVIOUS_NAV);
    givenNav(THURSDAY, NAV_WHEN_CHECKED);
    givenNav(FRIDAY, CORRECTED_NAV);
    givenNav(MONDAY, CORRECTED_NAV.multiply(new BigDecimal("1.01")));

    assertThat(detector.staleCheckDates(TUK75, FROM, TO)).containsExactly(FRIDAY);
  }

  @Test
  void aCheckDateWhoseNavIsNowMissingIsNotStaleSoItDoesNotRefireEveryDay() {
    givenStoredEvents(event(FRIDAY, RETURN_WHEN_CHECKED));
    given(fundNavQueryService.findLatestNavPerUnit(TUK75.getCode(), FRIDAY))
        .willReturn(Optional.empty());
    givenNav(THURSDAY, PREVIOUS_NAV);

    assertThat(detector.staleCheckDates(TUK75, FROM, TO)).isEmpty();
  }

  @Test
  void aCheckDateWhosePreviousNavIsNowMissingIsNotStale() {
    givenStoredEvents(event(FRIDAY, RETURN_WHEN_CHECKED));
    givenNav(FRIDAY, CORRECTED_NAV);
    given(fundNavQueryService.findLatestNavPerUnit(TUK75.getCode(), THURSDAY))
        .willReturn(Optional.empty());

    assertThat(detector.staleCheckDates(TUK75, FROM, TO)).isEmpty();
  }

  @Test
  void aZeroPreviousNavGivesNoReturnToCompareSoTheEventIsNotStale() {
    givenStoredEvents(event(FRIDAY, RETURN_WHEN_CHECKED));
    givenNav(FRIDAY, CORRECTED_NAV);
    givenNav(THURSDAY, BigDecimal.ZERO);

    assertThat(detector.staleCheckDates(TUK75, FROM, TO)).isEmpty();
  }

  private void givenStoredEvents(TrackingDifferenceEvent... events) {
    given(eventRepository.findDeduplicatedEventsForPeriod(TUK75, MODEL_PORTFOLIO, FROM, TO))
        .willReturn(List.of(events));
  }

  private void givenNav(LocalDate navDate, BigDecimal navPerUnit) {
    given(fundNavQueryService.findLatestNavPerUnit(TUK75.getCode(), navDate))
        .willReturn(Optional.of(navPerUnit));
  }

  private static TrackingDifferenceEvent event(LocalDate checkDate, BigDecimal fundReturn) {
    return TrackingDifferenceEvent.builder()
        .fund(TUK75)
        .checkDate(checkDate)
        .checkType(MODEL_PORTFOLIO)
        .trackingDifference(new BigDecimal("0.000100"))
        .fundReturn(fundReturn)
        .benchmarkReturn(new BigDecimal("0.009900"))
        .build();
  }
}
