package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.TrackingCheckType;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({StaleFundReturnDetector.class, FundNavQueryService.class, PublicHolidays.class})
class StaleFundReturnDetectorIT {

  private static final LocalDate WEDNESDAY = LocalDate.of(2026, 4, 8);
  private static final LocalDate THURSDAY = LocalDate.of(2026, 4, 9);
  private static final LocalDate FRIDAY = LocalDate.of(2026, 4, 10);
  private static final LocalDate MONDAY = LocalDate.of(2026, 4, 13);
  private static final LocalDate PERIOD_START = LocalDate.of(2026, 4, 1);
  private static final LocalDate PERIOD_END = LocalDate.of(2026, 4, 30);
  private static final BigDecimal FRIDAY_NAV_AS_CHECKED = new BigDecimal("10.2010");
  private static final BigDecimal FRIDAY_NAV_AS_CORRECTED = new BigDecimal("10.2500");

  @Autowired StaleFundReturnDetector detector;
  @Autowired TrackingDifferenceEventRepository eventRepository;
  @Autowired NavReportRepository navReportRepository;
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void storeTheNavSeriesAndTheChecksComputedFromIt() {
    storeNav(WEDNESDAY, new BigDecimal("10.0000"));
    storeNav(THURSDAY, new BigDecimal("10.1000"));
    storeNav(FRIDAY, FRIDAY_NAV_AS_CHECKED);
    storeNav(MONDAY, new BigDecimal("10.3000"));

    storeEvent(THURSDAY, MODEL_PORTFOLIO, new BigDecimal("0.020000"));
    storeEvent(THURSDAY, MODEL_PORTFOLIO, new BigDecimal("0.010000"));
    storeEvent(FRIDAY, MODEL_PORTFOLIO, new BigDecimal("0.010000"));
    storeEvent(MONDAY, MODEL_PORTFOLIO, new BigDecimal("0.009705"));
    storeEvent(THURSDAY, BENCHMARK_MODEL, new BigDecimal("0.004200"));
  }

  @Test
  void nothingIsStaleWhileEveryStoredFundReturnMatchesTheNavItWasComputedFrom() {
    assertThat(detector.staleCheckDates(TUK75, PERIOD_START, PERIOD_END)).isEmpty();
  }

  @Test
  void correctingANavInPlaceMakesItsDateAndTheNextWorkingDayStaleAndLeavesTheRestAlone() {
    correctNavInPlace(FRIDAY, FRIDAY_NAV_AS_CORRECTED);

    assertThat(detector.staleCheckDates(TUK75, PERIOD_START, PERIOD_END))
        .containsExactly(FRIDAY, MONDAY);
  }

  @Test
  void aRecheckThatStoresTheCorrectedReturnClearsTheStaleDate() {
    correctNavInPlace(FRIDAY, FRIDAY_NAV_AS_CORRECTED);

    storeEvent(FRIDAY, MODEL_PORTFOLIO, new BigDecimal("0.014851"));
    storeEvent(MONDAY, MODEL_PORTFOLIO, new BigDecimal("0.004878"));

    assertThat(detector.staleCheckDates(TUK75, PERIOD_START, PERIOD_END)).isEmpty();
    assertThat(eventRepository.findAll())
        .filteredOn(event -> event.getCheckType() == MODEL_PORTFOLIO)
        .hasSize(6);
  }

  @Test
  void aStaleDateOutsideTheRequestedWindowIsNotReported() {
    correctNavInPlace(FRIDAY, FRIDAY_NAV_AS_CORRECTED);

    assertThat(detector.staleCheckDates(TUK75, MONDAY, PERIOD_END)).containsExactly(MONDAY);
  }

  private void storeNav(LocalDate navDate, BigDecimal navPerUnit) {
    navReportRepository.save(
        NavReportRow.builder()
            .navDate(navDate)
            .fundCode(TUK75.getCode())
            .accountType("NAV")
            .accountName("Net asset value per unit")
            .marketPrice(navPerUnit)
            .calculationId(UUID.randomUUID())
            .build());
  }

  private void correctNavInPlace(LocalDate navDate, BigDecimal correctedNavPerUnit) {
    jdbcClient
        .sql(
            """
            UPDATE nav_report SET market_price = :corrected
            WHERE fund_code = :fundCode AND nav_date = :navDate AND account_type = 'NAV'
            """)
        .param("corrected", correctedNavPerUnit)
        .param("fundCode", TUK75.getCode())
        .param("navDate", navDate)
        .update();
  }

  private void storeEvent(LocalDate checkDate, TrackingCheckType checkType, BigDecimal fundReturn) {
    eventRepository.save(
        TrackingDifferenceEvent.builder()
            .fund(TUK75)
            .checkDate(checkDate)
            .checkType(checkType)
            .trackingDifference(new BigDecimal("0.000100"))
            .fundReturn(fundReturn)
            .benchmarkReturn(fundReturn.subtract(new BigDecimal("0.000100")))
            .build());
  }
}
