package ee.tuleva.onboarding.savings.fund.report;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrusteeReportCsvGeneratorTest {

  TrusteeReportCsvGenerator generator = new TrusteeReportCsvGenerator();

  @Test
  void generatesCsvMatchingExpectedFormat() {
    var rows =
        List.of(
            TrusteeReportRow.builder()
                .reportDate(LocalDate.of(2026, 2, 4))
                .nav(new BigDecimal("0.9961"))
                .issuedUnits(new BigDecimal("70981.829"))
                .issuedAmount(new BigDecimal("70705.00"))
                .redeemedUnits(new BigDecimal("500.000"))
                .redeemedAmount(new BigDecimal("498.05"))
                .totalOutstandingUnits(new BigDecimal("5860107.807"))
                .build(),
            TrusteeReportRow.builder()
                .reportDate(LocalDate.of(2026, 2, 3))
                .nav(new BigDecimal("1.0004"))
                .issuedUnits(new BigDecimal("488855.458"))
                .issuedAmount(new BigDecimal("489051.00"))
                .redeemedUnits(new BigDecimal("0.000"))
                .redeemedAmount(new BigDecimal("0.00"))
                .totalOutstandingUnits(new BigDecimal("5789625.978"))
                .build());

    byte[] csv = generator.generate(rows);

    String content = new String(csv, UTF_8);
    String[] lines = content.split("\r\n");

    assertThat(lines[0])
        .isEqualTo(
            "\uFEFF"
                + "Kuupäev,NAV,Väljalastud osakute kogus,Väljalastud osakute summa,"
                + "Tagasivõetud osakute kogus,Tagasivõetud osakute summa,"
                + "Fondi väljalastud osakute arv");
    assertThat(lines[1])
        .isEqualTo(
            "04.02.2026,\"0,9961\",\"70981,829\",\"70705,00\",\"500,000\",\"498,05\",\"5860107,807\"");
    assertThat(lines[2])
        .isEqualTo(
            "03.02.2026,\"1,0004\",\"488855,458\",\"489051,00\",\"0,000\",\"0,00\",\"5789625,978\"");
  }

  @Test
  void generatesCsvWithUtf8Bom() {
    var rows =
        List.of(
            TrusteeReportRow.builder()
                .reportDate(LocalDate.of(2026, 2, 3))
                .nav(ONE)
                .issuedUnits(ZERO)
                .issuedAmount(ZERO)
                .redeemedUnits(ZERO)
                .redeemedAmount(ZERO)
                .totalOutstandingUnits(ZERO)
                .build());

    byte[] csv = generator.generate(rows);

    assertThat(csv[0]).isEqualTo((byte) 0xEF);
    assertThat(csv[1]).isEqualTo((byte) 0xBB);
    assertThat(csv[2]).isEqualTo((byte) 0xBF);
  }

  @Test
  void generatesEmptyCsvWithHeaderOnly() {
    byte[] csv = generator.generate(List.of());

    String content = new String(csv, UTF_8);
    String[] lines = content.split("\r\n");

    assertThat(lines).hasSize(1);
    assertThat(lines[0]).startsWith("\uFEFFKuupäev,");
  }
}
