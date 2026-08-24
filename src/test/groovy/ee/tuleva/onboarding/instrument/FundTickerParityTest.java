package ee.tuleva.onboarding.instrument;

import static ee.tuleva.onboarding.comparisons.fundvalue.PriceSource.BLACKROCK;
import static ee.tuleva.onboarding.comparisons.fundvalue.PriceSource.DEUTSCHE_BOERSE;
import static ee.tuleva.onboarding.comparisons.fundvalue.PriceSource.EODHD;
import static ee.tuleva.onboarding.comparisons.fundvalue.PriceSource.EURONEXT;
import static ee.tuleva.onboarding.comparisons.fundvalue.PriceSource.MORNINGSTAR;
import static ee.tuleva.onboarding.comparisons.fundvalue.PriceSource.YAHOO;
import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider.PriceFeed;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Safety net for the FundTicker cutover: the DB-backed InstrumentReferenceService must answer
// exactly what the enum answers, per ISIN and per accessor, in both directions. Dies with the enum.
@SpringBootTest
@ActiveProfiles("test")
class FundTickerParityTest {

  @Autowired private InstrumentReferenceService service;

  @Autowired private InstrumentReferenceRepository repository;

  @Test
  void everySeededRowIsActiveAndListedOnEodhdSoBothSourcesDescribeTheSameUniverse() {
    var rows = repository.findAllByOrderByIdAsc();

    assertThat(rows).isNotEmpty();
    assertThat(rows)
        .as(
            "the service filters on active and on eodhd_listed, FundTicker has neither concept"
                + " — every parity assertion below only holds while both flags are true for"
                + " every row")
        .allSatisfy(
            row -> {
              assertThat(row.isActive()).as("active: isin=%s", row.getIsin()).isTrue();
              assertThat(row.isListedOnEodhd()).as("eodhdListed: isin=%s", row.getIsin()).isTrue();
            });
  }

  @Test
  void theTableAndTheEnumHoldExactlyTheSameIsins() {
    var enumIsins = stream(FundTicker.values()).map(FundTicker::getIsin).toList();
    var tableIsins =
        repository.findAllByOrderByIdAsc().stream().map(InstrumentReference::getIsin).toList();

    assertThat(tableIsins).containsExactlyInAnyOrderElementsOf(enumIsins);
  }

  @Test
  void findByIsinReturnsTheSameIdentityAsTheEnum() {
    for (var ticker : FundTicker.values()) {
      var instrument = instrument(ticker);

      assertThat(instrument.getYahooTicker())
          .as("yahooTicker: isin=%s", ticker.getIsin())
          .isEqualTo(ticker.getYahooTicker());
      assertThat(instrument.getEodhdTicker())
          .as("eodhdTicker: isin=%s", ticker.getIsin())
          .isEqualTo(ticker.getEodhdTicker());
      assertThat(instrument.getBloombergTicker())
          .as("bloombergTicker: isin=%s", ticker.getIsin())
          .isEqualTo(ticker.getBloombergTicker());
      assertThat(instrument.getBlackrockProductId())
          .as("blackrockProductId: isin=%s", ticker.getIsin())
          .isEqualTo(ticker.getBlackrockProductId());
      assertThat(instrument.getMorningstarId())
          .as("morningstarId: isin=%s", ticker.getIsin())
          .isEqualTo(ticker.getMorningstarId());
      assertThat(instrument.getDisplayName())
          .as("displayName: isin=%s", ticker.getIsin())
          .isEqualTo(ticker.getDisplayName());
      assertThat(instrument.getBenchmarkCategory())
          .as("benchmarkCategory: isin=%s", ticker.getIsin())
          .isEqualTo(
              ticker.getBenchmarkCategory() == null ? null : ticker.getBenchmarkCategory().name());
    }
  }

  @Test
  void findByTickerResolvesTheSameInstrumentAsTheEnum() {
    for (var ticker : FundTicker.values()) {
      var shortTicker = shortTicker(ticker);

      assertThat(service.findByTicker(shortTicker).map(InstrumentReference::getIsin))
          .as("findByTicker: shortTicker=%s", shortTicker)
          .isEqualTo(FundTicker.findByTicker(shortTicker).map(FundTicker::getIsin));
    }

    assertThat(service.findByTicker("NOSUCHTICKER"))
        .isEmpty()
        .isEqualTo(FundTicker.findByTicker("NOSUCHTICKER"));
  }

  @Test
  void findByBloombergTickerResolvesTheSameInstrumentAsTheEnum() {
    for (var ticker : FundTicker.values()) {
      var bloombergTicker = ticker.getBloombergTicker();

      assertThat(service.findByBloombergTicker(bloombergTicker).map(InstrumentReference::getIsin))
          .as("findByBloombergTicker: bloombergTicker=%s", bloombergTicker)
          .isEqualTo(FundTicker.findByBloombergTicker(bloombergTicker).map(FundTicker::getIsin));
    }

    assertThat(service.findByBloombergTicker("NOSUCHTICKER"))
        .isEmpty()
        .isEqualTo(FundTicker.findByBloombergTicker("NOSUCHTICKER"));
  }

