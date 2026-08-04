package ee.tuleva.onboarding.savings.fund.taxreport;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.SUBTRACTION;
import static ee.tuleva.onboarding.savings.fund.taxreport.CostBasisMethod.FIFO;
import static ee.tuleva.onboarding.savings.fund.taxreport.CostBasisMethod.WEIGHTED_AVERAGE;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CostBasisCalculatorTest {

  private static final String TKF = "EE0000003283";
  private static final LocalDate START_OF_2025 = LocalDate.parse("2025-01-01");
  private static final LocalDate END_OF_2025 = LocalDate.parse("2025-12-31");

  private final CostBasisCalculator calculator = new CostBasisCalculator();

  private static Transaction transaction(
      String time, String units, String nav, CashFlow.Type type) {
    BigDecimal unitCount = new BigDecimal(units);
    BigDecimal navPerUnit = new BigDecimal(nav);
    return Transaction.builder()
        .id(UUID.nameUUIDFromBytes((time + type).getBytes()))
        .amount(unitCount.multiply(navPerUnit))
        .currency(EUR)
        .time(Instant.parse(time))
        .isin(TKF)
        .type(type)
        .units(unitCount)
        .nav(navPerUnit)
        .build();
  }

  private static Transaction buy(String time, String units, String nav) {
    return transaction(time, units, nav, CONTRIBUTION_CASH);
  }

  private static Transaction sell(String time, String units, String nav) {
    return transaction(time, units, nav, SUBTRACTION);
  }

  private static final List<Transaction> HISTORY =
      List.of(
          buy("2024-02-15T10:00:00Z", "100", "10"),
          buy("2024-06-03T10:00:00Z", "50", "10.5"),
          buy("2025-01-20T10:00:00Z", "80", "11.2"),
          sell("2025-09-10T10:00:00Z", "40", "12"),
          buy("2025-11-05T10:00:00Z", "30", "11.8"));

  @Test
  void usesTheOldestUnitsFirstUnderFifo() {
    RealisedGain gain =
        calculator.realisedGainsBetween(HISTORY, START_OF_2025, END_OF_2025, FIFO).getFirst();

    assertThat(gain.proceeds()).isEqualByComparingTo("480.00");
    assertThat(gain.acquisitionCost()).isEqualByComparingTo("400.00");
    assertThat(gain.gain()).isEqualByComparingTo("80.00");
  }

  @Test
  void spreadsCostAcrossAllHoldingsUnderTheWeightedAverageMethod() {
    RealisedGain gain =
        calculator
            .realisedGainsBetween(HISTORY, START_OF_2025, END_OF_2025, WEIGHTED_AVERAGE)
            .getFirst();

    assertThat(gain.acquisitionCost()).isEqualByComparingTo("421.04");
    assertThat(gain.gain()).isEqualByComparingTo("58.96");
  }

  @Test
  void excludesDisposalsOutsideTheRequestedPeriod() {
    assertThat(
            calculator.realisedGainsBetween(
                HISTORY,
                LocalDate.parse("2024-01-01"),
                LocalDate.parse("2024-12-31"),
                WEIGHTED_AVERAGE))
        .isEmpty();
  }

  @Test
  void hasNothingToReportWhenNothingWasEverSold() {
    List<Transaction> onlyPurchases = HISTORY.stream().filter(Transaction::isAcquisition).toList();

    assertThat(
            calculator.realisedGainsBetween(
                onlyPurchases,
                LocalDate.parse("2020-01-01"),
                LocalDate.parse("2026-12-31"),
                WEIGHTED_AVERAGE))
        .isEmpty();
  }

  @Test
  void pricesALaterRedemptionOffTheAverageNotTheLeftoverFifoLots() {
    List<Transaction> twoLots =
        List.of(
            buy("2025-01-01T10:00:00Z", "100", "10"),
            buy("2025-02-01T10:00:00Z", "100", "20"),
            sell("2025-03-01T10:00:00Z", "100", "30"),
            sell("2025-04-01T10:00:00Z", "100", "30"));

    List<RealisedGain> gains =
        calculator.realisedGainsBetween(twoLots, START_OF_2025, END_OF_2025, WEIGHTED_AVERAGE);

    assertThat(gains.get(0).acquisitionCost()).isEqualByComparingTo("1500.00");
    assertThat(gains.get(1).acquisitionCost()).isEqualByComparingTo("1500.00");
  }

  @Test
  void takesTheSecondLotOnceTheFirstIsExhaustedUnderFifo() {
    List<Transaction> twoLots =
        List.of(
            buy("2025-01-01T10:00:00Z", "100", "10"),
            buy("2025-02-01T10:00:00Z", "100", "20"),
            sell("2025-03-01T10:00:00Z", "100", "30"),
            sell("2025-04-01T10:00:00Z", "100", "30"));

    List<RealisedGain> gains =
        calculator.realisedGainsBetween(twoLots, START_OF_2025, END_OF_2025, FIFO);

    assertThat(gains.get(0).acquisitionCost()).isEqualByComparingTo("1000.00");
    assertThat(gains.get(1).acquisitionCost()).isEqualByComparingTo("2000.00");
  }
}
