package ee.tuleva.onboarding.investment.position.parser;

import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.FundPosition;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SwedbankFundPositionParserTest {

  private final SwedbankFundPositionParser parser = new SwedbankFundPositionParser();

  private static final String HEADER =
      "reporting_date\tfund_code\taccount_type\taccount_name\taccount_id\tquantity\tmarket_price\tcurrency\tmarket_value";

  @Test
  void parse_parsesSecurityRow() {
    String csv =
        HEADER
            + "\n"
            + "2025-11-13\tTUV100\tSECURITY\tiShares Developed World Screened Index Fund\tIE00BFG1TM61\t4001615.5905\t31.7300\tEUR\t126971262.69";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(1);

    FundPosition position = positions.getFirst();
    assertThat(position.getReportingDate()).isEqualTo(LocalDate.of(2025, 11, 13));
    assertThat(position.getFundCode()).isEqualTo("TUV100");
    assertThat(position.getAccountType()).isEqualTo(SECURITY);
    assertThat(position.getAccountName()).isEqualTo("iShares Developed World Screened Index Fund");
    assertThat(position.getAccountId()).isEqualTo("IE00BFG1TM61");
    assertThat(position.getQuantity()).isEqualByComparingTo(new BigDecimal("4001615.5905"));
    assertThat(position.getMarketPrice()).isEqualByComparingTo(new BigDecimal("31.7300"));
    assertThat(position.getCurrency()).isEqualTo("EUR");
    assertThat(position.getMarketValue()).isEqualByComparingTo(new BigDecimal("126971262.69"));
  }

  @Test
  void parse_parsesCashRow() {
    String csv =
        HEADER + "\n" + "2025-11-13\tTUV100\tCASH\tCash account in SEB Pank\tRMP_KONTO_NR\t\t\t\t";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(1);

    FundPosition position = positions.getFirst();
    assertThat(position.getFundCode()).isEqualTo("TUV100");
    assertThat(position.getAccountType()).isEqualTo(CASH);
    assertThat(position.getAccountName()).isEqualTo("Cash account in SEB Pank");
    assertThat(position.getAccountId()).isEqualTo("RMP_KONTO_NR");
    assertThat(position.getQuantity()).isNull();
    assertThat(position.getMarketValue()).isNull();
  }

  @Test
  void parse_parsesNavRow() {
    String csv =
        HEADER
            + "\n"
            + "2025-11-13\tTUV100\tNAV\tNet Asset Value\tEETULEVAFOND\t1.0000\t1.6400\tEUR\t1.6400";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(1);
    assertThat(positions.getFirst().getAccountType()).isEqualTo(NAV);
    assertThat(positions.getFirst().getAccountId()).isEqualTo("EETULEVAFOND");
  }

  @Test
  void parse_parsesMultipleRows() {
    String csv =
        HEADER
            + "\n"
            + "2025-11-13\tTUV100\tSECURITY\tAsset 1\tIE00BFG1TM61\t1000\t10\tEUR\t10000\n"
            + "2025-11-13\tTUK75\tSECURITY\tAsset 2\tIE00BFG1TM62\t2000\t20\tEUR\t40000";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(2);
    assertThat(positions.get(0).getFundCode()).isEqualTo("TUV100");
    assertThat(positions.get(1).getFundCode()).isEqualTo("TUK75");
  }

  @Test
  void parse_skipsLineWithInsufficientColumns() {
    String csv = HEADER + "\n" + "2025-11-13\tTUV100\tSECURITY";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).isEmpty();
  }

  @Test
  void parse_skipsLineWithInvalidDate() {
    String csv =
        HEADER + "\n" + "INVALID\tTUV100\tSECURITY\tAsset 1\tIE00BFG1TM61\t1000\t10\tEUR\t10000";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).isEmpty();
  }

  @Test
  void parse_handlesThousandSeparator() {
    String csv =
        HEADER
            + "\n"
            + "2025-11-13\tTUV100\tSECURITY\tAsset 1\tIE00BFG1TM61\t4,001,615.5905\t31.73\tEUR\t126,971,262.69";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(1);
    assertThat(positions.getFirst().getQuantity())
        .isEqualByComparingTo(new BigDecimal("4001615.5905"));
    assertThat(positions.getFirst().getMarketValue())
        .isEqualByComparingTo(new BigDecimal("126971262.69"));
  }

  private ByteArrayInputStream toInputStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
