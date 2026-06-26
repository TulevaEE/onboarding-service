package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.report.ReportType.R16_FORECASTED_PAYMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import ee.tuleva.onboarding.savings.fund.nav.FundNavQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class R16FlowCalculationServiceTest {

  private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 6, 11);
  private static final LocalDate NAV_DATE = LocalDate.of(2026, 6, 10);

  private final EpisReportSummaryRepository summaryRepository =
      mock(EpisReportSummaryRepository.class);
  private final FundNavQueryService fundNavQueryService = mock(FundNavQueryService.class);

  private final R16FlowCalculationService service =
      new R16FlowCalculationService(
          summaryRepository,
          new OwnFundNavProvider(fundNavQueryService),
          new EstonianCalendar(new PublicHolidays()));

  @Test
  void calculatesOutflowEurAndDeadlinesFromLatestSummary() {
    givenR16Summary(
        Map.of("fondimaksedUnits", 1000, "uhekordsedUnits", 500, "paymentMonth", "2026-06"));
    givenNav("0.80");

    Optional<R16FundFlow> flow = service.calculateFlows(TUK75, AS_OF_DATE);

    assertThat(flow).isPresent();
    assertThat(flow.get())
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new R16FundFlow(
                TUK75,
                new BigDecimal("1000"),
                new BigDecimal("500"),
                new BigDecimal("1200"),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 8)));
  }

  @Test
  void adjustsPaymentDeadlineToNextEstonianBusinessDay() {
    givenR16Summary(
        Map.of("fondimaksedUnits", 100, "uhekordsedUnits", 0, "paymentMonth", "2026-08"));
    givenNav("0.80");

    R16FundFlow flow = service.calculateFlows(TUK75, AS_OF_DATE).orElseThrow();

    assertThat(flow.paymentDeadline()).isEqualTo(LocalDate.of(2026, 8, 17));
    assertThat(flow.sellByDate()).isEqualTo(LocalDate.of(2026, 8, 10));
  }

  @Test
  void emptyWhenNoR16SummaryExists() {
    assertThat(service.calculateFlows(TUK75, AS_OF_DATE)).isEmpty();
  }

  @Test
  void throwsWhenNavMissing() {
    givenR16Summary(
        Map.of("fondimaksedUnits", 100, "uhekordsedUnits", 0, "paymentMonth", "2026-06"));

    assertThatThrownBy(() -> service.calculateFlows(TUK75, AS_OF_DATE))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void throwsWhenNavOutOfReasonableRange() {
    givenR16Summary(
        Map.of("fondimaksedUnits", 100, "uhekordsedUnits", 0, "paymentMonth", "2026-06"));
    givenNav("0.001");

    assertThatThrownBy(() -> service.calculateFlows(TUK75, AS_OF_DATE))
        .isInstanceOf(IllegalStateException.class);
  }

  private void givenR16Summary(Map<String, Object> data) {
    given(
            summaryRepository.findTopByReportTypeAndFundOrderByReportDateDescIdDesc(
                R16_FORECASTED_PAYMENTS, TUK75))
        .willReturn(
            Optional.of(
                EpisReportSummary.builder()
                    .reportId(1L)
                    .reportType(R16_FORECASTED_PAYMENTS)
                    .reportDate(AS_OF_DATE)
                    .fund(TUK75)
                    .fundIsin(TUK75.getIsin())
                    .data(data)
                    .build()));
  }

  private void givenNav(String nav) {
    given(fundNavQueryService.findLatestNavDateOnOrBefore(TUK75.getCode(), AS_OF_DATE))
        .willReturn(Optional.of(NAV_DATE));
    given(fundNavQueryService.findNavPerUnit(TUK75.getCode(), NAV_DATE))
        .willReturn(Optional.of(new BigDecimal(nav)));
  }
}
