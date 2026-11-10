package ee.tuleva.onboarding.banking.statement;

import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.OPEN;
import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

public class DayClosingBalance {

  public static Optional<BigDecimal> on(BankStatement statement, LocalDate day, ZoneId timezone) {
    var opening = openingBalance(statement);
    var bookings = bookingsDatedInsideThePeriod(statement, timezone);
    if (!statement.isLastPage()
        || opening.isEmpty()
        || bookings.isEmpty()
        || !statement.getPeriod().covers(day)
        || !addUpToTheClosingBalance(statement, opening.get(), bookings.get())) {
      return Optional.empty();
    }
    return Optional.of(opening.get().add(sumBookedBy(day, bookings.get())));
  }

  private record DatedAmount(LocalDate day, BigDecimal amount) {}

  private static Optional<BigDecimal> openingBalance(BankStatement statement) {
    return statement.getBalances().stream()
        .filter(balance -> balance.type() == OPEN)
        .map(BankStatementBalance::balance)
        .findFirst();
  }

  private static Optional<List<DatedAmount>> bookingsDatedInsideThePeriod(
      BankStatement statement, ZoneId timezone) {
    var dated =
        statement.getEntries().stream()
            .filter(BankStatementEntry::booked)
            .map(
                entry ->
                    Optional.ofNullable(entry.receivedBefore())
                        .map(
                            bookedBy ->
                                new DatedAmount(
                                    bookedBy.atZone(timezone).toLocalDate(), entry.amount())))
            .toList();
    return dated.stream()
            .allMatch(
                booking -> booking.filter(it -> statement.getPeriod().covers(it.day())).isPresent())
        ? Optional.of(dated.stream().map(Optional::orElseThrow).toList())
        : Optional.empty();
  }

  private static boolean addUpToTheClosingBalance(
      BankStatement statement, BigDecimal opening, List<DatedAmount> bookings) {
    return statement
        .bookedBalance()
        .map(closing -> opening.add(sum(bookings)).compareTo(closing) == 0)
        .orElse(false);
  }

  private static BigDecimal sumBookedBy(LocalDate day, List<DatedAmount> bookings) {
    return sum(bookings.stream().filter(booking -> !booking.day().isAfter(day)).toList());
  }

  private static BigDecimal sum(List<DatedAmount> bookings) {
    return bookings.stream().map(DatedAmount::amount).reduce(ZERO, BigDecimal::add);
  }
}
