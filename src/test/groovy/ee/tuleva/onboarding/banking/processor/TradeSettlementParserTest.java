package ee.tuleva.onboarding.banking.processor;

import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.anInstrument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.banking.processor.TradeSettlementParser.TradeSettlementInfo;
import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradeSettlementParserTest {

  private static final InstrumentReference BNP_JAPAN =
      anInstrument()
          .isin("LU1291102447")
          .displayName("BNP Paribas Easy MSCI Japan Min TE UCITS ETF")
          .yahooTicker("EJAP.DE")
          .bloombergTicker("EJAP")
          .build();

  private static final InstrumentReference XTRACKERS_USA =
      anInstrument()
          .isin("IE00BJZ2DC62")
          .displayName("Xtrackers MSCI USA Screened UCITS ETF")
          .yahooTicker("XRSM.DE")
          .bloombergTicker("XRSM")
          .build();

  private static final InstrumentReference DEVELOPED_WORLD =
      anInstrument()
          .isin("IE00BFG1TM61")
          .displayName("iShares Developed World Screened Index Fund")
          .yahooTicker("0P000152G5.F")
          .bloombergTicker("BDWTEIA")
          .build();

  private static final InstrumentReference WITHOUT_YAHOO_TICKER =
      anInstrument()
          .isin("IE00NOTICKER0")
          .displayName("Instrument without a yahoo ticker")
          .bloombergTicker("NOYAHOO")
          .build();

  @Mock private InstrumentReferenceService instrumentReferenceService;

  @InjectMocks private TradeSettlementParser parser;

  @Test
  void parse_keepsTheSebReferenceTickerUnitsAndPriceOfAnEtfTrade() {
    given(instrumentReferenceService.findByTicker("EJAP")).willReturn(Optional.of(BNP_JAPAN));

    var result = parser.parse("DLA0553690/EJAP GY/11704/17.864/Buy/ Euroclear, ABNCNL2AXXX, 14448");

    assertThat(result)
        .contains(
            new TradeSettlementInfo(
                "DLA0553690",
                "LU1291102447",
                "EJAP",
                "BNP Paribas Easy MSCI Japan Min TE UCITS ETF",
                new BigDecimal("11704"),
                new BigDecimal("17.864")));
  }

  @Test
  void parse_keepsTheSebReferenceTickerUnitsAndPriceOfAnotherEtfTrade() {
    given(instrumentReferenceService.findByTicker("XRSM")).willReturn(Optional.of(XTRACKERS_USA));

    var result = parser.parse("DLA0553685/XRSM GY/19422/51.25/Buy/ Euroclear, ABNCNL2AXXX, 14448");

    assertThat(result)
        .contains(
            new TradeSettlementInfo(
                "DLA0553685",
                "IE00BJZ2DC62",
                "XRSM",
                "Xtrackers MSCI USA Screened UCITS ETF",
                new BigDecimal("19422"),
                new BigDecimal("51.25")));
  }

  @Test
  void parse_returnsEmptyForUnknownTicker() {
    given(instrumentReferenceService.findByTicker("ZZZZ")).willReturn(Optional.empty());
    given(instrumentReferenceService.findByBloombergTicker("ZZZZ")).willReturn(Optional.empty());

    var result = parser.parse("DLA0553690/ZZZZ GY/11704/17.864/Buy/ Euroclear, ABNCNL2AXXX, 14448");

    assertThat(result).isEmpty();
  }

  @Test
  void parse_resolvesMutualFundByBloombergTickerWithDecimalUnitsAndPrice() {
    given(instrumentReferenceService.findByTicker("BDWTEIA")).willReturn(Optional.empty());
    given(instrumentReferenceService.findByBloombergTicker("BDWTEIA"))
        .willReturn(Optional.of(DEVELOPED_WORLD));

    var result =
        parser.parse(
            "DLA0544429/BDWTEIA/31426.66/34.085995776/Buy/ BlackRock Asset Management Ireland Ltd");

    assertThat(result)
        .contains(
            new TradeSettlementInfo(
                "DLA0544429",
                "IE00BFG1TM61",
                "BDWTEIA",
                "iShares Developed World Screened Index Fund",
                new BigDecimal("31426.66"),
                new BigDecimal("34.085995776")));
  }

  @Test
  void parse_settlesAnInstrumentWithNoYahooTickerUnderTheTickerSebWrote() {
    given(instrumentReferenceService.findByTicker("NOYAHOO")).willReturn(Optional.empty());
    given(instrumentReferenceService.findByBloombergTicker("NOYAHOO"))
        .willReturn(Optional.of(WITHOUT_YAHOO_TICKER));

    var result = parser.parse("DLA0553698/NOYAHOO ID/24.4021/32765.6/Buy/ SNORAS, AGBLLT2XXXX");

    assertThat(result)
        .contains(
            new TradeSettlementInfo(
                "DLA0553698",
                "IE00NOTICKER0",
                "NOYAHOO",
                "Instrument without a yahoo ticker",
                new BigDecimal("24.4021"),
                new BigDecimal("32765.6")));
  }

  @Test
  void parse_returnsEmptyForABlankPrice() {
    given(instrumentReferenceService.findByTicker("EJAP")).willReturn(Optional.of(BNP_JAPAN));

    assertThat(parser.parse("DLA0553690/EJAP GY/11704//Buy/ Euroclear, ABNCNL2AXXX, 14448"))
        .isEmpty();
  }

  @Test
  void parse_returnsEmptyForAPriceWrittenWithADecimalComma() {
    given(instrumentReferenceService.findByTicker("EJAP")).willReturn(Optional.of(BNP_JAPAN));

    assertThat(parser.parse("DLA0553690/EJAP GY/11704/17,864/Buy/ Euroclear, ABNCNL2AXXX, 14448"))
        .isEmpty();
  }

  @Test
  void parse_returnsEmptyForBlankUnits() {
    given(instrumentReferenceService.findByTicker("EJAP")).willReturn(Optional.of(BNP_JAPAN));

    assertThat(parser.parse("DLA0553690/EJAP GY/ /17.864/Buy/ Euroclear, ABNCNL2AXXX, 14448"))
        .isEmpty();
  }

  @Test
  void parse_returnsEmptyForNonNumericUnits() {
    given(instrumentReferenceService.findByTicker("EJAP")).willReturn(Optional.of(BNP_JAPAN));

    assertThat(parser.parse("DLA0553690/EJAP GY/11,704/17.864/Buy/ Euroclear, ABNCNL2AXXX, 14448"))
        .isEmpty();
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

  @Test
  void parse_returnsEmptyForExactlyTwoSegments() {
    assertThat(parser.parse("DLA0553690/EJAP")).isEmpty();
  }

  @Test
  void parse_returnsEmptyForARemittanceWithoutAPrice() {
    assertThat(parser.parse("DLA0553690/EJAP GY/11704")).isEmpty();
  }

  @Test
  void parse_parsesExactlyFourSegments() {
    given(instrumentReferenceService.findByTicker("EJAP")).willReturn(Optional.of(BNP_JAPAN));

    var result = parser.parse("DLA0553690/EJAP GY/11704/17.864");

    assertThat(result)
        .contains(
            new TradeSettlementInfo(
                "DLA0553690",
                "LU1291102447",
                "EJAP",
                "BNP Paribas Easy MSCI Japan Min TE UCITS ETF",
                new BigDecimal("11704"),
                new BigDecimal("17.864")));
  }
}
