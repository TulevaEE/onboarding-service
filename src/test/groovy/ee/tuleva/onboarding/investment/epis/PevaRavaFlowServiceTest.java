package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.PEVA_RAVA_PAYMENT_LIMIT_BUFFER;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.PEVA_RAVA_PAYMENT_LIMIT_ROUNDING_STEP;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.PEVA_RAVA_TRADE_BUFFER_PERCENT;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.PEVA_RAVA_TRADE_ROUNDING_STEP;
import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R21_RAVA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.report.ReportType;
import ee.tuleva.onboarding.savings.fund.nav.FundNavQueryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PevaRavaFlowServiceTest {

  private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 8, 14);
  private static final LocalDate NAV_DATE = LocalDate.of(2026, 8, 13);
  private static final LocalDate LOCK_DATE = LocalDate.of(2026, 7, 31);
  private static final LocalDate EXEC_DATE = LocalDate.of(2026, 9, 1);
  private static final long R17_REPORT_ID = 17L;
  private static final long R21_REPORT_ID = 21L;

  private final EpisReportSummaryRepository summaryRepository =
      mock(EpisReportSummaryRepository.class);
  private final FundNavQueryService fundNavQueryService = mock(FundNavQueryService.class);
  private final InvestmentParameterRepository parameterRepository =
      mock(InvestmentParameterRepository.class);
  private final PevaRavaCycleRepository cycleRepository = mock(PevaRavaCycleRepository.class);
  private final PevaRavaPeriodService periodService = mock(PevaRavaPeriodService.class);
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-08-14T10:00:00Z"), ZoneId.of("Europe/Tallinn"));

  private final PevaRavaFlowService service =
      new PevaRavaFlowService(
          summaryRepository,
          new OwnFundNavProvider(fundNavQueryService),
          parameterRepository,
          cycleRepository,
          periodService,
          clock);

  @Test
  void calculatesFlowsFromUnitsAndLatestNav() {
    givenActiveCycle(R17_REPORT_ID, R21_REPORT_ID);
    givenSummaries(TUK75, Map.of("pikUnits", 10000, "switchingNetUnits", -5000), 20000);
    givenNav(TUK75, "0.80");
    givenParameters(TUK75, "500000");

    Map<TulevaFund, PevaRavaFlows> flows = service.calculateFlows();

    assertThat(flows).containsOnlyKeys(TUK75);
    assertThat(flows.get(TUK75))
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new PevaRavaFlows(
                new BigDecimal("8000"),
                new BigDecimal("-4000"),
                new BigDecimal("16000"),
                new BigDecimal("28000"),
                new BigDecimal("28000"),
                new BigDecimal("530000"),
                new BigDecimal("29000")));
  }

  @Test
  void switchingSurplusReducesLiquidityButNotBelowPik() {
    givenActiveCycle(R17_REPORT_ID, R21_REPORT_ID);
    givenSummaries(TUK00, Map.of("pikUnits", 1000, "switchingNetUnits", 3000), 2000);
    givenNav(TUK00, "0.70");
    givenParameters(TUK00, "200000");

    Map<TulevaFund, PevaRavaFlows> flows = service.calculateFlows();

    assertThat(flows.get(TUK00))
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new PevaRavaFlows(
                new BigDecimal("700"),
                new BigDecimal("2100"),
                new BigDecimal("1400"),
                new BigDecimal("700"),
                new BigDecimal("2100"),
                new BigDecimal("205000"),
                new BigDecimal("1000")));
  }

  @Test
  void missingR21IsTreatedAsZeroRava() {
    givenActiveCycle(R17_REPORT_ID, null);
    given(summaryRepository.findByReportIdAndFund(R17_REPORT_ID, TUK75))
        .willReturn(
            Optional.of(
                summary(
                    R17_PEVA,
                    R17_REPORT_ID,
                    TUK75,
                    Map.of("pikUnits", 10000, "switchingNetUnits", -5000))));
    givenNav(TUK75, "0.80");
    givenParameters(TUK75, "500000");

    Map<TulevaFund, PevaRavaFlows> flows = service.calculateFlows();

    assertThat(flows.get(TUK75))
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new PevaRavaFlows(
                new BigDecimal("8000"),
                new BigDecimal("-4000"),
                new BigDecimal("0"),
                new BigDecimal("12000"),
                new BigDecimal("12000"),
                new BigDecimal("515000"),
                new BigDecimal("13000")));
  }

  @Test
  void usesActiveCycleR21NotNewerCrossCycleSummary() {
    givenActiveCycle(R17_REPORT_ID, R21_REPORT_ID);
    given(summaryRepository.findByReportIdAndFund(R17_REPORT_ID, TUK75))
        .willReturn(
            Optional.of(
                summary(
                    R17_PEVA,
                    R17_REPORT_ID,
                    TUK75,
                    Map.of("pikUnits", 10000, "switchingNetUnits", -5000))));
    given(summaryRepository.findByReportIdAndFund(R21_REPORT_ID, TUK75))
        .willReturn(
            Optional.of(summary(R21_RAVA, R21_REPORT_ID, TUK75, Map.of("ravaUnits", 20000))));
    givenNav(TUK75, "0.80");
    givenParameters(TUK75, "500000");

    Map<TulevaFund, PevaRavaFlows> flows = service.calculateFlows();

    assertThat(flows.get(TUK75).ravaEur()).isEqualByComparingTo("16000");
  }

  @Test
  void omitsFundWhenNoActiveCycleExists() {
    given(periodService.getCurrentPeriod(AS_OF_DATE)).willReturn(Optional.empty());

    assertThat(service.calculateFlows()).isEmpty();
  }

  @Test
  void omitsFundWhenCycleHasNoLinkedReports() {
    givenActiveCycle(null, null);

    assertThat(service.calculateFlows()).isEmpty();
  }

  @Test
  void throwsWhenNavMissing() {
    givenActiveCycle(R17_REPORT_ID, R21_REPORT_ID);
    givenSummaries(TUK75, Map.of("pikUnits", 10000, "switchingNetUnits", 0), 0);

    assertThatThrownBy(service::calculateFlows).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void throwsWhenNavOutOfReasonableRange() {
    givenActiveCycle(R17_REPORT_ID, R21_REPORT_ID);
    givenSummaries(TUK75, Map.of("pikUnits", 10000, "switchingNetUnits", 0), 0);
    givenNav(TUK75, "12.00");

    assertThatThrownBy(service::calculateFlows).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void throwsWhenEurAmountsAreImplausiblyLarge() {
    givenActiveCycle(R17_REPORT_ID, R21_REPORT_ID);
    givenSummaries(TUK75, Map.of("pikUnits", 900000000, "switchingNetUnits", 0), 0);
    givenNav(TUK75, "0.80");

    assertThatThrownBy(service::calculateFlows).isInstanceOf(IllegalStateException.class);
  }

  private void givenActiveCycle(Long r17ReportId, Long r21ReportId) {
    PevaRavaCycle cycle = new PevaRavaCycle(LOCK_DATE, EXEC_DATE);
    given(periodService.getCurrentPeriod(AS_OF_DATE))
        .willReturn(Optional.of(new PevaRavaPeriod(PevaRavaPhase.ACTIVE, cycle, null, null)));
    PevaRavaCycleEntity entity =
        PevaRavaCycleEntity.builder()
            .lockDate(LOCK_DATE)
            .execDate(EXEC_DATE)
            .r17ReportId(r17ReportId)
            .r21ReportId(r21ReportId)
            .build();
    given(cycleRepository.findByExecDate(EXEC_DATE)).willReturn(Optional.of(entity));
  }

  private void givenSummaries(TulevaFund fund, Map<String, Object> r17Data, int ravaUnits) {
    given(summaryRepository.findByReportIdAndFund(R17_REPORT_ID, fund))
        .willReturn(Optional.of(summary(R17_PEVA, R17_REPORT_ID, fund, r17Data)));
    given(summaryRepository.findByReportIdAndFund(R21_REPORT_ID, fund))
        .willReturn(
            Optional.of(summary(R21_RAVA, R21_REPORT_ID, fund, Map.of("ravaUnits", ravaUnits))));
  }

  private void givenNav(TulevaFund fund, String nav) {
    given(fundNavQueryService.findLatestNavDateOnOrBefore(fund.getCode(), AS_OF_DATE))
        .willReturn(Optional.of(NAV_DATE));
    given(fundNavQueryService.findNavPerUnit(fund.getCode(), NAV_DATE))
        .willReturn(Optional.of(new BigDecimal(nav)));
  }

  private void givenParameters(TulevaFund fund, String paymentLimitBuffer) {
    given(parameterRepository.findLatestValue(PEVA_RAVA_PAYMENT_LIMIT_BUFFER, fund, AS_OF_DATE))
        .willReturn(new BigDecimal(paymentLimitBuffer));
    given(parameterRepository.findLatestValue(PEVA_RAVA_PAYMENT_LIMIT_ROUNDING_STEP, AS_OF_DATE))
        .willReturn(new BigDecimal("5000"));
    given(parameterRepository.findLatestValue(PEVA_RAVA_TRADE_BUFFER_PERCENT, AS_OF_DATE))
        .willReturn(new BigDecimal("0.02"));
    given(parameterRepository.findLatestValue(PEVA_RAVA_TRADE_ROUNDING_STEP, AS_OF_DATE))
        .willReturn(new BigDecimal("1000"));
  }

  private EpisReportSummary summary(
      ReportType reportType, Long reportId, TulevaFund fund, Map<String, Object> data) {
    return EpisReportSummary.builder()
        .reportId(reportId)
        .reportType(reportType)
        .reportDate(AS_OF_DATE)
        .fund(fund)
        .fundIsin(fund.getIsin())
        .data(data)
        .build();
  }
}
