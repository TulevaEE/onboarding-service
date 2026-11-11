package ee.tuleva.onboarding.banking.statement;

import static ee.tuleva.onboarding.banking.iso20022.camt052.EntryStatus2Code.BOOK;
import static ee.tuleva.onboarding.banking.iso20022.camt052.EntryStatus2Code.PDNG;
import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.CLOSE;
import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.OPEN;
import static java.time.temporal.ChronoUnit.MICROS;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.iso20022.camt052.EntryStatus2Code;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DayClosingBalanceTest {

  private static final LocalDate DATE = LocalDate.of(2026, 3, 13);
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  @Test
  void aDayInsideAMultiDayStatementIsTheOpeningBalancePlusItsBookingsUpToThatDay() {
    var statement =
        catchUpStatement(
            List.of(balance(OPEN, "1000.00"), balance(CLOSE, "1160.00")),
            List.of(
                bookedOn(DATE, "200.00"),
                bookedOn(DATE.plusDays(1), "-50.00"),
                bookedOn(DATE.plusDays(2), "10.00")));

    assertThat(DayClosingBalance.on(statement, DATE, TALLINN)).contains(new BigDecimal("1200.00"));
    assertThat(DayClosingBalance.on(statement, DATE.plusDays(1), TALLINN))
        .contains(new BigDecimal("1150.00"));
    assertThat(DayClosingBalance.on(statement, DATE.plusDays(2), TALLINN))
        .contains(new BigDecimal("1160.00"));
  }

  @Test
  void aDayWithoutBookingsKeepsThePreviousDaysBalance() {
    var statement =
        catchUpStatement(
            List.of(balance(OPEN, "1000.00"), balance(CLOSE, "1210.00")),
            List.of(bookedOn(DATE, "200.00"), bookedOn(DATE.plusDays(2), "10.00")));

    assertThat(DayClosingBalance.on(statement, DATE.plusDays(1), TALLINN))
        .contains(new BigDecimal("1200.00"));
  }

  @Test
  void countsOnlyBookedEntriesSoPendingOnesThatCancelOutCannotMoveADay() {
    var statement =
        catchUpStatement(
            List.of(balance(OPEN, "1000.00"), balance(CLOSE, "1000.00")),
            List.of(pendingOn(DATE, "200.00"), pendingOn(DATE.plusDays(2), "-200.00")));

    assertThat(DayClosingBalance.on(statement, DATE, TALLINN)).contains(new BigDecimal("1000.00"));
  }

  @Test
  void isAbsentWhenTheBankSentOnlyOnePageOfTheStatement() {
    var firstPage =
        catchUpStatement(
                List.of(balance(OPEN, "1000.00"), balance(CLOSE, "1000.00")),
                List.of(bookedOn(DATE, "200.00"), bookedOn(DATE.plusDays(2), "-200.00")))
            .withMorePagesToFollow();

    assertThat(DayClosingBalance.on(firstPage, DATE, TALLINN)).isEmpty();
  }

  @Test
  void isAbsentOutsideTheStatementPeriod() {
    var statement =
        catchUpStatement(
            List.of(balance(OPEN, "1000.00"), balance(CLOSE, "1200.00")),
            List.of(bookedOn(DATE, "200.00")));

    assertThat(DayClosingBalance.on(statement, DATE.minusDays(1), TALLINN)).isEmpty();
    assertThat(DayClosingBalance.on(statement, DATE.plusDays(3), TALLINN)).isEmpty();
  }

  @Test
  void isAbsentWithoutAnOpeningBalance() {
    var statement =
        catchUpStatement(List.of(balance(CLOSE, "1200.00")), List.of(bookedOn(DATE, "200.00")));

    assertThat(DayClosingBalance.on(statement, DATE, TALLINN)).isEmpty();
  }

  @Test
  void isAbsentWithoutAClosingBalance() {
    var statement =
        catchUpStatement(List.of(balance(OPEN, "1000.00")), List.of(bookedOn(DATE, "200.00")));

    assertThat(DayClosingBalance.on(statement, DATE, TALLINN)).isEmpty();
  }

  @Test
  void isAbsentWhenABookingHasNoDate() {
    var statement =
        catchUpStatement(
            List.of(balance(OPEN, "1000.00"), balance(CLOSE, "1000.00")),
            List.of(undatedBooking("200.00"), undatedBooking("-200.00")));

    assertThat(DayClosingBalance.on(statement, DATE, TALLINN)).isEmpty();
  }

  @Test
  void isAbsentWhenABookingIsDatedOutsideTheStatementPeriod() {
    var statement =
        catchUpStatement(
            List.of(balance(OPEN, "1000.00"), balance(CLOSE, "1150.00")),
            List.of(bookedOn(DATE, "200.00"), bookedOn(DATE.plusDays(3), "-50.00")));

    assertThat(DayClosingBalance.on(statement, DATE, TALLINN)).isEmpty();
  }

  @Test
  void isAbsentWhenTheBookingsDoNotAddUpToTheClosingBalance() {
    var statement =
        catchUpStatement(
            List.of(balance(OPEN, "1000.00"), balance(CLOSE, "1300.00")),
            List.of(bookedOn(DATE, "200.00")));

    assertThat(DayClosingBalance.on(statement, DATE, TALLINN)).isEmpty();
  }

  private static BankStatement catchUpStatement(
      List<BankStatementBalance> balances, List<BankStatementEntry> entries) {
    return new BankStatement(
        HISTORIC_STATEMENT,
        new BankStatementAccount("EE001234567890123456", "Acme OÜ", "10060701"),
        balances,
        entries,
        new StatementPeriod(DATE, DATE.plusDays(2)));
  }

  private static BankStatementEntry bookedOn(LocalDate bookingDate, String amount) {
    return entry(amount, endOf(bookingDate), BOOK);
  }

  private static BankStatementEntry pendingOn(LocalDate bookingDate, String amount) {
    return entry(amount, endOf(bookingDate), PDNG);
  }

  private static BankStatementEntry undatedBooking(String amount) {
    return entry(amount, null, BOOK);
  }

  private static BankStatementEntry entry(
      String amount, @Nullable Instant bookedBy, EntryStatus2Code status) {
    var value = new BigDecimal(amount);
    return new BankStatementEntry(
        null,
        value,
        "EUR",
        value.signum() < 0 ? TransactionType.DEBIT : TransactionType.CREDIT,
        "ref",
        "ext-" + bookedBy,
        null,
        null,
        bookedBy,
        status == BOOK);
  }

  private static Instant endOf(LocalDate day) {
    return day.atTime(LocalTime.MAX).atZone(TALLINN).toInstant().truncatedTo(MICROS);
  }

  private static BankStatementBalance balance(
      BankStatementBalance.StatementBalanceType type, String amount) {
    return new BankStatementBalance(type, DATE, new BigDecimal(amount));
  }
}
