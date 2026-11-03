package ee.tuleva.onboarding.fund;

import static ee.tuleva.onboarding.fund.FundFixture.additionalSavingsFund;
import static ee.tuleva.onboarding.fund.FundFixture.lhv2ndPillarFund;
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarStockFund;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class PublishedOngoingChargesFigureTest {

  private static final Clock NOVEMBER_2_MIDDAY = clockAt("2026-11-02T10:00:00Z");
  private static final LocalDate NOVEMBER_2 = LocalDate.parse("2026-11-02");

  @Test
  void aTulevaFundShowsTheFigureItsKidInForceTodayPrintsWithItsDecimals() {
    var figures = figuresOn(Map.of(NOVEMBER_2, kidFigure("0.0046", 2)), TUK75);

    var response =
        new PublishedOngoingChargesFigure(figures, NOVEMBER_2_MIDDAY)
            .applyTo(responseFor(tuleva2ndPillarStockFund()));

    assertThat(response).isEqualTo(responseWithKidFigure(tuleva2ndPillarStockFund(), "0.0046", 2));
  }

  @Test
  void aTwoDecimalKidPrintingAWholeTenthOfAPercentShowsTheShortFigureAndKeepsTwoDecimals() {
    var figures = figuresOn(Map.of(NOVEMBER_2, kidFigure("0.0050", 2)), TKF100);

    var response =
        new PublishedOngoingChargesFigure(figures, NOVEMBER_2_MIDDAY)
            .applyTo(responseFor(additionalSavingsFund()));

    assertThat(response).isEqualTo(responseWithKidFigure(additionalSavingsFund(), "0.005", 2));
  }

  @Test
  void aOneDecimalKidPrintingTheSameFigureShowsOneDecimal() {
    var figures = figuresOn(Map.of(NOVEMBER_2, kidFigure("0.005", 1)), TKF100);

    var response =
        new PublishedOngoingChargesFigure(figures, NOVEMBER_2_MIDDAY)
            .applyTo(responseFor(additionalSavingsFund()));

    assertThat(response).isEqualTo(responseWithKidFigure(additionalSavingsFund(), "0.005", 1));
  }

  @Test
  void theFigureInForceChangesAtMidnightInTallinnNotInUtc() {
    var figures =
        figuresOn(
            Map.of(
                LocalDate.parse("2026-11-01"),
                kidFigure("0.0052", 2),
                NOVEMBER_2,
                kidFigure("0.0046", 2)),
            TUK75);
    var halfPastMidnightInTallinnOnNovember2 = clockAt("2026-11-01T22:30:00Z");

    var response =
        new PublishedOngoingChargesFigure(figures, halfPastMidnightInTallinnOnNovember2)
            .applyTo(responseFor(tuleva2ndPillarStockFund()));

    assertThat(response).isEqualTo(responseWithKidFigure(tuleva2ndPillarStockFund(), "0.0046", 2));
  }

  @Test
  void aTulevaFundWithNoFigureInForceKeepsTheFundTableFigureAndNamesNoDecimals() {
    OngoingChargesFigures noFigureInForce = (fund, date) -> Optional.empty();

    var response =
        new PublishedOngoingChargesFigure(noFigureInForce, NOVEMBER_2_MIDDAY)
            .applyTo(responseFor(tuleva2ndPillarStockFund()));

    assertThat(response).isEqualTo(responseFor(tuleva2ndPillarStockFund()));
  }

  @Test
  void aTulevaFundKeepsTheFundTableFigureWhenTheKidLookupFails() {
    OngoingChargesFigures failingLookup =
        (fund, date) -> {
          throw new DataAccessResourceFailureException("connection dropped");
        };

    var response =
        new PublishedOngoingChargesFigure(failingLookup, NOVEMBER_2_MIDDAY)
            .applyTo(responseFor(tuleva2ndPillarStockFund()));

    assertThat(response).isEqualTo(responseFor(tuleva2ndPillarStockFund()));
  }

  @Test
  void anotherManagersFundKeepsTheFundTableFigureEvenWhenEveryFundHasOne() {
    OngoingChargesFigures aFigureForEveryFund = (fund, date) -> Optional.of(kidFigure("0.0046", 2));

    var response =
        new PublishedOngoingChargesFigure(aFigureForEveryFund, NOVEMBER_2_MIDDAY)
            .applyTo(responseFor(lhv2ndPillarFund()));

    assertThat(response).isEqualTo(responseFor(lhv2ndPillarFund()));
  }

  @Test
  void aTulevaFundsKidFigureInForceTodayComesWithItsDecimals() {
    var figures = figuresOn(Map.of(NOVEMBER_2, kidFigure("0.0050", 2)), TKF100);

    var figure =
        new PublishedOngoingChargesFigure(figures, NOVEMBER_2_MIDDAY)
            .inForceToday(additionalSavingsFund());

    assertThat(figure).contains(kidFigure("0.005", 2));
  }

  @Test
  void aTulevaFundHasNoKidFigureInForceWhenNoneIsPublished() {
    OngoingChargesFigures noFigureInForce = (fund, date) -> Optional.empty();

    var figure =
        new PublishedOngoingChargesFigure(noFigureInForce, NOVEMBER_2_MIDDAY)
            .inForceToday(tuleva2ndPillarStockFund());

    assertThat(figure).isEmpty();
  }

  private static ApiFundResponse responseFor(Fund fund) {
    return new ApiFundResponse(fund, Locale.ENGLISH);
  }

  private static ApiFundResponse responseWithKidFigure(Fund fund, String figure, int decimals) {
    var response = responseFor(fund);
    response.setOngoingChargesFigure(new BigDecimal(figure));
    response.setOngoingChargesPercentDecimals(decimals);
    return response;
  }

  private static PublishedFigure kidFigure(String figure, int decimals) {
    return new PublishedFigure(new BigDecimal(figure), decimals);
  }

  private static OngoingChargesFigures figuresOn(
      Map<LocalDate, PublishedFigure> figuresByDate, TulevaFund forFund) {
    return (fund, date) ->
        fund == forFund ? Optional.ofNullable(figuresByDate.get(date)) : Optional.empty();
  }

  private static Clock clockAt(String instant) {
    return Clock.fixed(Instant.parse(instant), UTC);
  }
}
