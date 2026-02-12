package ee.tuleva.onboarding.banking.processor;

import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TradeSettlementParserTest {

  private final TradeSettlementParser parser = new TradeSettlementParser();

  @Test
  void parse_extractsTickerAndUnitsFromRemittanceInfo() {
    var result = parser.parse("DLA0553690/EJAP GY/11704/17.864/Buy/ Euroclear, ABNCNL2AXXX, 14448");

    assertThat(result).isPresent();
    assertThat(result.get().ticker()).isEqualTo(BNP_JAPAN_ESG_FILTERED);
    assertThat(result.get().units()).isEqualByComparingTo(new BigDecimal("11704"));
  }

  @Test
  void parse_extractsAnotherTickerAndUnits() {
    var result = parser.parse("DLA0553685/XRSM GY/19422/51.25/Buy/ Euroclear, ABNCNL2AXXX, 14448");

    assertThat(result).isPresent();
    assertThat(result.get().ticker()).isEqualTo(XTRACKERS_USA_ESG_SCREENED);
    assertThat(result.get().units()).isEqualByComparingTo(new BigDecimal("19422"));
  }

  @Test
  void parse_returnsEmptyForUnknownTicker() {
    var result = parser.parse("DLA0553690/ZZZZ GY/11704/17.864/Buy/ Euroclear, ABNCNL2AXXX, 14448");

    assertThat(result).isEmpty();
  }

  @Test
  void parse_resolvesMutualFundWithDecimalUnits() {
    var result =
        parser.parse("DLA0553698/BDWTEIA ID/24.4021/32765.6/Buy/ SNORAS, AGBLLT2XXXX, 14448");

    assertThat(result).isPresent();
    assertThat(result.get().ticker()).isEqualTo(ISHARES_DEVELOPED_WORLD_ESG_SCREENED);
    assertThat(result.get().units()).isEqualByComparingTo(new BigDecimal("24.4021"));
  }

  @Test
  void parse_returnsEmptyForMalformedRemittanceInfo() {
    assertThat(parser.parse("some random text")).isEmpty();
    assertThat(parser.parse("")).isEmpty();
    assertThat(parser.parse(null)).isEmpty();
  }

  @Test
  void parse_returnsEmptyForSingleSegment() {
    assertThat(parser.parse("DLA0553690")).isEmpty();
  }
}
