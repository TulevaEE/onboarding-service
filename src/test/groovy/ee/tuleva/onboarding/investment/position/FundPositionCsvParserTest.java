package ee.tuleva.onboarding.investment.position;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FundPositionCsvParserTest {

  private final FundPositionCsvParser parser = new FundPositionCsvParser();

  private static final String HEADER =
      "ReportDate;NAVDate;Portfolio;AssetType;FundCurr;ISIN;AssetName;Quantity;AssetCurr;"
          + "PricePC;PriceQC;BookCostPC;BookCostQC;BookPriceQC;ValuationPC;MarketValuePC;"
          + "ValuationQC;InterestPC;InterestQC; ;GainPC;GainQC;PriceEffect;FxEffect;"
          + "InstrumentType;PrctNav;IssuerName;TNA;pGroupCode;MaturityDate;FxRate;"
          + "Security_ID;Detailed Asset Type;Trade ID;Trade Date;ClassCode";

  @Test
  void parse_parsesEquityRow() {
    String csv =
        HEADER
            + "\n"
            + "06.01.2026;05.01.2026;Tuleva Maailma Aktsiate Pensionifond;Equities;EUR;"
            + "IE00BFG1TM61;ISHARES DEVELOPED WLD ESG SC I;7819299.01;EUR;33.944;33.944;"
            + "178294725.4;178294725.4;22.8;265418285.6;265418285.6;265418285.6;0;0; ;"
            + "87123560.22;87123560.22;87123560.22;0;Equity Fund;28.853;"
            + "Blackrock Asset Management Ire;0;18;;1;BDWTEIA;Equity Fund;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(1);

    FundPosition position = positions.getFirst();
    assertThat(position.getReportDate()).isEqualTo(LocalDate.of(2026, 1, 6));
    assertThat(position.getNavDate()).isEqualTo(LocalDate.of(2026, 1, 5));
    assertThat(position.getPortfolio()).isEqualTo("Tuleva Maailma Aktsiate Pensionifond");
    assertThat(position.getFundCode()).isEqualTo("TUK75");
    assertThat(position.getAssetType()).isEqualTo("Equities");
    assertThat(position.getIsin()).isEqualTo("IE00BFG1TM61");
    assertThat(position.getAssetName()).isEqualTo("ISHARES DEVELOPED WLD ESG SC I");
    assertThat(position.getQuantity()).isEqualByComparingTo(new BigDecimal("7819299.01"));
    assertThat(position.getFundCurrency()).isEqualTo("EUR");
    assertThat(position.getAssetCurrency()).isEqualTo("EUR");
    assertThat(position.getPrice()).isEqualByComparingTo(new BigDecimal("33.944"));
    assertThat(position.getMarketValue()).isEqualByComparingTo(new BigDecimal("265418285.6"));
    assertThat(position.getPercentageOfNav()).isEqualByComparingTo(new BigDecimal("28.853"));
    assertThat(position.getIssuerName()).isEqualTo("Blackrock Asset Management Ire");
    assertThat(position.getInstrumentType()).isEqualTo("Equity Fund");
    assertThat(position.getSecurityId()).isEqualTo("BDWTEIA");
  }

  @Test
  void parse_parsesCashRow() {
    String csv =
        HEADER
            + "\n"
            + "06.01.2026;05.01.2026;Tuleva Maailma Volakirjade Pensionifond;Cash;EUR;"
            + "TULVPF              ;Swedbank AS;128027.84;EUR;0;0;0;0;0;128027.84;128027.84;"
            + "128027.84;0;0; ;0;0;0;0;Cash;1.113;Swedbank AS;0;18;;1;;Cash;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(1);

    FundPosition position = positions.getFirst();
    assertThat(position.getFundCode()).isEqualTo("TUK00");
    assertThat(position.getAssetType()).isEqualTo("Cash");
    assertThat(position.getIsin()).isEqualTo("TULVPF");
    assertThat(position.getAssetName()).isEqualTo("Swedbank AS");
  }

  @Test
  void parse_parsesFixedIncomeRow() {
    String csv =
        HEADER
            + "\n"
            + "06.01.2026;05.01.2026;Tuleva Maailma Volakirjade Pensionifond;Fixed Income;EUR;"
            + "IE0005032192;ISHS EURO CREDIT BOND INDEX FD;121790.7;EUR;23.345;23.345;"
            + "2844755.99;2844755.99;23.36;2843203.91;2843203.91;2843203.91;0;0; ;"
            + "-1552.08;-1552.08;-1552.08;0;Fixed Income Fund;24.72;"
            + "Blackrock Asset Management Ire;0;18;;1;BAREUBD;Fixed Income Fund;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(1);
    assertThat(positions.getFirst().getAssetType()).isEqualTo("Fixed Income");
    assertThat(positions.getFirst().getInstrumentType()).isEqualTo("Fixed Income Fund");
  }

  @Test
  void parse_parsesCashEquivRow() {
    String csv =
        HEADER
            + "\n"
            + "06.01.2026;05.01.2026;Tuleva Maailma Aktsiate Pensionifond;Cash & Cash Equiv;EUR;"
            + ";ULEOODEPOSIIT-SWEDBANK(EUR) .99% Due 06.01.2026;13303338.79;EUR;;;"
            + "13303338.79;13303338.79;1;13303338.79;13303703.89;13303338.79;365.1;365.1; ;"
            + "0;0;0;0;Money Market;1.446;SWEDBANK AS;0;18;;1;MM-HANSA-ONEUR;Overnight Deposit;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(1);
    assertThat(positions.getFirst().getAssetType()).isEqualTo("Cash & Cash Equiv");
    assertThat(positions.getFirst().getIsin()).isNull();
  }

  @Test
  void parse_skipsLiabilitiesRow() {
    String csv =
        HEADER
            + "\n"
            + "06.01.2026;05.01.2026;Tuleva Maailma Aktsiate Pensionifond;Liabilities;EUR;"
            + ";Management Fee Payable;-190567.45;EUR;0;0;0;0;0;-190567.45;-190567.45;"
            + "-190567.45;0;0; ;0;0;0;0;Liabilities;-0.021;;0;18;;1;;;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).isEmpty();
  }

  @Test
  void parse_skipsTotalNetAssetRow() {
    String csv =
        HEADER
            + "\n"
            + "06.01.2026;05.01.2026;Tuleva Maailma Aktsiate Pensionifond;TotalNetAsset;EUR;"
            + ";;0;;0;0;0;0;;0;;0;0;0; ;0;0;0;0;TotalNetAsset;0;;919901794.7;18;;;;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).isEmpty();
  }

  @Test
  void parse_skipsAssetRow() {
    String csv =
        HEADER
            + "\n"
            + "06.01.2026;05.01.2026;Tuleva Maailma Aktsiate Pensionifond;Asset;EUR;"
            + ";Other receivables;49371.68;EUR;0;0;0;0;0;49371.68;49371.68;49371.68;0;0; ;"
            + "0;0;0;0;Asset;0.005;;0;18;;1;;;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).isEmpty();
  }

  @Test
  void parse_parsesMultipleRows() {
    String csv =
        HEADER
            + "\n"
            + "06.01.2026;05.01.2026;Tuleva Maailma Aktsiate Pensionifond;Equities;EUR;"
            + "IE00BFG1TM61;Asset 1;1000;EUR;10;10;10000;10000;10;10000;10000;10000;0;0; ;"
            + "0;0;0;0;Equity Fund;10;Issuer;0;18;;1;SEC1;Equity Fund;;;\n"
            + "06.01.2026;05.01.2026;Tuleva Vabatahtlik Pensionifond;Equities;EUR;"
            + "IE00BFG1TM62;Asset 2;2000;EUR;20;20;40000;40000;20;40000;40000;40000;0;0; ;"
            + "0;0;0;0;Equity Fund;20;Issuer;0;18;;1;SEC2;Equity Fund;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).hasSize(2);
    assertThat(positions.get(0).getFundCode()).isEqualTo("TUK75");
    assertThat(positions.get(1).getFundCode()).isEqualTo("TUV100");
  }

  @Test
  void parse_skipsLineWithInsufficientColumns() {
    String csv =
        HEADER + "\n" + "06.01.2026;05.01.2026;Tuleva Maailma Aktsiate Pensionifond;Equities";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).isEmpty();
  }

  @Test
  void parse_skipsLineWithInvalidDate() {
    String csv =
        HEADER
            + "\n"
            + "INVALID;05.01.2026;Tuleva Maailma Aktsiate Pensionifond;Equities;EUR;"
            + "IE00BFG1TM61;Asset 1;1000;EUR;10;10;10000;10000;10;10000;10000;10000;0;0; ;"
            + "0;0;0;0;Equity Fund;10;Issuer;0;18;;1;SEC1;Equity Fund;;;";

    List<FundPosition> positions = parser.parse(toInputStream(csv));

    assertThat(positions).isEmpty();
  }

  private ByteArrayInputStream toInputStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
