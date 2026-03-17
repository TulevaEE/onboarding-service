package ee.tuleva.onboarding.savings.fund.nav;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class NavReportCsvGeneratorTest {

  private final NavReportCsvGenerator generator = new NavReportCsvGenerator();

  @Test
  void generatesCorrectCsvFromRows() {
    var navDate = LocalDate.of(2026, 3, 13);
    var rows =
        List.of(
            NavReportRow.builder()
                .navDate(navDate)
                .fundCode("TKF100")
                .accountType("SECURITY")
                .accountName("iShares Developed World Screened Index Fund")
                .accountId("IE00BFG1TM61")
                .quantity(new BigDecimal("38755.69"))
                .marketPrice(new BigDecimal("33.6226"))
                .marketValue(new BigDecimal("1303067.06"))
                .build(),
            NavReportRow.builder()
                .navDate(navDate)
                .fundCode("TKF100")
                .accountType("CASH")
                .accountName("Cash account in SEB Pank")
                .accountId("EE0000003283")
                .quantity(new BigDecimal("370794.18"))
                .marketPrice(new BigDecimal("1.00"))
                .marketValue(new BigDecimal("370794.18"))
                .build(),
            NavReportRow.builder()
                .navDate(navDate)
                .fundCode("TKF100")
                .accountType("UNITS")
                .accountName("Total outstanding units:")
                .quantity(new BigDecimal("7050814.517"))
                .marketPrice(new BigDecimal("0.9792"))
                .marketValue(new BigDecimal("6903990.38"))
                .build(),
            NavReportRow.builder()
                .navDate(navDate)
                .fundCode("TKF100")
                .accountType("NAV")
                .accountName("Net Asset Value")
                .quantity(new BigDecimal("1.00"))
                .marketPrice(new BigDecimal("0.9792"))
                .marketValue(new BigDecimal("0.9792"))
                .build());

    var csvBytes = generator.generate(rows);
    var csv = new String(csvBytes, UTF_8);

    // Remove BOM for assertion
    if (csv.startsWith("\uFEFF")) {
      csv = csv.substring(1);
    }

    var lines = csv.strip().split("\r?\n");
    assertThat(lines[0])
        .isEqualTo(
            "nav_date,fund_code,account_type,account_name,account_id,quantity,market_price,currency,market_value");
    assertThat(lines[1])
        .isEqualTo(
            "2026-03-13,TKF100,SECURITY,iShares Developed World Screened Index Fund,IE00BFG1TM61,38755.69,33.6226,EUR,1303067.06");
    assertThat(lines[2])
        .isEqualTo(
            "2026-03-13,TKF100,CASH,Cash account in SEB Pank,EE0000003283,370794.18,1.00,EUR,370794.18");
    assertThat(lines[3])
        .isEqualTo(
            "2026-03-13,TKF100,UNITS,Total outstanding units:,,7050814.517,0.9792,EUR,6903990.38");
    assertThat(lines[4]).isEqualTo("2026-03-13,TKF100,NAV,Net Asset Value,,1.00,0.9792,EUR,0.9792");
  }
}
