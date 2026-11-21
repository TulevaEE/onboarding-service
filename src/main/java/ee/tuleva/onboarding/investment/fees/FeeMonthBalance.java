package ee.tuleva.onboarding.investment.fees;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public record FeeMonthBalance(
    LocalDate feeMonth,
    BigDecimal accrued,
    BigDecimal paid,
    BigDecimal owed,
    @Nullable LocalDate clearedOn,
    List<FeeMonthPayment> payments) {

  static FeeMonthBalance unpaid(LocalDate feeMonth, BigDecimal accrued) {
    return new FeeMonthBalance(feeMonth, accrued, ZERO, accrued, null, List.of());
  }

  public boolean isCleared() {
    return clearedOn != null;
  }

  public boolean hasBeenPaid() {
    return !payments.isEmpty();
  }

  FeeMonthBalance clearedBy(BigDecimal payment, LocalDate bookedOn) {
    return new FeeMonthBalance(
        feeMonth,
        accrued,
        paid.add(payment),
        ZERO,
        isCleared() ? clearedOn : bookedOn,
        withPayment(bookedOn, ZERO));
  }

  FeeMonthBalance partlyPaidBy(BigDecimal payment, LocalDate bookedOn) {
    var owedAfter = owed.subtract(payment);
    return new FeeMonthBalance(
        feeMonth,
        accrued,
        paid.add(payment),
        owedAfter,
        clearedOn,
        withPayment(bookedOn, owedAfter));
  }

  private List<FeeMonthPayment> withPayment(LocalDate bookedOn, BigDecimal owedAfter) {
    return Stream.concat(payments.stream(), Stream.of(new FeeMonthPayment(bookedOn, owedAfter)))
        .toList();
  }
}
