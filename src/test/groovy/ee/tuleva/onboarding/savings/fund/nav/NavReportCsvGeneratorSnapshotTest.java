package ee.tuleva.onboarding.savings.fund.nav;

import static java.nio.charset.StandardCharsets.UTF_8;

import au.com.origin.snapshots.Expect;
import au.com.origin.snapshots.junit5.SnapshotExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SnapshotExtension.class)
class NavReportCsvGeneratorSnapshotTest {

  private final NavReportCsvGenerator generator = new NavReportCsvGenerator();

  private Expect expect;

  @Test
  void generatesCsvForRichNavReportWithZeroNegativeAndNullValues() {
    var navDate = LocalDate.of(2026, 3, 13);
    var fundCode = "TKF100";
    var rows =
        List.of(
            NavReportRow.builder()
                .navDate(navDate)
                .fundCode(fundCode)
                .accountType("SECURITY")
                .accountName("iShares Developed World Screened Index Fund")
                .accountId("IE00BFG1TM61")
                .quantity(new BigDecimal("38755.690"))
                .marketPrice(new BigDecimal("33.6226"))
                .marketValue(new BigDecimal("1303067.06"))
                .build(),
            NavReportRow.builder()
                .navDate(navDate)
                .fundCode(fundCode)
                .accountType("SECURITY")
                .accountName("Amundi Prime Global UCITS ETF")
                .accountId("LU1931975079")
                .quantity(new BigDecimal("0.000"))
                .marketPrice(new BigDecimal("28.4100"))
                .marketValue(new BigDecimal("0.00"))
                .build(),
            NavReportRow.builder()
                .navDate(navDate)
                .fundCode(fundCode)
                .accountType("CASH")
                .accountName("Cash account in SEB Pank")
                .accountId("EE0000003283")
                .quantity(new BigDecimal("370794.18"))
                .marketPrice(new BigDecimal("1.00"))
                .marketValue(new BigDecimal("370794.18"))
                .build(),
            NavReportRow.builder()
                .navDate(navDate)
                .fundCode(fundCode)
                .accountType("RECEIVABLES")
                .accountName("Receivables of outstanding units")
                .accountId("IE00BFG1TM61")
                .quantity(new BigDecimal("0.00"))
                .marketPrice(new BigDecimal("1.00"))
                .marketValue(new BigDecimal("0.00"))
                .build(),
            NavReportRow.builder()
                .navDate(navDate)
                .fundCode(fundCode)
                .accountType("LIABILITY")
                .accountName("Total payables of unsettled transactions")
                .accountId("IE00BFG1TM61")
                .quantity(new BigDecimal("-1899.34"))
                .marketPrice(new BigDecimal("1.00"))
                .marketValue(new BigDecimal("-1899.34"))
                .build(),
            NavReportRow.builder()
                .navDate(navDate)
                .fundCode(fundCode)
                .accountType("LIABILITY_FEE")
                .accountName("Management fee")
                .accountId("IE00BFG1TM61")
                .quantity(new BigDecimal("-850.00"))
                .marketPrice(new BigDecimal("1.00"))
                .marketValue(new BigDecimal("-850.00"))
                .build(),
            NavReportRow.builder()
                .navDate(navDate)
                .fundCode(fundCode)
                .accountType("UNITS")
                .accountName("Total outstanding units:")
                .quantity(new BigDecimal("7050814.517"))
                .marketPrice(new BigDecimal("0.9792"))
                .marketValue(new BigDecimal("6903990.38"))
                .build(),
            NavReportRow.builder()
                .navDate(navDate)
                .fundCode(fundCode)
                .accountType("NAV")
                .accountName("Net Asset Value")
                .quantity(new BigDecimal("1.00"))
                .marketPrice(new BigDecimal("0.9792"))
                .marketValue(new BigDecimal("0.9792"))
                .build(),
            NavReportRow.builder()
                .navDate(navDate)
                .fundCode(fundCode)
                .accountType("ADJUSTMENT")
                .accountName("Manual NAV adjustment pending review")
                .build());

    var csv = normalizeLineEndings(new String(generator.generate(rows), UTF_8));

    expect.toMatchSnapshot(csv);
  }

  @Test
  void generatesCsvForEmptyRowList() {
    var csv = normalizeLineEndings(new String(generator.generate(List.of()), UTF_8));

    expect.toMatchSnapshot(csv);
  }

  private static String normalizeLineEndings(String csv) {
    return csv.replace("\r\n", "\n");
  }
}
