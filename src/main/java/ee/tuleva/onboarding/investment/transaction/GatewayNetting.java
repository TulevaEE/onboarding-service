package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.epis.R45TransactionType.Direction.INFLOW;
import static ee.tuleva.onboarding.investment.epis.R45TransactionType.Direction.OUTFLOW;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.epis.R45Row;
import ee.tuleva.onboarding.investment.epis.R45TransactionType.Direction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
class GatewayNetting {

  Netted<BigDecimal> r45(NettingWindow window, List<R45Row> rows) {
    LocalDate today = window.today();
    List<R45Row> notYetSettled = rows.stream().filter(row -> !settlesBefore(row, today)).toList();
    BigDecimal outflowDueTodayOrUndated =
        valueOf(
            rows.stream()
                .filter(row -> row.settlementDate() == null || today.equals(row.settlementDate()))
                .toList(),
            OUTFLOW);
    List<R45Row> later = rows.stream().filter(settlesAfter(today)).toList();
    return new Netted<>(
        outflowBeyondInflow(notYetSettled),
        outflowDueTodayOrUndated.add(outflowBeyondInflow(later)));
  }

  private static boolean settlesBefore(R45Row row, LocalDate day) {
    LocalDate settlementDate = row.settlementDate();
    return settlementDate != null && settlementDate.isBefore(day);
  }

  private static Predicate<R45Row> settlesAfter(LocalDate day) {
    return row -> {
      LocalDate settlementDate = row.settlementDate();
      return settlementDate != null && settlementDate.isAfter(day);
    };
  }

  private static BigDecimal outflowBeyondInflow(List<R45Row> rows) {
    return ZERO.max(valueOf(rows, OUTFLOW).subtract(valueOf(rows, INFLOW)));
  }

  private static BigDecimal valueOf(List<R45Row> rows, Direction direction) {
    return rows.stream()
        .filter(row -> row.direction() == direction)
        .map(R45Row::valueEur)
        .reduce(ZERO, BigDecimal::add);
  }
}
