package ee.tuleva.onboarding.accounting.directo;

import static ee.tuleva.onboarding.accounting.directo.DirectoFields.date;
import static ee.tuleva.onboarding.accounting.directo.DirectoFields.require;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.ledger.JournalEntryLine;
import ee.tuleva.onboarding.ledger.JournalEntryPart;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

record Document(String type, String number, List<DocumentRow> rows) {

  static Document of(DirectoTransaction transaction) {
    var documentDate = date(require(transaction.date(), "date"), "date");
    return new Document(
        require(transaction.type(), "type"),
        require(transaction.number(), "number"),
        require(transaction.rows(), "rows").stream()
            .map(row -> DocumentRow.of(row, documentDate))
            .toList());
  }

  String identity() {
    return type + ":" + number;
  }

  Stream<JournalEntryPart> parts() {
    return rows.stream()
        .collect(groupingBy(DocumentRow::date, TreeMap::new, toList()))
        .entrySet()
        .stream()
        .map(
            rowsOfADate ->
                new JournalEntryPart(
                    identity() + ":" + rowsOfADate.getKey(),
                    type,
                    rowsOfADate.getKey(),
                    nonZeroLines(rowsOfADate.getValue()),
                    Set.of()))
        .filter(part -> !part.lines().isEmpty());
  }

  Stream<String> identifyingCodes() {
    return Stream.concat(Stream.of(number), rows.stream().flatMap(DocumentRow::dimensionCodes));
  }

  private static List<JournalEntryLine> nonZeroLines(List<DocumentRow> rows) {
    return rows.stream()
        .filter(row -> row.amount().signum() != 0)
        .map(row -> new JournalEntryLine(row.accountCode(), row.amount()))
        .toList();
  }
}
