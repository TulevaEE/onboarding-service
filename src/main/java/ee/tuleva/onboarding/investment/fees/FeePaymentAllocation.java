package ee.tuleva.onboarding.investment.fees;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

final class FeePaymentAllocation {

  private final List<FeeMonthBalance> months;
  private final BigDecimal threshold;

  private FeePaymentAllocation(List<FeeMonthBalance> months, BigDecimal threshold) {
    this.months = new ArrayList<>(months);
    this.threshold = threshold;
  }

  static List<FeeMonthBalance> allocate(
      List<FeeMonthBalance> months, List<FeePayment> payments, BigDecimal threshold) {
    var allocation = new FeePaymentAllocation(months, threshold);
    payments.forEach(allocation::apply);
    return List.copyOf(allocation.months);
  }

  private void apply(FeePayment payment) {
    var completeMonths = monthsBefore(payment.month());
    var open = completeMonths.stream().filter(this::owesOrIsOwed).toList();
    if (!clearsARunOfOpenMonths(payment, open)) {
      payOldestFirst(payment, open, completeMonths);
    }
  }

  private List<Integer> monthsBefore(LocalDate month) {
    return IntStream.range(0, months.size())
        .filter(index -> months.get(index).feeMonth().isBefore(month))
        .boxed()
        .toList();
  }

  private boolean owesOrIsOwed(int index) {
    return months.get(index).owed().signum() != 0;
  }

  private boolean clearsARunOfOpenMonths(FeePayment payment, List<Integer> open) {
    for (int first = 0; first < open.size(); first++) {
      var owedSoFar = ZERO;
      for (int last = first; last < open.size(); last++) {
        owedSoFar = owedSoFar.add(months.get(open.get(last)).owed());
        var difference = payment.amount().subtract(owedSoFar);
        if (difference.abs().compareTo(threshold) <= 0) {
          clear(open.subList(first, last + 1), difference, payment.bookedOn());
          return true;
        }
      }
    }
    return false;
  }

  private void clear(List<Integer> run, BigDecimal difference, LocalDate bookedOn) {
    var last = run.getLast();
    for (var index : run) {
      var month = months.get(index);
      var share = index.equals(last) ? month.owed().add(difference) : month.owed();
      months.set(index, month.clearedBy(share, bookedOn));
    }
  }

  private void payOldestFirst(
      FeePayment payment, List<Integer> open, List<Integer> completeMonths) {
    var remaining = payment.amount();
    for (var index : open) {
      var month = months.get(index);
      if (month.owed().signum() <= 0 || remaining.signum() <= 0) {
        continue;
      }
      if (remaining.compareTo(month.owed()) >= 0) {
        remaining = remaining.subtract(month.owed());
        months.set(index, month.clearedBy(month.owed(), payment.bookedOn()));
      } else {
        months.set(index, month.partlyPaidBy(remaining, payment.bookedOn()));
        remaining = ZERO;
      }
    }
    if (remaining.signum() != 0) {
      var newest = completeMonths.getLast();
      months.set(newest, months.get(newest).partlyPaidBy(remaining, payment.bookedOn()));
    }
  }
}
