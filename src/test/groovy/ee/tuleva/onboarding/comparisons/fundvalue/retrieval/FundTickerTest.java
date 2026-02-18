package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FundTickerTest {

  @Test
  void allYahooTickersAreUnique() {
    var tickers = getYahooTickers();
    assertThat(tickers).doesNotHaveDuplicates();
  }

  @Test
  void allEodhdTickersAreUnique() {
    var tickers = getEodhdTickers();
    assertThat(tickers).doesNotHaveDuplicates();
  }

  @Test
  void allEodhdTickersAreDifferentFromYahooTickers() {
    var yahooTickers = getYahooTickers();
    var eodhdTickers = getEodhdTickers();

    assertThat(eodhdTickers).doesNotContainAnyElementsOf(yahooTickers);
  }

  @Test
  void amundiUsaScreenedHasDistinctTickers() {
    var ticker = AMUNDI_USA_SCREENED;

    assertThat(ticker.getYahooTicker()).isEqualTo("USAS.PA");
    assertThat(ticker.getEodhdTicker()).isEqualTo("USAS.PA.EODHD");
  }

  @Test
  void getXetraIsinsReturnsOnlyXetraTradedEtfs() {
    assertThat(getXetraIsins())
        .containsExactlyInAnyOrder(
            "IE00BFNM3G45",
            "IE00BFNM3D14",
            "IE00BFNM3L97",
            "IE00BMDBMY19",
            "IE00BJZ2DC62",
            "LU0476289540",
            "IE000O58J820",
            "LU1291099718",
            "LU1291106356",
            "LU1291102447");
  }

  @Test
  void getEuronextParisIsinsReturnsOnlyParisTradedEtfs() {
    assertThat(getEuronextParisIsins()).containsExactly("IE000F60HVH9");
  }

  @Test
  void xetraTradedEtfReturnsXetraStorageKey() {
    assertThat(ISHARES_USA_ESG_SCREENED.getXetraStorageKey()).hasValue("IE00BFNM3G45.XETR");
  }

  @Test
  void nonXetraTradedEtfReturnsEmptyXetraStorageKey() {
    assertThat(AMUNDI_USA_SCREENED.getXetraStorageKey()).isEmpty();
  }

  @Test
  void parisTradedEtfReturnsEuronextParisStorageKey() {
    assertThat(AMUNDI_USA_SCREENED.getEuronextParisStorageKey()).hasValue("IE000F60HVH9.XPAR");
  }

  @Test
  void nonParisTradedEtfReturnsEmptyEuronextParisStorageKey() {
    assertThat(ISHARES_USA_ESG_SCREENED.getEuronextParisStorageKey()).isEmpty();
  }

  @Test
  void findByTicker_findsXetraTicker() {
    assertThat(findByTicker("EJAP")).contains(BNP_JAPAN_ESG_FILTERED);
  }

  @Test
  void findByTicker_findsAnotherTicker() {
    assertThat(findByTicker("XRSM")).contains(XTRACKERS_USA_ESG_SCREENED);
  }

  @Test
  void findByTicker_returnsEmptyForUnknown() {
    assertThat(findByTicker("ZZZZ")).isEmpty();
  }

  @Test
  void getMorningstarFundsReturnsExactlySevenEntries() {
    assertThat(getMorningstarFunds()).hasSize(7);
  }

  @Test
  void mutualFundReturnsMorningstarStorageKey() {
    assertThat(ISHARES_DEVELOPED_WORLD_ESG_SCREENED.getMorningstarStorageKey())
        .hasValue("IE00BFG1TM61.MORNINGSTAR");
  }

  @Test
  void etfReturnsEmptyMorningstarStorageKey() {
    assertThat(ISHARES_USA_ESG_SCREENED.getMorningstarStorageKey()).isEmpty();
  }
}
