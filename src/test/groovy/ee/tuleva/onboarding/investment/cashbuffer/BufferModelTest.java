package ee.tuleva.onboarding.investment.cashbuffer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class BufferModelTest {

  private static final BufferModel MODEL =
      new BufferModel(
          new BigDecimal("0.95"),
          new BigDecimal("0.20"),
          new BigDecimal("0.1"),
          new BigDecimal("24000.00"));

  @Test
  void recommendsTheFloorPlusTheOutflowTailNotCoveredByCreditedInflowPlusAccruedFees() {
    var window =
        window(
            month(1, "900000.00", "30000.00"),
            month(2, "800000.00", "72000.00"),
            month(3, "0.00", "0.00"),
            month(4, "1000000.00", "15000.00"));

    var recommendation = MODEL.recommend(window, new BigDecimal("3000.00"));

    assertThat(recommendation)
        .isEqualTo(
            new Recommendation(
                MODEL,
                new BigDecimal("65700.00"),
                new BigDecimal("480000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("44700.00")));
  }

  @Test
  void creditedInflowAboveTheOutflowTailNeverTakesTheBufferBelowTheFloorPlusFees() {
    var window = window(month(1, "5000000.00", "1000.00"), month(2, "5000000.00", "2000.00"));

    var recommendation = MODEL.recommend(window, new BigDecimal("300.00"));

    assertThat(recommendation.recommended()).isEqualByComparingTo("24300.00");
  }

  @Test
  void withNoInflowCreditTheWholeOutflowTailIsHeld() {
    var noCredit =
        new BufferModel(
            new BigDecimal("0.95"),
            new BigDecimal("0.20"),
            BigDecimal.ZERO,
            new BigDecimal("24000.00"));
    var window = window(month(1, "5000000.00", "1000.00"), month(2, "5000000.00", "2000.00"));

    var recommendation = noCredit.recommend(window, new BigDecimal("300.00"));

    assertThat(recommendation.outflowAtPercentile()).isEqualByComparingTo("1950.00");
    assertThat(recommendation.recommended()).isEqualByComparingTo("26250.00");
  }

  private static FlowWindow window(MonthlyFlows... months) {
    return new FlowWindow(List.of(months));
  }

  private static MonthlyFlows month(int month, String inflow, String tailOutflow) {
    var zero = new BigDecimal("0.00");
    return new MonthlyFlows(
        YearMonth.of(2026, month),
        new BigDecimal(inflow),
        zero,
        new BigDecimal(tailOutflow),
        new BigDecimal("999999.00"),
        zero,
        0);
  }
}
