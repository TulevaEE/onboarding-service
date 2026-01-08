package ee.tuleva.onboarding.investment.position.parser;

import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.FundPosition;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SwedbankFundPositionParserTest {

  private final SwedbankFundPositionParser parser = new SwedbankFundPositionParser();

  private static final String HEADER =
      "ReportDate;NAVDate;Portfolio;AssetType;FundCurr;ISIN;AssetName;Quantity;AssetCurr;PricePC;PriceQC;BookCostPC;BookCostQC;BookPriceQC;ValuationPC;MarketValuePC;ValuationQC;InterestPC;InterestQC; ;GainPC;GainQC;PriceEffect;FxEffect;InstrumentType;PrctNav;IssuerName;TNA;pGroupCode;MaturityDate;FxRate;Security_ID;Detailed Asset Type;Trade ID;Trade Date;ClassCode";

  @Test
  @DisplayName("parse_parsesEquityRow")
  void parse_parsesEquityRow() {
    String csv =
        HEADER
            + "\n"
            + "06.01.2026;05.01.2026;Tuleva Maailma Aktsiate Pensionifond;Equities;EUR;IE00BFG1TM61;ISHARES DEVELOPED WLD ESG SC I;7819299.01;EUR;33.944;33.944;178294725.4;178294725.4;22.8;265418285.6;265418285.6;265418285.6;0;0;;87123560.22;87123560.22;87123560.22;0;Equity Fund;28.853;Blackrock Asset Management Ire;0;18;;1;BDWTEIA;Equity Fund;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(1);

    FundPosition position = positions.getFirst();
    assertThat(position.getReportingDate()).isEqualTo(LocalDate.of(2026, 1, 5));
    assertThat(position.getFundCode()).isEqualTo("TUK75");
    assertThat(position.getAccountType()).isEqualTo(SECURITY);
    assertThat(position.getAccountName()).isEqualTo("ISHARES DEVELOPED WLD ESG SC I");
    assertThat(position.getAccountId()).isEqualTo("IE00BFG1TM61");
    assertThat(position.getQuantity()).isEqualByComparingTo(new BigDecimal("7819299.01"));
    assertThat(position.getMarketPrice()).isEqualByComparingTo(new BigDecimal("33.944"));
    assertThat(position.getCurrency()).isEqualTo("EUR");
    assertThat(position.getMarketValue()).isEqualByComparingTo(new BigDecimal("265418285.6"));
  }

  @Test
  @DisplayName("parse_parsesCashRow")
  void parse_parsesCashRow() {
    String csv =
        HEADER
            + "\n"
            + "06.01.2026;05.01.2026;Tuleva Maailma Aktsiate Pensionifond;Cash & Cash Equiv;EUR;;ULEOODEPOSIIT-SWEDBANK(EUR) .99% Due 06.01.2026;13303338.79;EUR;;;13303338.79;13303338.79;1;13303338.79;13303703.89;13303338.79;365.1;365.1;;0;0;0;0;Money Market;1.446;SWEDBANK AS;0;18;;1;MM-HANSA-ONEUR;Overnight Deposit;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(1);

    FundPosition position = positions.getFirst();
    assertThat(position.getFundCode()).isEqualTo("TUK75");
    assertThat(position.getAccountType()).isEqualTo(CASH);
    assertThat(position.getAccountName())
        .isEqualTo("ULEOODEPOSIIT-SWEDBANK(EUR) .99% Due 06.01.2026");
    assertThat(position.getAccountId()).isNull();
    assertThat(position.getQuantity()).isEqualByComparingTo(new BigDecimal("13303338.79"));
    assertThat(position.getMarketValue()).isEqualByComparingTo(new BigDecimal("13303703.89"));
  }

  @Test
  @DisplayName("parse_parsesFixedIncomeRow")
  void parse_parsesFixedIncomeRow() {
    String csv =
        HEADER
            + "\n"
            + "06.01.2026;05.01.2026;Tuleva Maailma Volakirjade Pensionifond;Fixed Income;EUR;IE0005032192;ISHS EURO CREDIT BOND INDEX FD;121790.7;EUR;23.345;23.345;2844755.99;2844755.99;23.36;2843203.91;2843203.91;2843203.91;0;0;;-1552.08;-1552.08;-1552.08;0;Fixed Income Fund;24.72;Blackrock Asset Management Ire;0;18;;1;BAREUBD;Fixed Income Fund;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(1);
    assertThat(positions.getFirst().getFundCode()).isEqualTo("TUK00");
    assertThat(positions.getFirst().getAccountType()).isEqualTo(SECURITY);
    assertThat(positions.getFirst().getAccountId()).isEqualTo("IE0005032192");
  }

  @Test
  @DisplayName("parse_parsesThirdPillarFund")
  void parse_parsesThirdPillarFund() {
    String csv =
        HEADER
            + "\n"
            + "06.01.2026;05.01.2026;Tuleva Vabatahtlik Pensionifon;Equities;EUR;IE0009FT4LX4;BLACKROCK CCF DEV WRLD ESG IDX;8659561.903;EUR;15.37;15.37;100128802;100128802;11.56;133097466.4;133097466.4;133097466.4;0;0;;32968664.46;32968664.46;32968664.46;0;Equity Fund;28.444;BlackRock Advisors UK Ltd;0;18;;1;BLESIXE;Equity Fund;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(1);
    assertThat(positions.getFirst().getFundCode()).isEqualTo("TUV100");
  }

  @Test
  void parse_parsesLiabilities() {
    String csv =
        HEADER
            + "\n"
            + "06.01.2026;05.01.2026;Tuleva Maailma Aktsiate Pensionifond;Liabilities;EUR;;Management Fee Payable;-190567.45;EUR;0;0;0;0;0;-190567.45;-190567.45;-190567.45;0;0;;0;0;0;0;Liabilities;-0.021;;0;18;;1;;;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(1);
    FundPosition position = positions.getFirst();
    assertThat(position.getFundCode()).isEqualTo("TUK75");
    assertThat(position.getAccountType()).isEqualTo(LIABILITY);
    assertThat(position.getAccountName()).isEqualTo("Management Fee Payable");
    assertThat(position.getQuantity()).isEqualByComparingTo(new BigDecimal("-190567.45"));
    assertThat(position.getMarketValue()).isEqualByComparingTo(new BigDecimal("-190567.45"));
  }

  @Test
  void parse_parsesReceivables() {
    String csv =
        HEADER
            + "\n"
            + "06.01.2026;05.01.2026;Tuleva Maailma Aktsiate Pensionifond;Asset;EUR;;Other receivables;49371.68;EUR;0;0;0;0;0;49371.68;49371.68;49371.68;0;0;;0;0;0;0;Asset;0.005;;0;18;;1;;;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(1);
    FundPosition position = positions.getFirst();
    assertThat(position.getFundCode()).isEqualTo("TUK75");
    assertThat(position.getAccountType()).isEqualTo(RECEIVABLES);
    assertThat(position.getAccountName()).isEqualTo("Other receivables");
    assertThat(position.getQuantity()).isEqualByComparingTo(new BigDecimal("49371.68"));
    assertThat(position.getMarketValue()).isEqualByComparingTo(new BigDecimal("49371.68"));
  }

  @Test
  void parse_parsesNav() {
    String csv =
        HEADER
            + "\n"
            + "06.01.2026;05.01.2026;Tuleva Maailma Aktsiate Pensionifond;TotalNetAsset;EUR;;;0;;0;0;0;0;;0;;0;0;0;;0;0;0;0;TotalNetAsset;0;;919901794.7;18;;;;;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(1);
    FundPosition position = positions.getFirst();
    assertThat(position.getFundCode()).isEqualTo("TUK75");
    assertThat(position.getAccountType()).isEqualTo(NAV);
    assertThat(position.getAccountName()).isEmpty();
    assertThat(position.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  @DisplayName("parse_parsesMultipleRows")
  void parse_parsesMultipleRows() {
    String csv =
        HEADER
            + "\n"
            + "06.01.2026;05.01.2026;Tuleva Maailma Aktsiate Pensionifond;Equities;EUR;IE00BFG1TM61;Asset 1;1000;EUR;10;10;0;0;0;0;10000;0;0;0;;0;0;0;0;Equity Fund;0;;0;18;;1;;;;;\n"
            + "06.01.2026;05.01.2026;Tuleva Maailma Volakirjade Pensionifond;Fixed Income;EUR;IE0005032192;Asset 2;2000;EUR;20;20;0;0;0;0;40000;0;0;0;;0;0;0;0;Fixed Income Fund;0;;0;18;;1;;;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(2);
    assertThat(positions.get(0).getFundCode()).isEqualTo("TUK75");
    assertThat(positions.get(1).getFundCode()).isEqualTo("TUK00");
  }

  @Test
  @DisplayName("parse_skipsLineWithInsufficientColumns")
  void parse_skipsLineWithInsufficientColumns() {
    String csv = HEADER + "\n" + "06.01.2026;05.01.2026;Tuleva";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).isEmpty();
  }

  @Test
  @DisplayName("parse_skipsLineWithInvalidDate")
  void parse_skipsLineWithInvalidDate() {
    String csv =
        HEADER
            + "\n"
            + "INVALID;INVALID;Tuleva Maailma Aktsiate Pensionifond;Equities;EUR;IE00BFG1TM61;Asset 1;1000;EUR;10;10;0;0;0;0;10000;0;0;0;;0;0;0;0;Equity Fund;0;;0;18;;1;;;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).isEmpty();
  }

  @Test
  @DisplayName("parse_skipsUnknownPortfolio")
  void parse_skipsUnknownPortfolio() {
    String csv =
        HEADER
            + "\n"
            + "06.01.2026;05.01.2026;Unknown Fund;Equities;EUR;IE00BFG1TM61;Asset 1;1000;EUR;10;10;0;0;0;0;10000;0;0;0;;0;0;0;0;Equity Fund;0;;0;18;;1;;;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).isEmpty();
  }

  private ByteArrayInputStream toInputStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
