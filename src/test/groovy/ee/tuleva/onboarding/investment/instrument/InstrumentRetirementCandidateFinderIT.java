package ee.tuleva.onboarding.investment.instrument;

import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.instrument;
import static ee.tuleva.onboarding.instrument.InstrumentReferenceServiceFixture.instrumentReferenceService;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.instrument.BenchmarkCategoryProxy;
import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.investment.instrument.InstrumentRetirementCandidateFinder.RetirementCandidate;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class InstrumentRetirementCandidateFinderIT {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);
  private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(UTC).toInstant(), UTC);

  private static final List<LocalDate> NAV_DATES =
      List.of(
          LocalDate.of(2026, 9, 16),
          LocalDate.of(2026, 9, 17),
          LocalDate.of(2026, 9, 18),
          LocalDate.of(2026, 9, 21),
          LocalDate.of(2026, 9, 22),
          LocalDate.of(2026, 9, 23));
  private static final LocalDate LAST_HELD_ON = NAV_DATES.getFirst();

  private static final LocalDate MODEL_THAT_HELD_IT = LocalDate.of(2026, 6, 17);
  private static final LocalDate LIVE_MODEL = LocalDate.of(2026, 8, 19);
  private static final LocalDate MODEL_THAT_KEPT_IT_AT_ZERO_WEIGHT = LocalDate.of(2026, 3, 2);
  private static final LocalDate LONG_SINCE_SOLD_ON = LocalDate.of(2026, 2, 27);

  private static final String EXITED_ISIN = "IE0009FT4LX4";
  private static final String STILL_HELD_ISIN = "IE00BFG1TM61";
  private static final String BENCHMARK_PROXY_ISIN = "IE00B4L5Y983";
  private static final String AWAITING_FIRST_MODEL_ISIN = "IE000QWCYQT0";
  private static final String FUND_BENCHMARK_ISIN = "LU0826455353";

  private static final BigDecimal SOME_UNITS = new BigDecimal("1000.00000");

  @Autowired private ModelPortfolioAllocationRepository allocationRepository;
  @Autowired private FundPositionRepository fundPositionRepository;

  @Test
  void retiresAnInstrumentOffTheBooksForFiveNavDates() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    heldUntil(EXITED_ISIN, LAST_HELD_ON);
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates())
        .containsExactly(new RetirementCandidate(EXITED_ISIN, EXITED_ISIN, LAST_HELD_ON, 5));
  }

  @Test
  void waitsWhileFewerThanFiveNavDatesHavePassedSinceItLeftTheBooks() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    heldUntil(EXITED_ISIN, NAV_DATES.get(1));
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates()).isEmpty();
  }

  @Test
  void countsFromTheLastNavDateItWasHeldRatherThanFromTheModelItLeft() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    heldUntil(EXITED_ISIN, NAV_DATES.get(2));
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates()).isEmpty();
  }

  @Test
  void doesNotRetireOnTheDayALiveModelDropsAnInstrumentSoldLongAgo() {
    modelPortfolio(MODEL_THAT_KEPT_IT_AT_ZERO_WEIGHT, STILL_HELD_ISIN);
    allocation(TUK75, MODEL_THAT_KEPT_IT_AT_ZERO_WEIGHT, EXITED_ISIN, BigDecimal.ZERO);
    modelPortfolio(TODAY, STILL_HELD_ISIN);
    position(EXITED_ISIN, SOME_UNITS, LONG_SINCE_SOLD_ON);
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates()).isEmpty();
  }

  @Test
  void waitsForFiveNavDatesAfterTheModelDroppedItNotCountingTheDayOfTheDrop() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(NAV_DATES.get(1), STILL_HELD_ISIN);
    position(EXITED_ISIN, SOME_UNITS, LONG_SINCE_SOLD_ON);
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates()).isEmpty();
  }

  @Test
  void retiresOnceFiveNavDatesHavePassedAfterTheModelDroppedIt() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(NAV_DATES.getFirst(), STILL_HELD_ISIN);
    position(EXITED_ISIN, SOME_UNITS, LONG_SINCE_SOLD_ON);
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates())
        .containsExactly(
            new RetirementCandidate(EXITED_ISIN, EXITED_ISIN, NAV_DATES.getFirst(), 5));
  }

  @Test
  void startsTheClockWhenTheLastFundsModelDropsIt() {
    modelPortfolio(TUK75, MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(TUK75, LIVE_MODEL, STILL_HELD_ISIN);
    modelPortfolio(TUV100, MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(TUV100, NAV_DATES.get(2), STILL_HELD_ISIN);
    position(EXITED_ISIN, SOME_UNITS, LONG_SINCE_SOLD_ON);
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates()).isEmpty();
  }

  @Test
  void treatsARemainingZeroQuantityRowAsOffTheBooks() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    heldUntil(EXITED_ISIN, LAST_HELD_ON);
    NAV_DATES.forEach(navDate -> position(EXITED_ISIN, BigDecimal.ZERO, navDate));
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates())
        .containsExactly(new RetirementCandidate(EXITED_ISIN, EXITED_ISIN, LAST_HELD_ON, 5));
  }

  @Test
  void keepsAnInstrumentReportedWithoutAQuantityAtTheLatestNavDateAsHeld() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    heldUntil(EXITED_ISIN, LAST_HELD_ON);
    NAV_DATES.stream().skip(1).forEach(navDate -> position(EXITED_ISIN, null, navDate));
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates()).isEmpty();
  }

  @Test
  void startsTheClockAtTheLastNavDateItWasReportedWithoutAQuantity() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    heldUntil(EXITED_ISIN, LAST_HELD_ON);
    position(EXITED_ISIN, null, NAV_DATES.get(1));
    NAV_DATES.stream().skip(2).forEach(navDate -> position(EXITED_ISIN, BigDecimal.ZERO, navDate));
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates()).isEmpty();
  }

  @Test
  void letsAFundThatReportedItOnlyWithoutAQuantityGateTheClock() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    heldUntil(TUK75, EXITED_ISIN, LAST_HELD_ON);
    heldThroughout(TUK75, STILL_HELD_ISIN);
    position(TUV100, EXITED_ISIN, null, LAST_HELD_ON);
    position(TUV100, STILL_HELD_ISIN, SOME_UNITS, LAST_HELD_ON);
    position(TUV100, STILL_HELD_ISIN, SOME_UNITS, NAV_DATES.get(1));

    assertThat(finder().findCandidates()).isEmpty();
  }

  @Test
  void ignoresAnInstrumentStillHeldAtTheLatestNavDate() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    heldThroughout(EXITED_ISIN);
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates()).isEmpty();
  }

  @Test
  void ignoresAnInstrumentTheLiveModelPortfolioStillNames() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, EXITED_ISIN, STILL_HELD_ISIN);
    heldUntil(EXITED_ISIN, LAST_HELD_ON);
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates()).isEmpty();
  }

  @Test
  void ignoresAnInstrumentAnUpcomingModelPortfolioBringsBack() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    modelPortfolio(TODAY.plusMonths(1), EXITED_ISIN, STILL_HELD_ISIN);
    heldUntil(EXITED_ISIN, LAST_HELD_ON);
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates()).isEmpty();
  }

  @Test
  void neverRetiresABenchmarkProxyEvenOnceAModelDroppedItAndItWasSold() {
    modelPortfolio(MODEL_THAT_HELD_IT, BENCHMARK_PROXY_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    heldUntil(BENCHMARK_PROXY_ISIN, LAST_HELD_ON);
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates()).isEmpty();
  }

  @Test
  void ignoresAnInstrumentAddedAheadOfItsFirstModelPortfolio() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    heldUntil(EXITED_ISIN, LAST_HELD_ON);
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates())
        .containsExactly(new RetirementCandidate(EXITED_ISIN, EXITED_ISIN, LAST_HELD_ON, 5));
  }

  @Test
  void countsFromTheModelDropForAnInstrumentNoFundEverReported() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates())
        .containsExactly(new RetirementCandidate(EXITED_ISIN, EXITED_ISIN, LIVE_MODEL, 6));
  }

  @Test
  void keepsAnInstrumentALiveModelStillCarriesAtZeroWeight() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    allocation(TUK75, LIVE_MODEL, EXITED_ISIN, BigDecimal.ZERO);
    heldUntil(EXITED_ISIN, LAST_HELD_ON);
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates()).isEmpty();
  }

  @Test
  void neverRetiresAnInstrumentTheFundLevelBenchmarkIsPricedFrom() {
    modelPortfolio(MODEL_THAT_HELD_IT, FUND_BENCHMARK_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    heldUntil(FUND_BENCHMARK_ISIN, LAST_HELD_ON);
    heldThroughout(STILL_HELD_ISIN);

    assertThat(finder().findCandidates()).isEmpty();
  }

  @Test
  void doesNotLetOtherFundsNavDatesRunDownTheClockForAFundThatHeldIt() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    heldUntil(TUK75, EXITED_ISIN, LAST_HELD_ON);
    heldThroughout(TUK75, STILL_HELD_ISIN);
    heldUntil(TUV100, EXITED_ISIN, LAST_HELD_ON);
    position(TUV100, STILL_HELD_ISIN, SOME_UNITS, NAV_DATES.get(1));

    assertThat(finder().findCandidates()).isEmpty();
  }

  @Test
  void doesNotTreatASecuritiesLessReportAsAnObservationThatTheInstrumentIsGone() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    heldUntil(EXITED_ISIN, LAST_HELD_ON);
    heldUntil(STILL_HELD_ISIN, LAST_HELD_ON);
    NAV_DATES.stream().skip(1).forEach(this::cashOnly);

    assertThat(finder().findCandidates()).isEmpty();
  }

  @Test
  void startsTheClockWhenTheLastFundSellsOutRatherThanTheFirst() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    heldUntil(TUK75, EXITED_ISIN, LAST_HELD_ON);
    heldUntil(TUV100, EXITED_ISIN, NAV_DATES.get(4));
    heldThroughout(TUK75, STILL_HELD_ISIN);
    heldThroughout(TUV100, STILL_HELD_ISIN);

    assertThat(finder().findCandidates()).isEmpty();
  }

  @Test
  void retiresOnlyOnceEveryFundHasBeenWithoutItForFiveNavDates() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    heldUntil(TUK75, EXITED_ISIN, NAV_DATES.get(0));
    heldUntil(TUV100, EXITED_ISIN, NAV_DATES.get(0));
    heldThroughout(TUK75, STILL_HELD_ISIN);
    heldThroughout(TUV100, STILL_HELD_ISIN);

    assertThat(finder().findCandidates())
        .containsExactly(new RetirementCandidate(EXITED_ISIN, EXITED_ISIN, LAST_HELD_ON, 5));
  }

  @Test
  void isNotHeldUpByAFundThatNeverHeldItAndHasStoppedReportingSecurities() {
    modelPortfolio(MODEL_THAT_HELD_IT, EXITED_ISIN, STILL_HELD_ISIN);
    modelPortfolio(LIVE_MODEL, STILL_HELD_ISIN);
    heldUntil(TUK75, EXITED_ISIN, LAST_HELD_ON);
    heldThroughout(TUK75, STILL_HELD_ISIN);
    position(TUV100, STILL_HELD_ISIN, SOME_UNITS, LAST_HELD_ON);

    assertThat(finder().findCandidates())
        .containsExactly(new RetirementCandidate(EXITED_ISIN, EXITED_ISIN, LAST_HELD_ON, 5));
  }

  private InstrumentRetirementCandidateFinder finder() {
    return new InstrumentRetirementCandidateFinder(
        instrumentReferenceService(activeInstruments(), benchmarkProxies()),
        allocationRepository,
        fundPositionRepository,
        () -> Set.of(FUND_BENCHMARK_ISIN),
        CLOCK);
  }

  private static List<InstrumentReference> activeInstruments() {
    return List.of(
        instrument(EXITED_ISIN).build(),
        instrument(STILL_HELD_ISIN).build(),
        instrument(BENCHMARK_PROXY_ISIN).build(),
        instrument(AWAITING_FIRST_MODEL_ISIN).build(),
        instrument(FUND_BENCHMARK_ISIN).build());
  }

  private static List<BenchmarkCategoryProxy> benchmarkProxies() {
    return List.of(
        new BenchmarkCategoryProxy(1L, "EQUITY_DM", BENCHMARK_PROXY_ISIN, null, "MSCI_WORLD"));
  }

  private void modelPortfolio(LocalDate effectiveDate, String... isins) {
    modelPortfolio(TUK75, effectiveDate, isins);
  }

  private void modelPortfolio(TulevaFund fund, LocalDate effectiveDate, String... isins) {
    for (var isin : isins) {
      allocation(fund, effectiveDate, isin, BigDecimal.ONE);
    }
  }

  private void allocation(
      TulevaFund fund, LocalDate effectiveDate, String isin, BigDecimal weight) {
    allocationRepository.save(
        ModelPortfolioAllocation.builder()
            .fund(fund)
            .effectiveDate(effectiveDate)
            .isin(isin)
            .weight(weight)
            .build());
  }

  private void cashOnly(LocalDate navDate) {
    fundPositionRepository.save(
        FundPosition.builder()
            .fund(TUK75)
            .navDate(navDate)
            .accountType(CASH)
            .accountName("cash")
            .accountId("EE3600109435")
            .quantity(SOME_UNITS)
            .build());
  }

  private void heldThroughout(String isin) {
    heldThroughout(TUK75, isin);
  }

  private void heldThroughout(TulevaFund fund, String isin) {
    NAV_DATES.forEach(navDate -> position(fund, isin, SOME_UNITS, navDate));
  }

  private void heldUntil(String isin, LocalDate lastHeldOn) {
    heldUntil(TUK75, isin, lastHeldOn);
  }

  private void heldUntil(TulevaFund fund, String isin, LocalDate lastHeldOn) {
    NAV_DATES.stream()
        .filter(navDate -> !navDate.isAfter(lastHeldOn))
        .forEach(navDate -> position(fund, isin, SOME_UNITS, navDate));
  }

  private void position(String isin, BigDecimal quantity, LocalDate navDate) {
    position(TUK75, isin, quantity, navDate);
  }

  private void position(TulevaFund fund, String isin, BigDecimal quantity, LocalDate navDate) {
    fundPositionRepository.save(
        FundPosition.builder()
            .fund(fund)
            .navDate(navDate)
            .accountType(SECURITY)
            .accountName(isin + " " + quantity)
            .accountId(isin)
            .quantity(quantity)
            .build());
  }
}
