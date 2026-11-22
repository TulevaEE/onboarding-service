package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationBasis.MONTHLY_ESTIMATE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.investment.fees.ocf.KidFigure;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationService;
import ee.tuleva.onboarding.investment.series.SeriesSegment;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class KidCalculationServiceTest {

  private static final Instant NOW = Instant.parse("2026-03-10T09:00:00Z");
  private static final YearMonth JANUARY = YearMonth.of(2026, 1);
  private static final YearMonth FEBRUARY = YearMonth.of(2026, 2);
  private static final BigDecimal ONGOING_CHARGES = new BigDecimal("0.00500000");

  private final KidFiguresService kidFiguresService = Mockito.mock(KidFiguresService.class);
  private final OcfPublicationService ocfPublications = Mockito.mock(OcfPublicationService.class);
  private final KidCalculationRepository repository = Mockito.mock(KidCalculationRepository.class);
  private final KidCalculationService service =
      new KidCalculationService(
          KidFunds.TULEVA_FUNDS,
          kidFiguresService,
          ocfPublications,
          repository,
          Clock.fixed(NOW, ZoneId.of("Europe/Tallinn")));

  @Test
  void anUnexpectedFailureForAFundIsLoggedAndDoesNotEndTheScheduledRun() {
    given(kidFiguresService.latestCloseDate(TKF100))
        .willThrow(new IllegalStateException("database unavailable"));

    assertThat(service.calculateTheMissingMonths())
        .containsExactly(
            KidFundRun.failed(TKF100, new IllegalStateException("database unavailable")));
    verify(repository, never()).save(any(), any());
  }

  @Test
  void anUnexpectedFailureInOneMonthIsReportedInTheRunAndTheLaterMonthsStillRun() {
    var kid = aKid();
    var february = figuresEndingWith(LocalDate.of(2026, 2, 28));
    var storedFebruary = stored(7, FEBRUARY, kid);
    givenBothMonthsHaveTheirPricesAndOcf(kid);
    given(kidFiguresService.figures(TKF100, JANUARY, ONGOING_CHARGES))
        .willThrow(new NumberFormatException("Infinite or NaN"));
    given(kidFiguresService.figures(TKF100, FEBRUARY, ONGOING_CHARGES)).willReturn(february);
    given(repository.save(any(), any())).willReturn(storedFebruary);

    assertThat(service.calculateTheMissingMonths())
        .containsExactly(
            KidFundRun.ran(
                TKF100,
                List.of(storedFebruary),
                List.of(new KidFailedMonth(JANUARY, "NumberFormatException"))));
    verify(repository)
        .save(KidCalculation.complete(TKF100, FEBRUARY, OcfBasis.given(kid), february), NOW);
    verify(repository, never())
        .save(argThat(calculation -> calculation.month().equals(JANUARY)), any());
  }

  @Test
  void aMonthThatFailedUnexpectedlyIsNoLongerReportedOnceTheNextRunCalculatesIt() {
    var kid = aKid();
    var january = figuresEndingWith(LocalDate.of(2026, 1, 31));
    var february = figuresEndingWith(LocalDate.of(2026, 2, 28));
    var storedJanuary = stored(8, JANUARY, kid);
    var storedFebruary = stored(7, FEBRUARY, kid);
    givenBothMonthsHaveTheirPricesAndOcf(kid);
    given(kidFiguresService.figures(TKF100, JANUARY, ONGOING_CHARGES))
        .willThrow(new NumberFormatException("Infinite or NaN"))
        .willReturn(january);
    given(kidFiguresService.figures(TKF100, FEBRUARY, ONGOING_CHARGES)).willReturn(february);
    given(repository.save(any(), any()))
        .willAnswer(
            invocation ->
                invocation.<KidCalculation>getArgument(0).month().equals(JANUARY)
                    ? storedJanuary
                    : storedFebruary);
    service.calculateTheMissingMonths();
    given(repository.findMonthsPublishedOrCompleteInTheirLatestVersion(eq(TKF100), any(), any()))
        .willReturn(Set.of(FEBRUARY));

    assertThat(service.calculateTheMissingMonths())
        .containsExactly(KidFundRun.ran(TKF100, List.of(storedJanuary)));
  }

  private void givenBothMonthsHaveTheirPricesAndOcf(KidFigure kid) {
    given(kidFiguresService.latestCloseDate(TKF100))
        .willReturn(Optional.of(LocalDate.of(2026, 3, 9)));
    given(ocfPublications.figureInForceOn(eq(TKF100), any())).willReturn(Optional.of(kid));
  }

  private static KidFigure aKid() {
    return new KidFigure(
        3, LocalDate.of(2026, 1, 12), ONGOING_CHARGES, 2, MONTHLY_ESTIMATE, "KID 2026");
  }

  private static StoredKidCalculation stored(long id, YearMonth month, KidFigure kid) {
    return new StoredKidCalculation(
        id,
        TKF100,
        month,
        1,
        List.of(),
        "MSCI_ACWI",
        month.atEndOfMonth(),
        OcfBasis.given(kid),
        TransactionCostSource.PUBLISHED_OCF,
        NOW);
  }

  private static KidFigures figuresEndingWith(LocalDate periodEnd) {
    return new KidFigures(
        TKF100,
        List.of(new SeriesSegment("MSCI_ACWI", null)),
        periodEnd.minusYears(10),
        periodEnd,
        periodEnd,
        ONGOING_CHARGES,
        5,
        new BigDecimal("10000"),
        List.of(),
        List.of());
  }
}
