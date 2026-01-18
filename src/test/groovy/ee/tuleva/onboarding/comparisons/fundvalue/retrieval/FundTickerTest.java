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
}
