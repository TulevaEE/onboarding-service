package ee.tuleva.onboarding.accounting.directo;

import static ee.tuleva.onboarding.accounting.directo.DirectoFields.codes;
import static ee.tuleva.onboarding.accounting.directo.DirectoFields.require;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.UNNECESSARY;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

record DocumentRow(
    String accountCode,
    BigDecimal amount,
    LocalDate date,
    List<String> objectCodes,
    List<String> projectCodes) {

  static DocumentRow of(DirectoRow row, LocalDate documentDate) {
    var rowDate = row.date();
    return new DocumentRow(
        require(row.account(), "rows.account"),
        amount(row),
        rowDate == null || rowDate.isBlank()
            ? documentDate
            : DirectoFields.date(rowDate, "rows.date"),
        Stream.of(row.object(), row.supplier(), row.customer())
            .flatMap(commaSeparated -> codes(commaSeparated).stream())
            .toList(),
        codes(row.project()));
  }

  Stream<String> dimensionCodes() {
    return Stream.concat(objectCodes.stream(), projectCodes.stream());
  }

  private static BigDecimal amount(DirectoRow row) {
    var debit = row.debit();
    var credit = row.credit();
    try {
      return (debit == null ? ZERO : debit)
          .subtract(credit == null ? ZERO : credit)
          .setScale(2, UNNECESSARY);
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException(
          "Directo amount has a fraction of a cent: field=rows.amount");
    }
  }
}
