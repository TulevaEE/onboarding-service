package ee.tuleva.onboarding.instrument

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification


@SpringBootTest
@ActiveProfiles("test")
class InstrumentReferenceServiceIntSpec extends Specification {

  @Autowired
  InstrumentReferenceService service

  def "every FundTicker ISIN exists in the service"() {
    expect:
    FundTicker.values().each { ticker ->
      def result = service.findByIsin(ticker.isin)
      assert result.isPresent(): "Missing ISIN in instrument_reference: ${ticker.isin} (${ticker.name()})"
    }
  }

  def "findByIsin returns correct data for each FundTicker entry"() {
    expect:
    FundTicker.values().each { ticker ->
      def instrument = service.findByIsin(ticker.isin).orElseThrow()

      assert instrument.yahooTicker == ticker.yahooTicker:
          "Yahoo ticker mismatch for ${ticker.isin}: expected=${ticker.yahooTicker}, actual=${instrument.yahooTicker}"
      assert instrument.eodhdTicker == ticker.eodhdTicker:
          "EODHD ticker mismatch for ${ticker.isin}: expected=${ticker.eodhdTicker}, actual=${instrument.eodhdTicker}"
      assert instrument.bloombergTicker == ticker.bloombergTicker:
          "Bloomberg ticker mismatch for ${ticker.isin}: expected=${ticker.bloombergTicker}, actual=${instrument.bloombergTicker}"
      assert instrument.blackrockProductId == ticker.blackrockProductId:
          "BlackRock ID mismatch for ${ticker.isin}: expected=${ticker.blackrockProductId}, actual=${instrument.blackrockProductId}"
      assert instrument.morningstarId == ticker.morningstarId:
          "Morningstar ID mismatch for ${ticker.isin}: expected=${ticker.morningstarId}, actual=${instrument.morningstarId}"
      assert instrument.displayName == ticker.displayName:
          "Display name mismatch for ${ticker.isin}: expected=${ticker.displayName}, actual=${instrument.displayName}"
    }
  }

  def "getYahooTickers matches FundTicker"() {
    when:
    def serviceTickers = service.getYahooTickers().toSorted()
    def enumTickers = FundTicker.getYahooTickers().toSorted()

    then:
    serviceTickers == enumTickers
  }

  def "getXetraIsins matches FundTicker"() {
    when:
    def serviceIsins = service.getXetraIsins().toSorted()
    def enumIsins = FundTicker.getXetraIsins().toSorted()

    then:
    serviceIsins == enumIsins
  }

  def "getEuronextParisIsins matches FundTicker"() {
    when:
    def serviceIsins = service.getEuronextParisIsins().toSorted()
    def enumIsins = FundTicker.getEuronextParisIsins().toSorted()

    then:
    serviceIsins == enumIsins
  }

  def "getEodhdTickers matches FundTicker"() {
    when:
    def serviceTickers = service.getEodhdTickers().toSorted()
    def enumTickers = FundTicker.getEodhdTickers().toSorted()

    then:
    serviceTickers == enumTickers
  }

  def "getBlackrockFunds matches FundTicker"() {
    when:
    def serviceIsins = service.getBlackrockFunds().collect { it.isin }.toSorted()
    def enumIsins = FundTicker.getBlackrockFunds().collect { it.isin }.toSorted()

    then:
    serviceIsins == enumIsins
  }

  def "getMorningstarFunds matches FundTicker"() {
    when:
    def serviceIsins = service.getMorningstarFunds().collect { it.isin }.toSorted()
    def enumIsins = FundTicker.getMorningstarFunds().collect { it.isin }.toSorted()

    then:
    serviceIsins == enumIsins
  }

  def "storage key derivation matches FundTicker for all ISINs"() {
    expect:
    FundTicker.values().each { ticker ->
      def instrument = service.findByIsin(ticker.isin).orElseThrow()

      assert instrument.getXetraStorageKey() == ticker.getXetraStorageKey():
          "Xetra key mismatch for ${ticker.isin}"
      assert instrument.getEuronextParisStorageKey() == ticker.getEuronextParisStorageKey():
          "Euronext key mismatch for ${ticker.isin}"
      assert instrument.getBlackrockStorageKey() == ticker.getBlackrockStorageKey():
          "BlackRock key mismatch for ${ticker.isin}"
      assert instrument.getMorningstarStorageKey() == ticker.getMorningstarStorageKey():
          "Morningstar key mismatch for ${ticker.isin}"
    }
  }

