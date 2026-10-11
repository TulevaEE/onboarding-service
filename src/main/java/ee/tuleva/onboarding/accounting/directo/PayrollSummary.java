package ee.tuleva.onboarding.accounting.directo;

import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableSet;

import ee.tuleva.onboarding.ledger.JournalEntryLine;
import ee.tuleva.onboarding.ledger.JournalEntryPart;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class PayrollSummary {

  private PayrollSummary() {}

  static List<JournalEntryPart> of(List<JournalEntryPart> protectedParts) {
    return protectedParts.stream()
        .collect(groupingBy(part -> YearMonth.from(part.date()), TreeMap::new, toList()))
        .entrySet()
        .stream()
        .map(month -> summary(month.getKey(), month.getValue()))
        .filter(summary -> !summary.lines().isEmpty())
        .toList();
  }

  private static JournalEntryPart summary(YearMonth month, List<JournalEntryPart> parts) {
    final String DOCUMENT_TYPE = "PAYROLL_SUMMARY";
    return new JournalEntryPart(
        "PAYROLL:" + month,
        DOCUMENT_TYPE,
        month.atEndOfMonth(),
        linesPerAccount(parts),
        parts.stream().map(JournalEntryPart::sourceKey).collect(toUnmodifiableSet()));
  }

  private static List<JournalEntryLine> linesPerAccount(List<JournalEntryPart> parts) {
    return parts.stream()
        .flatMap(part -> part.lines().stream())
        .collect(
            groupingBy(
                JournalEntryLine::accountCode,
                TreeMap::new,
                reducing(ZERO, JournalEntryLine::amount, BigDecimal::add)))
        .entrySet()
        .stream()
        .filter(account -> account.getValue().signum() != 0)
        .map(PayrollSummary::line)
        .toList();
  }

  private static JournalEntryLine line(Map.Entry<String, BigDecimal> account) {
    return new JournalEntryLine(account.getKey(), account.getValue());
  }
}
