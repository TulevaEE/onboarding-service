package ee.tuleva.onboarding.investment.fees;

import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class FeePaymentAllocationTest {

  private static final BigDecimal THRESHOLD = new BigDecimal("0.02");
  private static final LocalDate AUGUST = LocalDate.of(2026, 8, 1);
  private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 1);
  private static final LocalDate OCTOBER = LocalDate.of(2026, 10, 1);
  private static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 1);

  @Test
  void withNoDebitEveryMonthStaysOwed() {
    var months = allocate(List.of(unpaid(SEPTEMBER, "1000.00"), unpaid(OCTOBER, "500.00")));

    assertThat(months).containsExactly(unpaid(SEPTEMBER, "1000.00"), unpaid(OCTOBER, "500.00"));
    assertThat(owed(months)).isEqualByComparingTo("1500.00");
  }

  @Test
  void aDebitEqualToTheMonthClearsItOnTheDayItIsBooked() {
    var months =
        allocate(
            List.of(unpaid(SEPTEMBER, "1000.00"), unpaid(OCTOBER, "500.00")),
            debit("2026-10-01", "1000.00"));

    assertThat(months)
        .containsExactly(
            month(SEPTEMBER, "1000.00", "1000.00", "0", "2026-10-01", paid("2026-10-01", "0")),
            unpaid(OCTOBER, "500.00"));
    assertThat(owed(months)).isEqualByComparingTo("500.00");
  }

  @Test
  void aDebitWithinTheThresholdClosesTheMonthAtWhatWasPaid() {
    var months = allocate(List.of(unpaid(SEPTEMBER, "1000.00")), debit("2026-10-01", "999.98"));

    assertThat(months)
        .containsExactly(
            month(SEPTEMBER, "1000.00", "999.98", "0", "2026-10-01", paid("2026-10-01", "0")));
  }

  @Test
  void aDebitBeyondTheThresholdClearsOnlyWhatItPaidAndTheRestStaysOwed() {
    var months = allocate(List.of(unpaid(SEPTEMBER, "1000.00")), debit("2026-10-01", "999.97"));

    assertThat(months)
        .containsExactly(
            month(SEPTEMBER, "1000.00", "999.97", "0.03", null, paid("2026-10-01", "0.03")));
  }

  @Test
  void aSecondDebitForTheRestClearsTheMonth() {
    var months =
        allocate(
            List.of(unpaid(SEPTEMBER, "1000.00")),
            debit("2026-10-01", "600.00"),
            debit("2026-10-02", "400.00"));

    assertThat(months)
        .containsExactly(
            month(
                SEPTEMBER,
                "1000.00",
                "1000.00",
                "0",
                "2026-10-02",
                paid("2026-10-01", "400.00"),
                paid("2026-10-02", "0")));
  }

  @Test
  void oneDebitForTwoMonthsClearsBothOldestFirst() {
    var months =
        allocate(
            List.of(
                unpaid(SEPTEMBER, "1000.00"),
                unpaid(OCTOBER, "500.00"),
                unpaid(NOVEMBER, "200.00")),
            debit("2026-11-02", "1500.01"));

    assertThat(months)
        .containsExactly(
            month(SEPTEMBER, "1000.00", "1000.00", "0", "2026-11-02", paid("2026-11-02", "0")),
            month(OCTOBER, "500.00", "500.01", "0", "2026-11-02", paid("2026-11-02", "0")),
            unpaid(NOVEMBER, "200.00"));
  }

  @Test
  void aSecondDebitForAClearedMonthLeavesItClearedWithTheExcessOwedBack() {
    var months =
        allocate(
            List.of(unpaid(SEPTEMBER, "1000.00"), unpaid(OCTOBER, "1000.00")),
            debit("2026-10-05", "1000.00"),
            debit("2026-10-06", "1000.00"));

    assertThat(months)
        .containsExactly(
            month(
                SEPTEMBER,
                "1000.00",
                "2000.00",
                "-1000.00",
                "2026-10-05",
                paid("2026-10-05", "0"),
                paid("2026-10-06", "-1000.00")),
            unpaid(OCTOBER, "1000.00"));
    assertThat(owed(months)).isEqualByComparingTo(ZERO);
  }

  @Test
  void aDebitForMoreThanIsOwedClearsEveryMonthAndLeavesTheExcessOwedBackOnTheNewest() {
    var months =
        allocate(
            List.of(
                unpaid(SEPTEMBER, "1000.00"),
                unpaid(OCTOBER, "500.00"),
                unpaid(NOVEMBER, "200.00")),
            debit("2026-11-02", "1600.00"));

    assertThat(months)
        .containsExactly(
            month(SEPTEMBER, "1000.00", "1000.00", "0", "2026-11-02", paid("2026-11-02", "0")),
            month(
                OCTOBER,
                "500.00",
                "600.00",
                "-100.00",
                "2026-11-02",
                paid("2026-11-02", "0"),
                paid("2026-11-02", "-100.00")),
            unpaid(NOVEMBER, "200.00"));
    assertThat(owed(months)).isEqualByComparingTo("100.00");
  }

  @Test
  void aShortDebitPaysTheOldestMonthFirst() {
    var months =
        allocate(
            List.of(
                unpaid(SEPTEMBER, "1000.00"),
                unpaid(OCTOBER, "500.00"),
                unpaid(NOVEMBER, "200.00")),
            debit("2026-11-02", "1200.00"));

    assertThat(months)
        .containsExactly(
            month(SEPTEMBER, "1000.00", "1000.00", "0", "2026-11-02", paid("2026-11-02", "0")),
            month(OCTOBER, "500.00", "200.00", "300.00", null, paid("2026-11-02", "300.00")),
            unpaid(NOVEMBER, "200.00"));
  }

  @Test
  void aMonthThatAccruedNothingIsLeftAloneByADebitForTheNextOne() {
    var months =
        allocate(
            List.of(unpaid(AUGUST, "0"), unpaid(SEPTEMBER, "1000.00")),
            debit("2026-10-01", "1000.00"));

    assertThat(months)
        .containsExactly(
            unpaid(AUGUST, "0"),
            month(SEPTEMBER, "1000.00", "1000.00", "0", "2026-10-01", paid("2026-10-01", "0")));
  }

  @Test
  void aDebitMatchingALaterMonthClearsItAndLeavesAnOlderResidualOwed() {
    var months =
        allocate(
            List.of(
                unpaid(SEPTEMBER, "1000.00"),
                unpaid(OCTOBER, "1000.00"),
                unpaid(NOVEMBER, "300.00")),
            debit("2026-10-01", "999.50"),
            debit("2026-11-02", "1000.00"));

    assertThat(months)
        .containsExactly(
            month(SEPTEMBER, "1000.00", "999.50", "0.50", null, paid("2026-10-01", "0.50")),
            month(OCTOBER, "1000.00", "1000.00", "0", "2026-11-02", paid("2026-11-02", "0")),
            unpaid(NOVEMBER, "300.00"));
  }

  private static List<FeeMonthBalance> allocate(
      List<FeeMonthBalance> months, FeePayment... payments) {
    return FeePaymentAllocation.allocate(months, List.of(payments), THRESHOLD);
  }

  private static BigDecimal owed(List<FeeMonthBalance> months) {
    return months.stream().map(FeeMonthBalance::owed).reduce(ZERO, BigDecimal::add);
  }

  private static FeePayment debit(String bookedOn, String amount) {
    return new FeePayment(LocalDate.parse(bookedOn), new BigDecimal(amount));
  }

  private static FeeMonthPayment paid(String bookedOn, String owedAfter) {
    return new FeeMonthPayment(LocalDate.parse(bookedOn), amount(owedAfter));
  }

  private static FeeMonthBalance unpaid(LocalDate feeMonth, String accrued) {
    return new FeeMonthBalance(feeMonth, amount(accrued), ZERO, amount(accrued), null, List.of());
  }

  private static FeeMonthBalance month(
      LocalDate feeMonth,
      String accrued,
      String paid,
      String owed,
      @Nullable String clearedOn,
      FeeMonthPayment... payments) {
    return new FeeMonthBalance(
        feeMonth,
        amount(accrued),
        amount(paid),
        amount(owed),
        clearedOn == null ? null : LocalDate.parse(clearedOn),
        List.of(payments));
  }

  private static BigDecimal amount(String value) {
    return value.equals("0") ? ZERO : new BigDecimal(value);
  }
}
