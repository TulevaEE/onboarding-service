package ee.tuleva.onboarding.banking.processor;

import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.BNP_JAPAN_ESG_FILTERED;
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.XTRACKERS_USA_ESG_SCREENED;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TradeSettlementParserTest {

  private final TradeSettlementParser parser = new TradeSettlementParser();

  @Test
  void parse_extractsTickerFromRemittanceInfo() {
    var result = parser.parse("DLA0553690/EJAP GY/11704/17.864/Buy/ Euroclear, ABNCNL2AXXX, 14448");

    assertThat(result).contains(BNP_JAPAN_ESG_FILTERED);
  }

  @Test
  void parse_extractsAnotherTicker() {
    var result = parser.parse("DLA0553685/XRSM GY/19422/51.25/Buy/ Euroclear, ABNCNL2AXXX, 14448");

    assertThat(result).contains(XTRACKERS_USA_ESG_SCREENED);
  }

  @Test
  void parse_returnsEmptyForUnknownTicker() {
    var result = parser.parse("DLA0553690/ZZZZ GY/11704/17.864/Buy/ Euroclear, ABNCNL2AXXX, 14448");

    assertThat(result).isEmpty();
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
