package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.epis.R16Phase.ACTIVE;
import static ee.tuleva.onboarding.investment.epis.R16Phase.BUFFERED;
import static ee.tuleva.onboarding.investment.epis.R16Phase.IGNORE;
import static ee.tuleva.onboarding.investment.epis.R16Phase.VISIBLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class R16PhaseCalculatorTest {

  private final R45ReportService r45ReportService = mock(R45ReportService.class);

  private final R16PhaseCalculator calculator = new R16PhaseCalculator(r45ReportService);

  @Test
  void ignoreWhenNoR16Data() {
    assertThat(calculator.phaseFor(null, LocalDate.of(2026, 6, 10))).isEqualTo(IGNORE);
  }

  @Test
  void visibleBeforeSellByDate() {
    assertThat(calculator.phaseFor(flow(TUK75), LocalDate.of(2026, 6, 5))).isEqualTo(VISIBLE);
  }

  @Test
  void activeFromSellByUntilPaymentDeadline() {
    assertThat(calculator.phaseFor(flow(TUK75), LocalDate.of(2026, 6, 8))).isEqualTo(ACTIVE);
    assertThat(calculator.phaseFor(flow(TUK75), LocalDate.of(2026, 6, 14))).isEqualTo(ACTIVE);
  }

  @Test
  void bufferedFromPaymentDeadlineUntilTwentieth() {
    assertThat(calculator.phaseFor(flow(TUK75), LocalDate.of(2026, 6, 15))).isEqualTo(BUFFERED);
    assertThat(calculator.phaseFor(flow(TUK75), LocalDate.of(2026, 6, 20))).isEqualTo(BUFFERED);
  }

  @Test
  void ignoreAfterTwentiethOfPaymentMonth() {
    assertThat(calculator.phaseFor(flow(TUK75), LocalDate.of(2026, 6, 21))).isEqualTo(IGNORE);
  }

  @Test
  void tuv100SuppressedWhenR45HasRedRowsInSixteenToTwentyWindow() {
    given(r45ReportService.getLatestRedRowSettlementDates(TUV100))
        .willReturn(List.of(LocalDate.of(2026, 6, 17)));

    assertThat(calculator.phaseFor(flow(TUV100), LocalDate.of(2026, 6, 10))).isEqualTo(IGNORE);
  }

  @Test
  void tuv100NotSuppressedWhenRedRowsOutsideWindow() {
    given(r45ReportService.getLatestRedRowSettlementDates(TUV100))
        .willReturn(List.of(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 25)));

    assertThat(calculator.phaseFor(flow(TUV100), LocalDate.of(2026, 6, 10))).isEqualTo(ACTIVE);
  }

  @Test
  void otherFundsNotSuppressedByR45RedRows() {
    given(r45ReportService.getLatestRedRowSettlementDates(TUK75))
        .willReturn(List.of(LocalDate.of(2026, 6, 17)));

    assertThat(calculator.phaseFor(flow(TUK75), LocalDate.of(2026, 6, 10))).isEqualTo(ACTIVE);
  }

  @Test
  void exposesSuppressionStatusForTuv100() {
    given(r45ReportService.getLatestRedRowSettlementDates(TUV100))
        .willReturn(List.of(LocalDate.of(2026, 6, 17)));

    assertThat(calculator.isSuppressedByR45(flow(TUV100))).isTrue();
    assertThat(calculator.isSuppressedByR45(flow(TUK75))).isFalse();
  }

  private R16FundFlow flow(TulevaFund fund) {
    return new R16FundFlow(
        fund,
        new BigDecimal("1000"),
        new BigDecimal("500"),
        new BigDecimal("1200"),
        LocalDate.of(2026, 6, 1),
        LocalDate.of(2026, 6, 15),
        LocalDate.of(2026, 6, 8));
  }
}
