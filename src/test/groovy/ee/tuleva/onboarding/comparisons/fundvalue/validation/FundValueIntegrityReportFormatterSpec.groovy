package ee.tuleva.onboarding.comparisons.fundvalue.validation

import ee.tuleva.onboarding.instrument.InstrumentReference
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate

import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.instrument
import static ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.Severity

class FundValueIntegrityReportFormatterSpec extends Specification {

  static final InstrumentReference INSTRUMENT = instrument("IE00BFG1TM61")
      .displayName("iShares Developed World Screened Index Fund")
      .yahooTicker("0P000152G5.F")
      .eodhdTicker("IE00BFG1TM61.EUFUND")
      .blackrockProductId("270890")
      .morningstarId("0P000152G5")
      .build()

  private static List<IntegrityCheckResult.Discrepancy> discrepancyList(
      boolean present, Severity severity, LocalDate date) {
    if (!present) {
      return []
    }
    return [new IntegrityCheckResult.Discrepancy(
        "IWDA", date, 100.00, 99.00, 1.00, 1.0000, severity, "EODHD vs Yahoo", [])]
  }

  static final LocalDate SOURCE_STATUS_DATE = LocalDate.of(2026, 6, 10)

  @Unroll
  def "sourceStatus for #scenario"() {
    given:
    def result = new FundValueIntegrityChecker.InstrumentCheckResult(
        INSTRUMENT, configured as Set, withData as Set, stale,
        discrepancyList(hasDiscrepancy, severity, SOURCE_STATUS_DATE))

    expect:
    FundValueIntegrityReportFormatter.sourceStatus(result, sourceName, SOURCE_STATUS_DATE) == expected

    where:
    scenario                              | sourceName | configured  | withData    | stale                                                                                    | hasDiscrepancy | severity          || expected
    "not configured"                      | "Yahoo"    | []          | []          | []                                                                                       | false          | Severity.INFO     || "-"
    "configured but no data yet"          | "Yahoo"    | ["Yahoo"]   | []          | []                                                                                       | false          | Severity.INFO     || "❌"
    "configured but stale"                | "Yahoo"    | ["Yahoo"]   | ["Yahoo"]   | [new IntegrityCheckResult.StaleSource("x", "Yahoo", "k", SOURCE_STATUS_DATE, 10)] | false          | Severity.INFO     || "❌"
    "yahoo with a discrepancy"            | "Yahoo"    | ["Yahoo"]   | ["Yahoo"]   | []                                                                                       | true           | Severity.INFO     || "⚠️"
    "yahoo without a discrepancy"         | "Yahoo"    | ["Yahoo"]   | ["Yahoo"]   | []                                                                                       | false          | Severity.INFO     || "✅"
    "eodhd with critical discrepancy"     | "EODHD"    | ["EODHD"]   | ["EODHD"]   | []                                                                                       | true           | Severity.CRITICAL || "❌"
    "eodhd without critical discrepancy"  | "EODHD"    | ["EODHD"]   | ["EODHD"]   | []                                                                                       | false          | Severity.CRITICAL || "✅"
  }

  @Unroll
  def "padCrossProviderStatus pads #status to width #width"() {
    expect:
    FundValueIntegrityReportFormatter.padCrossProviderStatus(status, width) == status + (" " * (width - status.length()))

    where:
    status | width
    "✅"    | 6
    "❌"    | 6
    "⚠️"    | 6
    "ℹ️"    | 6
    "-"    | 6
  }

  def "padCrossProviderStatus does not pad a mark that already fills its column"() {
    expect:
    FundValueIntegrityReportFormatter.padCrossProviderStatus("✅", 1) == "✅"
  }

  def "buildLatestDaySummary reports only the critical issues that fall on the latest date"() {
    given:
    LocalDate latestDate = LocalDate.of(2026, 6, 10)
    LocalDate otherDate = LocalDate.of(2026, 6, 9)
    def onLatestDate = new IntegrityCheckResult.Discrepancy(
        "IWDA", latestDate, 100.00000, 90.00000, 10.00000, 10.0000, Severity.CRITICAL, "EODHD vs Yahoo", [])
    def onOtherDate = new IntegrityCheckResult.Discrepancy(
        "IWDA", otherDate, 100.00000, 90.00000, 10.00000, 10.0000, Severity.CRITICAL, "EODHD vs Yahoo", [])
    def result = new FundValueIntegrityChecker.InstrumentCheckResult(
        INSTRUMENT, [] as Set, [] as Set, [], [onLatestDate, onOtherDate])

    when:
    String summary = FundValueIntegrityReportFormatter.buildLatestDaySummary(latestDate, [result])

    then:
    summary.contains("1 critical issue(s) found")
    summary.contains("IWDA: EODHD=100.00000 vs Yahoo=90.00000 (diff: 10.0000%)")
    !summary.contains("2 critical issue(s) found")
  }

