package ee.tuleva.onboarding.investment.position.parser;

import static ee.tuleva.onboarding.fund.TulevaFund.*;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static java.math.BigDecimal.ONE;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.report.CsvToJsonConverter;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SebFundPositionParserTest {

  private final SebFundPositionParser parser = new SebFundPositionParser(Clock.systemUTC());
  private final CsvToJsonConverter csvConverter = new CsvToJsonConverter();
  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 1, 26);
  private static final LocalDate NAV_DATE = LocalDate.of(2026, 1, 25);

  @Test
  void parse_parsesCashRow() {
    List<Map<String, Object>> rawData =
        List.of(
            Map.of(
                "Client name", "TKF100",
                "Account", "EE861010220306591229",
                "Name", "Cash account in SEB Pank",
                "Quantity", new BigDecimal("5302814.90"),
                "Market price", new BigDecimal("1.000"),
                "Currency", "EUR",
                "Market Value (EUR)", new BigDecimal("5302814.90")));

    List<FundPosition> positions = parser.parse(rawData, REPORT_DATE);

    assertThat(positions).hasSize(1);

    FundPosition position = positions.getFirst();
    assertThat(position.getNavDate()).isEqualTo(REPORT_DATE);
    assertThat(position.getReportDate()).isEqualTo(REPORT_DATE);
    assertThat(position.getFund()).isEqualTo(TKF100);
    assertThat(position.getAccountType()).isEqualTo(CASH);
    assertThat(position.getAccountName()).isEqualTo("Cash account in SEB Pank");
    assertThat(position.getAccountId()).isNull();
    assertThat(position.getQuantity()).isEqualByComparingTo(new BigDecimal("5302814.90"));
    assertThat(position.getMarketPrice()).isEqualByComparingTo(ONE);
    assertThat(position.getCurrency()).isEqualTo("EUR");
    assertThat(position.getMarketValue()).isEqualByComparingTo(new BigDecimal("5302814.90"));
  }

  @Test
  void parse_extractsDatesFromHeaders() {
    Map<String, Object> sentRow = new HashMap<>();
    sentRow.put("Tuleva Fondid AS", "2026-01-26");
    sentRow.put("Fund Management Company:", "Sent:");

    Map<String, Object> asOfRow = new HashMap<>();
    asOfRow.put("Tuleva Fondid AS", "2026-01-25");
    asOfRow.put("Fund Management Company:", "As of:");

    List<Map<String, Object>> rawData =
        List.of(
            sentRow,
            asOfRow,
            Map.of(
                "Client name", "TKF100",
                "Account", "EE861010220306591229",
                "Name", "Cash account in SEB Pank",
                "Quantity", new BigDecimal("5302814.90"),
                "Market price", new BigDecimal("1.000"),
                "Currency", "EUR",
                "Market Value (EUR)", new BigDecimal("5302814.90")));

    List<FundPosition> positions = parser.parse(rawData, null);

    assertThat(positions).hasSize(1);
    FundPosition position = positions.getFirst();
    assertThat(position.getNavDate()).isEqualTo(NAV_DATE);
    assertThat(position.getReportDate()).isEqualTo(REPORT_DATE);
  }

  @Test
  void parse_parsesSecurityRow() {
    List<Map<String, Object>> rawData =
        List.of(
            Map.of(
                "Client name", "TKF100",
                "Account", "VP68168",
                "ISIN", "IE00BMDBMY19",
                "Name", "Invesco MSCI Emerging Markets Universal Screened UCITS ETF Acc",
                "Quantity", new BigDecimal("15000.523"),
                "Market price", new BigDecimal("35.258"),
                "Currency", "EUR",
                "Market Value (EUR)", new BigDecimal("528888.44")));

    List<FundPosition> positions = parser.parse(rawData, REPORT_DATE);

    assertThat(positions).hasSize(1);
    FundPosition position = positions.getFirst();
    assertThat(position.getFund()).isEqualTo(TKF100);
    assertThat(position.getAccountType()).isEqualTo(SECURITY);
    assertThat(position.getAccountName())
        .isEqualTo("Invesco MSCI Emerging Markets Universal Screened UCITS ETF Acc");
    assertThat(position.getAccountId()).isEqualTo("IE00BMDBMY19");
    assertThat(position.getQuantity()).isEqualByComparingTo(new BigDecimal("15000.523"));
    assertThat(position.getMarketPrice()).isEqualByComparingTo(new BigDecimal("35.258"));
    assertThat(position.getMarketValue()).isEqualByComparingTo(new BigDecimal("528888.44"));
  }

  @Test
  void parse_parsesReceivablesRow() {
    List<Map<String, Object>> rawData =
        List.of(
            Map.of(
                "Client name", "TKF100",
                "Account", "EE861010220306591229",
                "ISIN", "EE0000003283",
                "Name", "Receivables of outstanding units",
                "Market Value (EUR)", new BigDecimal("0.00")));

    List<FundPosition> positions = parser.parse(rawData, REPORT_DATE);

    assertThat(positions).hasSize(1);
    FundPosition position = positions.getFirst();
    assertThat(position.getFund()).isEqualTo(TKF100);
    assertThat(position.getAccountType()).isEqualTo(RECEIVABLES);
    assertThat(position.getAccountName()).isEqualTo("Receivables of outstanding units");
    assertThat(position.getAccountId()).isEqualTo("EE0000003283");
  }

  @Test
  void parse_parsesPayablesAsLiability() {
    List<Map<String, Object>> rawData =
        List.of(
            Map.of(
                "Client name", "TKF100",
                "Account", "EE861010220306591229",
                "ISIN", "EE0000003283",
                "Name", "Payables of redeemed units",
                "Market Value (EUR)", new BigDecimal("0.00")));

    List<FundPosition> positions = parser.parse(rawData, REPORT_DATE);

    assertThat(positions).hasSize(1);
    FundPosition position = positions.getFirst();
    assertThat(position.getAccountType()).isEqualTo(LIABILITY);
    assertThat(position.getAccountName()).isEqualTo("Payables of redeemed units");
  }

  @Test
  void parse_parsesRegisterRowAsUnits() {
    List<Map<String, Object>> rawData =
        List.of(
            Map.of(
                "Client name", "TKF100",
                "Account", "Register",
                "ISIN", "EE0000003283",
                "Name", "Total outstanding units:",
                "Quantity", new BigDecimal("219655461.600")));

    List<FundPosition> positions = parser.parse(rawData, REPORT_DATE);

    assertThat(positions).hasSize(1);
    FundPosition position = positions.getFirst();
    assertThat(position.getFund()).isEqualTo(TKF100);
    assertThat(position.getAccountType()).isEqualTo(UNITS);
    assertThat(position.getAccountName()).isEqualTo("Total outstanding units:");
    assertThat(position.getQuantity()).isEqualByComparingTo(new BigDecimal("219655461.600"));
  }

  @Test
  void parse_skipsTotalRow() {
    List<Map<String, Object>> rawData =
        List.of(
            Map.of(
                "Client name", "TKF100",
                "Account", "Total",
                "Market Value (EUR)", new BigDecimal("59801848.29")));

    List<FundPosition> positions = parser.parse(rawData, REPORT_DATE);

    assertThat(positions).isEmpty();
  }

  @Test
  void parse_parsesAllFunds() {
    List<Map<String, Object>> rawData =
        List.of(
            createDataRow("TKF100", "Cash account in SEB Pank", "1000.00"),
            createDataRow("TUV100", "Cash account in SEB Pank", "2000.00"),
            createDataRow("TUK75", "Cash account in SEB Pank", "3000.00"),
            createDataRow("TUK00", "Cash account in SEB Pank", "4000.00"));

    List<FundPosition> positions = parser.parse(rawData, REPORT_DATE);

    assertThat(positions).hasSize(4);
    assertThat(positions.get(0).getFund()).isEqualTo(TKF100);
    assertThat(positions.get(1).getFund()).isEqualTo(TUV100);
    assertThat(positions.get(2).getFund()).isEqualTo(TUK75);
    assertThat(positions.get(3).getFund()).isEqualTo(TUK00);
  }

  @Test
  void parse_fallsBackToReportDateWhenNoHeaders() {
    List<Map<String, Object>> rawData =
        List.of(
            Map.of(
                "Client name", "TKF100",
                "Account", "EE861010220306591229",
                "Name", "Cash account",
                "Quantity", new BigDecimal("1000"),
                "Currency", "EUR",
                "Market Value (EUR)", new BigDecimal("1000")));

    List<FundPosition> positions = parser.parse(rawData, REPORT_DATE);

    assertThat(positions).hasSize(1);
    FundPosition position = positions.getFirst();
    assertThat(position.getNavDate()).isEqualTo(REPORT_DATE);
    assertThat(position.getReportDate()).isEqualTo(REPORT_DATE);
  }

  @Test
  void parse_returnsEmptyListForEmptyInput() {
    List<FundPosition> positions = parser.parse(List.of(), REPORT_DATE);

    assertThat(positions).isEmpty();
  }

  @Test
  void parse_skipsRowsWithoutName() {
    List<Map<String, Object>> rawData =
        List.of(
            Map.of(
                "Client name", "TKF100",
                "Account", "EE861010220306591229",
                "Quantity", new BigDecimal("1000"),
                "Currency", "EUR"));

    List<FundPosition> positions = parser.parse(rawData, REPORT_DATE);

    assertThat(positions).isEmpty();
  }

  @Test
  void parse_parsesCsvWithProperFormat() {
    String csv =
        "Client name;Account;ISIN;Name;Quantity;Market price;Currency;Market Value (EUR)\n"
            + "TKF100;EE861010220306591229;;Cash account in SEB Pank;24826773,530;1,000;EUR;24826773,53\n"
            + "TKF100;VP68168;IE00BMDBMY19;Invesco MSCI ETF;15000,523;35,258;EUR;528888,44";

    List<FundPosition> positions = parser.parse(toJson(csv), REPORT_DATE);

    assertThat(positions).hasSize(2);
    assertThat(positions.get(0).getFund()).isEqualTo(TKF100);
    assertThat(positions.get(0).getAccountType()).isEqualTo(CASH);
    assertThat(positions.get(0).getQuantity()).isEqualByComparingTo(new BigDecimal("24826773.530"));
    assertThat(positions.get(1).getAccountType()).isEqualTo(SECURITY);
    assertThat(positions.get(1).getAccountId()).isEqualTo("IE00BMDBMY19");
  }

  private Map<String, Object> createDataRow(String fundCode, String name, String marketValue) {
    return Map.of(
        "Client name",
        fundCode,
        "Account",
        "EE861010220306591229",
        "Name",
        name,
        "Quantity",
        new BigDecimal(marketValue),
        "Market price",
        new BigDecimal("1.000"),
        "Currency",
        "EUR",
        "Market Value (EUR)",
        new BigDecimal(marketValue));
  }

  private List<Map<String, Object>> toJson(String csv) {
    return csvConverter.convert(
        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), ';');
  }
}