  @Test
  void findByEodhdTickerResolvesTheSameInstrumentAsTheEnum() {
    for (var ticker : FundTicker.values()) {
      var eodhdTicker = ticker.getEodhdTicker();

      assertThat(service.findByEodhdTicker(eodhdTicker).map(InstrumentReference::getIsin))
          .as("findByEodhdTicker: eodhdTicker=%s", eodhdTicker)
          .isEqualTo(FundTicker.findByEodhdTicker(eodhdTicker).map(FundTicker::getIsin));
    }

    assertThat(service.findByEodhdTicker("NOSUCH.XETRA"))
        .isEmpty()
        .isEqualTo(FundTicker.findByEodhdTicker("NOSUCH.XETRA"));
  }

  @Test
  void theFilteredListsHoldTheSameEntriesAsTheEnum() {
    assertThat(service.getXetraIsins())
        .containsExactlyInAnyOrderElementsOf(FundTicker.getXetraIsins());
    assertThat(service.getEuronextParisIsins())
        .containsExactlyInAnyOrderElementsOf(FundTicker.getEuronextParisIsins());
    assertThat(service.getEodhdTickers())
        .containsExactlyInAnyOrderElementsOf(FundTicker.getEodhdTickers());
    assertThat(service.getYahooTickers())
        .containsExactlyInAnyOrderElementsOf(FundTicker.getYahooTickers());
    assertThat(service.getBlackrockFunds().stream().map(InstrumentReference::getIsin).toList())
        .containsExactlyInAnyOrderElementsOf(
            FundTicker.getBlackrockFunds().stream().map(FundTicker::getIsin).toList());
    assertThat(service.getMorningstarFunds().stream().map(InstrumentReference::getIsin).toList())
        .containsExactlyInAnyOrderElementsOf(
            FundTicker.getMorningstarFunds().stream().map(FundTicker::getIsin).toList());
  }

  @Test
  void theStorageKeysAreDerivedIdentically() {
    for (var ticker : FundTicker.values()) {
      var instrument = instrument(ticker);

      assertThat(instrument.getXetraStorageKey())
          .as("xetraStorageKey: isin=%s", ticker.getIsin())
          .isEqualTo(ticker.getXetraStorageKey());
      assertThat(instrument.getEuronextParisStorageKey())
          .as("euronextParisStorageKey: isin=%s", ticker.getIsin())
          .isEqualTo(ticker.getEuronextParisStorageKey());
      assertThat(instrument.getBlackrockStorageKey())
          .as("blackrockStorageKey: isin=%s", ticker.getIsin())
          .isEqualTo(ticker.getBlackrockStorageKey());
      assertThat(instrument.getMorningstarStorageKey())
          .as("morningstarStorageKey: isin=%s", ticker.getIsin())
          .isEqualTo(ticker.getMorningstarStorageKey());
    }
  }

  @Test
  void exchangeTradedMeansTheSameThingInBothSources() {
    for (var ticker : FundTicker.values()) {
      var listedOnAnExchange =
          ticker.getEodhdTicker().endsWith(".XETRA")
              || ticker.getEodhdTicker().endsWith(".PA.EODHD");

      assertThat(instrument(ticker).isExchangeTraded())
          .as("isExchangeTraded: isin=%s", ticker.getIsin())
          .isEqualTo(listedOnAnExchange);
    }
  }

  @Test
  void theStorageKeyResolversFollowThePriceFeedOrderAndDeriveTheSameKeys() {
    List<PriceFeed> feeds = PriorityPriceProvider.priceFeeds();

    assertThat(feeds.stream().map(PriceFeed::source))
        .as("EODHD deliberately sits above the exchange feeds")
        .containsExactly(BLACKROCK, MORNINGSTAR, EODHD, DEUTSCHE_BOERSE, EURONEXT, YAHOO);

    var resolvers = service.storageKeyResolvers();
    assertThat(resolvers).hasSameSizeAs(feeds);

    for (var ticker : FundTicker.values()) {
      var instrument = instrument(ticker);

      for (var index = 0; index < feeds.size(); index++) {
        Optional<String> feedKey = feeds.get(index).storageKey().apply(ticker);

        assertThat(resolvers.get(index).apply(instrument))
            .as("storage key: feed=%s, isin=%s", feeds.get(index).source(), ticker.getIsin())
            .isEqualTo(feedKey);
      }
    }
  }

  private InstrumentReference instrument(FundTicker ticker) {
    return service
        .findByIsin(ticker.getIsin())
        .orElseThrow(
            () ->
                new AssertionError(
                    "Missing row in instrument_reference: isin=%s, fundTicker=%s"
                        .formatted(ticker.getIsin(), ticker.name())));
  }

  private static String shortTicker(FundTicker ticker) {
    var yahooTicker = ticker.getYahooTicker();
    var dotIndex = yahooTicker.indexOf('.');
    return dotIndex > 0 ? yahooTicker.substring(0, dotIndex) : yahooTicker;
  }
}
