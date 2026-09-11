package ee.tuleva.onboarding.epis;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CashFlowTest {

  @Test
  void getPriceTime_returnsPriceTimeWhenPresent() {
    Instant time = Instant.parse("2020-01-01T00:00:00Z");
    Instant priceTime = Instant.parse("2020-01-02T00:00:00Z");
    CashFlow cashFlow = CashFlow.builder().time(time).priceTime(priceTime).build();

    assertThat(cashFlow.getPriceTime()).isEqualTo(priceTime);
  }

  @Test
  void getPriceTime_fallsBackToTimeWhenPriceTimeAbsent() {
    Instant time = Instant.parse("2020-01-01T00:00:00Z");
    CashFlow cashFlow = CashFlow.builder().time(time).build();

    assertThat(cashFlow.getPriceTime()).isEqualTo(time);
  }

  @ParameterizedTest
  @CsvSource({
    "CONTRIBUTION_CASH, true",
    "CONTRIBUTION_CASH_WORKPLACE, true",
    "CONTRIBUTION, true",
    "SUBTRACTION, false",
    "CASH, false",
    "REFUND, false",
    "TRANSFER_TO_PIK, false",
    "TRANSFER_FROM_PIK, false",
    "OTHER, false"
  })
  void isContribution_classifiesByType(CashFlow.Type type, boolean expected) {
    CashFlow cashFlow = CashFlow.builder().type(type).build();

    assertThat(cashFlow.isContribution()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "CONTRIBUTION_CASH, true",
    "CONTRIBUTION_CASH_WORKPLACE, true",
    "CONTRIBUTION, false",
    "SUBTRACTION, false",
    "CASH, false",
    "REFUND, false",
    "TRANSFER_TO_PIK, false",
    "TRANSFER_FROM_PIK, false",
    "OTHER, false"
  })
  void isCashContribution_classifiesByType(CashFlow.Type type, boolean expected) {
    CashFlow cashFlow = CashFlow.builder().type(type).build();

    assertThat(cashFlow.isCashContribution()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "CONTRIBUTION_CASH, false",
    "CONTRIBUTION_CASH_WORKPLACE, false",
    "CONTRIBUTION, false",
    "SUBTRACTION, true",
    "CASH, false",
    "REFUND, false",
    "TRANSFER_TO_PIK, false",
    "TRANSFER_FROM_PIK, false",
    "OTHER, false"
  })
  void isSubtraction_classifiesByType(CashFlow.Type type, boolean expected) {
    CashFlow cashFlow = CashFlow.builder().type(type).build();

    assertThat(cashFlow.isSubtraction()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "CONTRIBUTION_CASH, false",
    "CONTRIBUTION_CASH_WORKPLACE, false",
    "CONTRIBUTION, false",
    "SUBTRACTION, false",
    "CASH, true",
    "REFUND, false",
    "TRANSFER_TO_PIK, false",
    "TRANSFER_FROM_PIK, false",
    "OTHER, false"
  })
  void isCash_classifiesByType(CashFlow.Type type, boolean expected) {
    CashFlow cashFlow = CashFlow.builder().type(type).build();

    assertThat(cashFlow.isCash()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "CONTRIBUTION_CASH, false",
    "CONTRIBUTION_CASH_WORKPLACE, false",
    "CONTRIBUTION, false",
    "SUBTRACTION, false",
    "CASH, false",
    "REFUND, true",
    "TRANSFER_TO_PIK, false",
    "TRANSFER_FROM_PIK, false",
    "OTHER, false"
  })
  void isRefund_classifiesByType(CashFlow.Type type, boolean expected) {
    CashFlow cashFlow = CashFlow.builder().type(type).build();

    assertThat(cashFlow.isRefund()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "CONTRIBUTION_CASH, false",
    "CONTRIBUTION_CASH_WORKPLACE, false",
    "CONTRIBUTION, false",
    "SUBTRACTION, false",
    "CASH, false",
    "REFUND, false",
    "TRANSFER_TO_PIK, true",
    "TRANSFER_FROM_PIK, false",
    "OTHER, false"
  })
  void isTransferToPik_classifiesByType(CashFlow.Type type, boolean expected) {
    CashFlow cashFlow = CashFlow.builder().type(type).build();

    assertThat(cashFlow.isTransferToPik()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "CONTRIBUTION_CASH, false",
    "CONTRIBUTION_CASH_WORKPLACE, false",
    "CONTRIBUTION, false",
    "SUBTRACTION, false",
    "CASH, false",
    "REFUND, false",
    "TRANSFER_TO_PIK, false",
    "TRANSFER_FROM_PIK, true",
    "OTHER, false"
  })
  void isTransferFromPik_classifiesByType(CashFlow.Type type, boolean expected) {
    CashFlow cashFlow = CashFlow.builder().type(type).build();

    assertThat(cashFlow.isTransferFromPik()).isEqualTo(expected);
  }

  @Test
  void isAfter_trueWhenTimeAfterGivenInstant() {
    Instant cutoff = Instant.parse("2020-01-01T00:00:00Z");
    CashFlow cashFlow = CashFlow.builder().time(cutoff.plusSeconds(1)).build();

    assertThat(cashFlow.isAfter(cutoff)).isTrue();
  }

  @Test
  void isAfter_falseWhenTimeNotAfterGivenInstant() {
    Instant cutoff = Instant.parse("2020-01-01T00:00:00Z");
    CashFlow cashFlow = CashFlow.builder().time(cutoff.minusSeconds(1)).build();

    assertThat(cashFlow.isAfter(cutoff)).isFalse();
  }

  @Test
  void isPriceTimeAfter_trueWhenPriceTimeAfterGivenInstant() {
    Instant cutoff = Instant.parse("2020-01-01T00:00:00Z");
    CashFlow cashFlow = CashFlow.builder().priceTime(cutoff.plusSeconds(1)).build();

    assertThat(cashFlow.isPriceTimeAfter(cutoff)).isTrue();
  }

  @Test
  void isPriceTimeAfter_falseWhenPriceTimeNotAfterGivenInstant() {
    Instant cutoff = Instant.parse("2020-01-01T00:00:00Z");
    CashFlow cashFlow = CashFlow.builder().priceTime(cutoff.minusSeconds(1)).build();

    assertThat(cashFlow.isPriceTimeAfter(cutoff)).isFalse();
  }

  @Test
  void compareTo_ordersByTimeFirst() {
    CashFlow earlier =
        CashFlow.builder()
            .time(Instant.parse("2020-01-01T00:00:00Z"))
            .amount(BigDecimal.TEN)
            .currency(EUR)
            .type(CashFlow.Type.CASH)
            .build();
    CashFlow later =
        CashFlow.builder()
            .time(Instant.parse("2020-01-02T00:00:00Z"))
            .amount(BigDecimal.TEN)
            .currency(EUR)
            .type(CashFlow.Type.CASH)
            .build();

    assertThat(earlier.compareTo(later)).isNegative();
    assertThat(later.compareTo(earlier)).isPositive();
  }

  @Test
  void compareTo_fallsBackToPriceTimeWhenTimesEqual() {
    Instant time = Instant.parse("2020-01-01T00:00:00Z");
    CashFlow earlierPrice =
        CashFlow.builder()
            .time(time)
            .priceTime(Instant.parse("2019-12-31T00:00:00Z"))
            .amount(BigDecimal.TEN)
            .currency(EUR)
            .type(CashFlow.Type.CASH)
            .build();
    CashFlow laterPrice =
        CashFlow.builder()
            .time(time)
            .priceTime(Instant.parse("2020-01-02T00:00:00Z"))
            .amount(BigDecimal.TEN)
            .currency(EUR)
            .type(CashFlow.Type.CASH)
            .build();

    assertThat(earlierPrice.compareTo(laterPrice)).isNegative();
  }

  @Test
  void compareTo_fallsBackToAmountWhenTimeAndPriceTimeEqual() {
    Instant time = Instant.parse("2020-01-01T00:00:00Z");
    CashFlow smallerAmount =
        CashFlow.builder()
            .time(time)
            .priceTime(time)
            .amount(BigDecimal.ONE)
            .currency(EUR)
            .type(CashFlow.Type.CASH)
            .build();
    CashFlow largerAmount =
        CashFlow.builder()
            .time(time)
            .priceTime(time)
            .amount(BigDecimal.TEN)
            .currency(EUR)
            .type(CashFlow.Type.CASH)
            .build();

    assertThat(smallerAmount.compareTo(largerAmount)).isNegative();
  }

  @Test
  void compareTo_fallsBackToTypeWhenTimePriceTimeAmountAndCurrencyEqual() {
    Instant time = Instant.parse("2020-01-01T00:00:00Z");
    CashFlow contribution =
        CashFlow.builder()
            .time(time)
            .priceTime(time)
            .amount(BigDecimal.TEN)
            .currency(EUR)
            .type(CashFlow.Type.CONTRIBUTION_CASH)
            .build();
    CashFlow subtraction =
        CashFlow.builder()
            .time(time)
            .priceTime(time)
            .amount(BigDecimal.TEN)
            .currency(EUR)
            .type(CashFlow.Type.SUBTRACTION)
            .build();

    assertThat(contribution.compareTo(subtraction)).isNegative();
  }

  @Test
  void toString_formatsAllFields() {
    Instant time = Instant.parse("2020-01-01T00:00:00Z");
    Instant priceTime = Instant.parse("2020-01-02T00:00:00Z");
    CashFlow cashFlow =
        CashFlow.builder()
            .isin("EE123")
            .time(time)
            .priceTime(priceTime)
            .amount(BigDecimal.TEN)
            .currency(EUR)
            .type(CashFlow.Type.CASH)
            .units(BigDecimal.ONE)
            .nav(BigDecimal.valueOf(2))
            .build();

    assertThat(cashFlow.toString())
        .isEqualTo("{EE123, 2020-01-01T00:00:00Z, 10, CASH, 2020-01-02T00:00:00Z, units=1, nav=2}");
  }
}
