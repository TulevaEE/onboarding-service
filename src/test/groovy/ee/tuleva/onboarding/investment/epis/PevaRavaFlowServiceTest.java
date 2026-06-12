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

  private final EpisReportSummaryRepository summaryRepository =
      mock(EpisReportSummaryRepository.class);
  private final FundNavQueryService fundNavQueryService = mock(FundNavQueryService.class);
  private final InvestmentParameterRepository parameterRepository =
      mock(InvestmentParameterRepository.class);
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-08-14T10:00:00Z"), ZoneId.of("Europe/Tallinn"));

  private final PevaRavaFlowService service =
      new PevaRavaFlowService(
          summaryRepository,
          new OwnFundNavProvider(fundNavQueryService),
          parameterRepository,
          clock);

  @Test
  void calculatesFlowsFromUnitsAndLatestNav() {
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
    given(summaryRepository.findTopByReportTypeAndFundOrderByReportDateDescIdDesc(R17_PEVA, TUK75))
        .willReturn(
            Optional.of(summary(TUK75, Map.of("pikUnits", 10000, "switchingNetUnits", -5000))));
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
  void omitsFundWithoutAnySummaries() {
    assertThat(service.calculateFlows()).isEmpty();
  }

  @Test
  void throwsWhenNavMissing() {
    givenSummaries(TUK75, Map.of("pikUnits", 10000, "switchingNetUnits", 0), 0);

    assertThatThrownBy(service::calculateFlows).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void throwsWhenNavOutOfReasonableRange() {
    givenSummaries(TUK75, Map.of("pikUnits", 10000, "switchingNetUnits", 0), 0);
    givenNav(TUK75, "12.00");

    assertThatThrownBy(service::calculateFlows).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void throwsWhenEurAmountsAreImplausiblyLarge() {
    givenSummaries(TUK75, Map.of("pikUnits", 900000000, "switchingNetUnits", 0), 0);
    givenNav(TUK75, "0.80");

    assertThatThrownBy(service::calculateFlows).isInstanceOf(IllegalStateException.class);
  }

  private void givenSummaries(TulevaFund fund, Map<String, Object> r17Data, int ravaUnits) {
    given(summaryRepository.findTopByReportTypeAndFundOrderByReportDateDescIdDesc(R17_PEVA, fund))
        .willReturn(Optional.of(summary(fund, r17Data)));
    given(summaryRepository.findTopByReportTypeAndFundOrderByReportDateDescIdDesc(R21_RAVA, fund))
        .willReturn(Optional.of(summary(fund, Map.of("ravaUnits", ravaUnits))));
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

  private EpisReportSummary summary(TulevaFund fund, Map<String, Object> data) {
    return EpisReportSummary.builder()
        .reportId(1L)
        .reportType(R17_PEVA)
        .reportDate(AS_OF_DATE)
        .fund(fund)
        .fundIsin(fund.getIsin())
        .data(data)
        .build();
  }
}
