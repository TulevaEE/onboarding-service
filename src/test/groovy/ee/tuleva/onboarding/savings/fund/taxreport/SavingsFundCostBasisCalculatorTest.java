package ee.tuleva.onboarding.savings.fund.taxreport;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.epis.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.CashFlow.Type.SUBTRACTION;
import static ee.tuleva.onboarding.savings.fund.taxreport.CostBasisMethod.FIFO;
import static ee.tuleva.onboarding.savings.fund.taxreport.CostBasisMethod.WEIGHTED_AVERAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.epis.CashFlow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SavingsFundCostBasisCalculatorTest {

  private static final String TKF = "EE0000003283";
  private static final LocalDate START_OF_2025 = LocalDate.parse("2025-01-01");
  private static final LocalDate END_OF_2025 = LocalDate.parse("2025-12-31");

  private final SavingsFundCostBasisCalculator calculator = new SavingsFundCostBasisCalculator();

  private static Transaction transaction(
      String time, String units, String nav, BigDecimal amount, CashFlow.Type type) {
    return Transaction.builder()
        .id(UUID.nameUUIDFromBytes((time + type).getBytes()))
        .amount(amount)
        .currency(EUR)
        .time(Instant.parse(time))
        .isin(TKF)
        .type(type)
        .units(new BigDecimal(units))
        .nav(new BigDecimal(nav))
        .build();
  }

  private static Transaction transaction(
      String time, String units, String nav, CashFlow.Type type) {
    return transaction(time, units, nav, new BigDecimal(units).multiply(new BigDecimal(nav)), type);
  }

  private static Transaction buy(String time, String units, String nav) {
    return transaction(time, units, nav, CONTRIBUTION_CASH);
  }

  private static Transaction sell(String time, String units, String nav) {
    return transaction(time, units, nav, SUBTRACTION);
  }

  private static Transaction buyPaying(String time, String units, String nav, String amount) {
    return transaction(time, units, nav, new BigDecimal(amount), CONTRIBUTION_CASH);
  }

  private static Transaction sellReceiving(String time, String units, String nav, String amount) {
    return transaction(time, units, nav, new BigDecimal(amount), SUBTRACTION);
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
  void refusesToPriceAHistoryWhereAnAcquisitionIsMissingItsUnits() {
    Transaction acquisitionWithoutUnits =
        Transaction.builder()
            .id(UUID.nameUUIDFromBytes("missing-units".getBytes()))
            .amount(new BigDecimal("1000"))
            .currency(EUR)
            .time(Instant.parse("2025-01-01T10:00:00Z"))
            .isin(TKF)
            .type(CONTRIBUTION_CASH)
            .nav(new BigDecimal("10"))
            .build();

    List<Transaction> history =
        List.of(acquisitionWithoutUnits, sell("2025-03-01T10:00:00Z", "100", "30"));

    assertThatThrownBy(
            () -> calculator.realisedGainsBetween(history, START_OF_2025, END_OF_2025, FIFO))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesToPriceAHistoryWhereAnAcquisitionHasZeroUnits() {
    List<Transaction> history =
        List.of(
            buyPaying("2025-01-01T10:00:00Z", "0", "10", "1000"),
            sell("2025-03-01T10:00:00Z", "100", "30"));

    assertThatThrownBy(
            () -> calculator.realisedGainsBetween(history, START_OF_2025, END_OF_2025, FIFO))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void pricesAnAcquisitionThatHasNoNavOffTheCashPaid() {
    Transaction acquisitionWithoutNav =
        Transaction.builder()
            .id(UUID.nameUUIDFromBytes("missing-nav".getBytes()))
            .amount(new BigDecimal("1000"))
            .currency(EUR)
            .time(Instant.parse("2025-01-01T10:00:00Z"))
            .isin(TKF)
            .type(CONTRIBUTION_CASH)
            .units(new BigDecimal("100"))
            .build();

    List<Transaction> history =
        List.of(acquisitionWithoutNav, sell("2025-03-01T10:00:00Z", "100", "30"));

    RealisedGain gain =
        calculator.realisedGainsBetween(history, START_OF_2025, END_OF_2025, FIFO).getFirst();

    assertThat(gain.acquisitionCost()).isEqualByComparingTo("1000.00");
    assertThat(gain.gain()).isEqualByComparingTo("2000.00");
  }

  @Test
  void pricesAcquisitionsAtTheCashPaidWhenIssuedUnitsDoNotMultiplyBackToIt() {
    List<Transaction> history =
        List.of(
            buyPaying("2025-02-01T10:00:00Z", "9.87654", "10.12570", "100.00"),
            sellReceiving("2025-06-01T10:00:00Z", "9.87654", "10.63140", "105.00"));

    RealisedGain gain =
        calculator.realisedGainsBetween(history, START_OF_2025, END_OF_2025, FIFO).getFirst();

    assertThat(gain.acquisitionCost()).isEqualByComparingTo("100.00");
    assertThat(gain.proceeds()).isEqualByComparingTo("105.00");
    assertThat(gain.gain()).isEqualByComparingTo("5.00");
  }

  @Test
  void refusesToPriceARedemptionOfMoreUnitsThanWereEverHeld() {
    List<Transaction> oversell =
        List.of(buy("2025-01-01T10:00:00Z", "10", "10"), sell("2025-03-01T10:00:00Z", "20", "12"));

    assertThatThrownBy(
            () -> calculator.realisedGainsBetween(oversell, START_OF_2025, END_OF_2025, FIFO))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                calculator.realisedGainsBetween(
                    oversell, START_OF_2025, END_OF_2025, WEIGHTED_AVERAGE))
        .isInstanceOf(IllegalStateException.class);
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

  private static Transaction sellSettledOn(
      String time, String settledTime, String units, String nav) {
    BigDecimal unitCount = new BigDecimal(units);
    BigDecimal navPerUnit = new BigDecimal(nav);
    return Transaction.builder()
        .id(UUID.nameUUIDFromBytes((time + settledTime + SUBTRACTION).getBytes()))
        .amount(unitCount.multiply(navPerUnit))
        .currency(EUR)
        .time(Instant.parse(time))
        .settledTime(Instant.parse(settledTime))
        .isin(TKF)
        .type(SUBTRACTION)
        .units(unitCount)
        .nav(navPerUnit)
        .build();
  }

  @Test
  void countsARedemptionInTheYearItWasPaidOutNotBooked() {
    List<Transaction> history =
        List.of(
            buy("2025-01-10T10:00:00Z", "100", "10"),
            sellSettledOn("2025-12-31T10:00:00Z", "2026-01-05T10:00:00Z", "40", "12"));

    assertThat(calculator.realisedGainsBetween(history, START_OF_2025, END_OF_2025, FIFO))
        .isEmpty();
    assertThat(
            calculator.realisedGainsBetween(
                history, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), FIFO))
        .hasSize(1);
  }

  @Test
  void datesARealisedGainByThePayoutDate() {
    List<Transaction> history =
        List.of(
            buy("2025-01-10T10:00:00Z", "100", "10"),
            sellSettledOn("2025-06-01T10:00:00Z", "2025-06-04T10:00:00Z", "40", "12"));

    RealisedGain gain =
        calculator.realisedGainsBetween(history, START_OF_2025, END_OF_2025, FIFO).getFirst();

    assertThat(gain.time()).isEqualTo(Instant.parse("2025-06-04T10:00:00Z"));
  }

  @Test
  void stillConsumesLotsInBookingOrderWhenPayoutLagsBehind() {
    List<Transaction> history =
        List.of(
            buy("2025-01-01T10:00:00Z", "100", "10"),
            buy("2025-02-01T10:00:00Z", "100", "20"),
            sellSettledOn("2025-03-01T10:00:00Z", "2025-03-05T10:00:00Z", "100", "30"),
            sellSettledOn("2025-04-01T10:00:00Z", "2025-04-05T10:00:00Z", "100", "30"));

    List<RealisedGain> gains =
        calculator.realisedGainsBetween(history, START_OF_2025, END_OF_2025, FIFO);

    assertThat(gains.get(0).acquisitionCost()).isEqualByComparingTo("1000.00");
    assertThat(gains.get(1).acquisitionCost()).isEqualByComparingTo("2000.00");
  }
}