  def "buildLatestDaySummary shows only the info count line when there are no critical issues"() {
    given:
    LocalDate latestDate = LocalDate.of(2026, 6, 10)
    def infoIssue = new IntegrityCheckResult.Discrepancy(
        "IWDA", latestDate, 100.00000, 90.00000, 10.00000, 10.0000, Severity.INFO, "EODHD vs Yahoo", [])
    def result = new FundValueIntegrityChecker.InstrumentCheckResult(
        INSTRUMENT, [] as Set, [] as Set, [], [infoIssue])

    when:
    String summary = FundValueIntegrityReportFormatter.buildLatestDaySummary(latestDate, [result])

    then:
    !summary.contains("critical issue(s) found")
    summary.contains("1 expected Yahoo discrepancies (INFO - no action needed)")
  }

  def "buildLatestDaySummary shows only the critical count line when there are no info issues"() {
    given:
    LocalDate latestDate = LocalDate.of(2026, 6, 10)
    def criticalIssue = new IntegrityCheckResult.Discrepancy(
        "IWDA", latestDate, 100.00000, 90.00000, 10.00000, 10.0000, Severity.CRITICAL, "EODHD vs BlackRock", [])
    def result = new FundValueIntegrityChecker.InstrumentCheckResult(
        INSTRUMENT, [] as Set, [] as Set, [], [criticalIssue])

    when:
    String summary = FundValueIntegrityReportFormatter.buildLatestDaySummary(latestDate, [result])

    then:
    summary.contains("1 critical issue(s) found")
    !summary.contains("expected Yahoo discrepancies")
  }

  def "truncateFundName leaves a name exactly at the width limit untouched"() {
    expect:
    FundValueIntegrityReportFormatter.truncateFundName("x" * 49) == "x" * 49
  }

  def "truncateFundName shortens a name past the width limit and appends an ellipsis"() {
    expect:
    FundValueIntegrityReportFormatter.truncateFundName("x" * 50) == ("x" * 46) + "..."
  }

  def "formatCrossProviderSeparator renders a non-empty table separator row"() {
    expect:
    FundValueIntegrityReportFormatter.formatCrossProviderSeparator().startsWith("├─")
  }

  def "formatCrossProviderFooter renders a non-empty table footer row"() {
    expect:
    FundValueIntegrityReportFormatter.formatCrossProviderFooter().startsWith("└─")
  }

  def "formatAllSources lists nothing when only the two compared sources are present"() {
    given:
    def discrepancy = new IntegrityCheckResult.Discrepancy(
        "IWDA", LocalDate.of(2026, 6, 10), 100.00, 90.00, 10.00, 10.0000, Severity.CRITICAL, "EODHD vs BlackRock",
        [new IntegrityCheckResult.SourceValue("EODHD", 100.00), new IntegrityCheckResult.SourceValue("BlackRock", 90.00)])

    expect:
    FundValueIntegrityReportFormatter.formatAllSources(discrepancy) == ""
  }

  def "appendIssueDetails truncates the list and reports how many more issues were omitted"() {
    given:
    LocalDate date = LocalDate.of(2026, 6, 10)
    def issues = (1..11).collect {
      new IntegrityCheckResult.Discrepancy(
          "FUND$it", date.minusDays(it), 100.00000, 90.00000, 10.00000, 10.0000, Severity.CRITICAL, "EODHD vs BlackRock", [])
    }
    StringBuilder summary = new StringBuilder()

    when:
    FundValueIntegrityReportFormatter.appendIssueDetails(summary, issues)

    then:
    summary.toString().contains("... and 1 more")
  }

  def "appendIssueDetails omits the more-issues line when exactly 10 issues are listed"() {
    given:
    LocalDate date = LocalDate.of(2026, 6, 10)
    def issues = (1..10).collect {
      new IntegrityCheckResult.Discrepancy(
          "FUND$it", date.minusDays(it), 100.00000, 90.00000, 10.00000, 10.0000, Severity.CRITICAL, "EODHD vs BlackRock", [])
    }
    StringBuilder summary = new StringBuilder()

    when:
    FundValueIntegrityReportFormatter.appendIssueDetails(summary, issues)

    then:
    !summary.toString().contains("more")
  }
}
