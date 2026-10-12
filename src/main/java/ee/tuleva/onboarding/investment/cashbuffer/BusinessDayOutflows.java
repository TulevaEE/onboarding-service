package ee.tuleva.onboarding.investment.cashbuffer;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

record BusinessDayOutflows(List<BigDecimal> operatingOutflows) {

  List<BigDecimal> forwardRollingTotals(int businessDays) {
    if (businessDays < 1 || operatingOutflows.isEmpty()) {
      throw new IllegalArgumentException(
          "Rolling totals need business days and a horizon of at least one: days="
              + operatingOutflows.size()
              + ", horizon="
              + businessDays);
    }
    var span = Math.min(businessDays, operatingOutflows.size());
    return IntStream.rangeClosed(0, operatingOutflows.size() - span)
        .mapToObj(
            start ->
                operatingOutflows.subList(start, start + span).stream()
                    .reduce(ZERO, BigDecimal::add))
        .toList();
  }
}