  def "benchmark category matches FundTicker"() {
    expect:
    FundTicker.values().each { ticker ->
      def instrument = service.findByIsin(ticker.isin).orElseThrow()
      def expectedCategory = ticker.benchmarkCategory?.name()

      assert instrument.benchmarkCategory == expectedCategory:
          "Benchmark category mismatch for ${ticker.isin}: expected=${expectedCategory}, actual=${instrument.benchmarkCategory}"
    }
  }

  def "benchmark proxy resolution mirrors TrackingDifferenceService.resolveBenchmarkKey"() {
    expect:
    proxyStorageKey("EQUITY_DM", true) == Optional.of("IE00B4L5Y983.XETR")
    proxyStorageKey("EQUITY_DM", false) == Optional.of("MSCI_WORLD")
    proxyStorageKey("EQUITY_EM", true) == Optional.of("IE00B4L5YC18.XETR")
    proxyStorageKey("EQUITY_EM", false) == Optional.of("MSCI_EM")

    and:
    proxyStorageKey("BOND_EURO", true) == Optional.of("IE00B3DKXQ41.XETR")
    proxyStorageKey("BOND_EURO", false) == Optional.of("IE00B3DKXQ41.XETR")
    proxyStorageKey("BOND_GLOBAL", true) == Optional.of("IE00BDBRDM35.XETR")
    proxyStorageKey("BOND_GLOBAL", false) == Optional.of("IE00BDBRDM35.XETR")

    and:
    !service.resolveBenchmarkProxy(null, true).isPresent()
  }

  def "resolveBenchmarkProxy fails loudly for a category with no configured proxy"() {
    when:
    service.resolveBenchmarkProxy("NONEXISTENT", true)

    then:
    thrown(InstrumentReferenceService.UnresolvableBenchmarkProxyException)
  }

  def "the exchange-traded proxy carries the proxy instrument, not just its storage key"() {
    when:
    def proxy = service.resolveBenchmarkProxy("EQUITY_DM", true).orElseThrow()

    then:
    proxy.proxyInstrument().isin == "IE00B4L5Y983"
    !proxy.isIndex()
  }

  private Optional<String> proxyStorageKey(String category, boolean exchangeTraded) {
    return service.resolveBenchmarkProxy(category, exchangeTraded).map { it.storageKey() }
  }

  def "findByTicker matches FundTicker.findByTicker"() {
    expect:
    ["SGAS", "EUNL", "GAGH", "BDWTEIA"].each { shortTicker ->
      def serviceResult = service.findByTicker(shortTicker)
      def enumResult = FundTicker.findByTicker(shortTicker)

      assert serviceResult.isPresent() == enumResult.isPresent():
          "findByTicker mismatch for ${shortTicker}"
      if (serviceResult.isPresent()) {
        assert serviceResult.get().isin == enumResult.get().isin:
            "findByTicker ISIN mismatch for ${shortTicker}"
      }
    }
  }

  def "settlement terms stay empty until seeded manually after deploy"() {
    expect:
    service.settlementTerms("IE0009FT4LX4") == Optional.empty()
    service.settlementTerms("IE00BFNM3G45") == Optional.empty()
  }

  def "findByBloombergTicker matches FundTicker.findByBloombergTicker"() {
    expect:
    ["SGAS", "EUNL", "BGIEAX2", "GAGH"].each { bbgTicker ->
      def serviceResult = service.findByBloombergTicker(bbgTicker)
      def enumResult = FundTicker.findByBloombergTicker(bbgTicker)

      assert serviceResult.isPresent() == enumResult.isPresent():
          "findByBloombergTicker mismatch for ${bbgTicker}"
      if (serviceResult.isPresent()) {
        assert serviceResult.get().isin == enumResult.get().isin:
            "findByBloombergTicker ISIN mismatch for ${bbgTicker}"
      }
    }
  }
}
