package ee.tuleva.onboarding.investment.instrument

import spock.lang.Specification

class InstrumentReferenceServiceSpec extends Specification {

  InstrumentReferenceRepository instrumentReferenceRepository = Mock()
  BenchmarkCategoryProxyRepository benchmarkCategoryProxyRepository = Mock()
  org.springframework.context.ApplicationEventPublisher eventPublisher = Mock()

  InstrumentReferenceService service

  def setup() {
    instrumentReferenceRepository.findAll() >> [
        instrument("IE00B4L5Y983", "EUNL.DE", "EUNL.XETRA", "EUNL", null, null, "EQUITY_DM", true),
        instrument("IE00BFNM3G45", "SGAS.DE", "SGAS.XETRA", "SGAS", null, null, "EQUITY_DM", true),
        instrument("FR0013209921", "WLXU.PA", "WLXU.PA.EODHD", "WLXU", null, null, "EQUITY_DM", true),
        instrument("IE00BFNM3P36", "AYEM.DE", "AYEM.XETRA", "AYEM", null, null, "EQUITY_EM", true),
        instrument("LU0290358497", null, "DBXE.XETRA", "DBXE", "283108", null, "EQUITY_DM", true),
        instrument("IE00BKM4GZ66", null, "EMIM.XETRA", null, null, "F00000Q7RC", "EQUITY_EM", false),
    ]
    benchmarkCategoryProxyRepository.findAll() >> [
        new BenchmarkCategoryProxy(1L, "EQUITY_DM", "IE00B4L5Y983.XETR", "MSCI_WORLD"),
        new BenchmarkCategoryProxy(2L, "EQUITY_EM", "IE00B4L5YC18.XETR", "MSCI_EM"),
        new BenchmarkCategoryProxy(3L, "BOND_EURO", "IE00B3DKXQ41.XETR", null),
    ]

    service = new InstrumentReferenceService(instrumentReferenceRepository, benchmarkCategoryProxyRepository, eventPublisher)
    service.init()
  }

  def "findByIsin returns matching instrument"() {
    expect:
    service.findByIsin("IE00B4L5Y983").isPresent()
    service.findByIsin("IE00B4L5Y983").get().isin == "IE00B4L5Y983"
  }

  def "findByIsin returns empty for unknown ISIN"() {
    expect:
    service.findByIsin("UNKNOWN").isEmpty()
  }

  def "findByTicker returns matching instrument by short ticker"() {
    expect:
    service.findByTicker("EUNL").isPresent()
    service.findByTicker("EUNL").get().isin == "IE00B4L5Y983"
  }

  def "findByTicker returns empty for unknown ticker"() {
    expect:
    service.findByTicker("NONEXISTENT").isEmpty()
  }

  def "findByBloombergTicker returns matching instrument"() {
    expect:
    service.findByBloombergTicker("EUNL").isPresent()
    service.findByBloombergTicker("EUNL").get().isin == "IE00B4L5Y983"
  }

  def "findByBloombergTicker returns empty for unknown ticker"() {
    expect:
    service.findByBloombergTicker("NONEXISTENT").isEmpty()
  }

  def "findAll returns all instruments"() {
    expect:
    service.findAll().size() == 6
  }

  def "getXetraIsins returns only active instruments with .XETRA eodhd ticker"() {
    when:
    def isins = service.getXetraIsins()

    then:
    isins.contains("IE00B4L5Y983")
    isins.contains("IE00BFNM3G45")
    isins.contains("IE00BFNM3P36")
    isins.contains("LU0290358497")
    !isins.contains("FR0013209921")
    !isins.contains("IE00BKM4GZ66")
  }

  def "getEuronextParisIsins returns only active instruments with .PA.EODHD eodhd ticker"() {
    when:
    def isins = service.getEuronextParisIsins()

    then:
    isins.contains("FR0013209921")
    !isins.contains("IE00B4L5Y983")
  }

  def "getEodhdTickers returns tickers for active instruments"() {
    when:
    def tickers = service.getEodhdTickers()

    then:
    tickers.contains("EUNL.XETRA")
    tickers.contains("WLXU.PA.EODHD")
    !tickers.contains("EMIM.XETRA")
  }

  def "getYahooTickers returns tickers for active instruments excluding nulls"() {
    when:
    def tickers = service.getYahooTickers()

    then:
    tickers.contains("EUNL.DE")
    tickers.contains("SGAS.DE")
    !tickers.contains(null)
    tickers.size() == 4
  }

  def "getBlackrockFunds returns only active instruments with blackrockProductId"() {
    when:
    def funds = service.getBlackrockFunds()

    then:
    funds.size() == 1
    funds[0].isin == "LU0290358497"
  }

  def "getMorningstarFunds returns only active instruments with morningstarId"() {
    when:
    def funds = service.getMorningstarFunds()

    then:
    funds.isEmpty()
  }

  def "resolveBenchmarkProxy returns ETF proxy for exchange-traded"() {
    expect:
    service.resolveBenchmarkProxy("EQUITY_DM", true) == Optional.of("IE00B4L5Y983.XETR")
  }

  def "resolveBenchmarkProxy returns index proxy for non-exchange-traded"() {
    expect:
    service.resolveBenchmarkProxy("EQUITY_DM", false) == Optional.of("MSCI_WORLD")
  }

  def "resolveBenchmarkProxy returns empty for unknown category"() {
    expect:
    service.resolveBenchmarkProxy("NONEXISTENT", true).isEmpty()
  }

  def "resolveBenchmarkProxy returns empty for null category"() {
    expect:
    service.resolveBenchmarkProxy(null, true).isEmpty()
  }

  def "resolveBenchmarkProxy returns empty when index proxy is null"() {
    expect:
    service.resolveBenchmarkProxy("BOND_EURO", false).isEmpty()
  }

  def "storageKeyResolvers resolve keys in priority order with EODHD above the exchange feeds"() {
    given:
    def inst = instrument("IE00TEST", "TST.DE", "TST.XETRA", null, "123", "M1", "EQUITY_DM", true)

    when:
    def keys = service.storageKeyResolvers()
        .collect { resolver -> resolver.apply(inst) }
        .findAll { it.isPresent() }
        .collect { it.get() }

    then:
    keys == [
        "IE00TEST.BLACKROCK",
        "IE00TEST.MORNINGSTAR",
        "TST.XETRA",
        "IE00TEST.XETR",
        "TST.DE",
    ]
  }

  def "scheduledRefresh refreshes cache from DB"() {
    given:
    def repo = Mock(InstrumentReferenceRepository)
    def proxyRepo = Mock(BenchmarkCategoryProxyRepository)
    repo.findAll() >>> [
        [instrument("IE00OLD", "OLD.DE", "OLD.XETRA", "OLD", null, null, null, true)],
        [instrument("IE00NEW", "NEW.DE", "NEW.XETRA", "NEW", null, null, null, true)]
    ]
    proxyRepo.findAll() >> []
    def svc = new InstrumentReferenceService(repo, proxyRepo, eventPublisher)
    svc.init()

    when:
    svc.scheduledRefresh()

    then:
    svc.findAll().size() == 1
    svc.findByIsin("IE00NEW").isPresent()
    svc.findByIsin("IE00OLD").isEmpty()
  }

  def "init populates cache without publishing the cache refreshed event"() {
    given:
    def publisher = Mock(org.springframework.context.ApplicationEventPublisher)
    def svc = new InstrumentReferenceService(instrumentReferenceRepository, benchmarkCategoryProxyRepository, publisher)

    when:
    svc.init()

    then:
    svc.findAll().size() == 6
    0 * publisher.publishEvent(_ as InstrumentCacheRefreshedEvent)
  }

  def "scheduledRefresh publishes the cache refreshed event"() {
    given:
    def publisher = Mock(org.springframework.context.ApplicationEventPublisher)
    def svc = new InstrumentReferenceService(instrumentReferenceRepository, benchmarkCategoryProxyRepository, publisher)

    when:
    svc.scheduledRefresh()

    then:
    1 * publisher.publishEvent(new InstrumentCacheRefreshedEvent(6))
  }

  def "refresh handles exceptions gracefully"() {
    given:
    def repo = Mock(InstrumentReferenceRepository)
    def proxyRepo = Mock(BenchmarkCategoryProxyRepository)
    repo.findAll() >>> [
        [instrument("IE00OK", "OK.DE", "OK.XETRA", "OK", null, null, null, true)],
        { throw new RuntimeException("DB down") }
    ]
    proxyRepo.findAll() >> []
    def svc = new InstrumentReferenceService(repo, proxyRepo, eventPublisher)
    svc.init()

    when:
    svc.scheduledRefresh()

    then:
    noExceptionThrown()
    svc.findAll().size() == 1
  }

  private static InstrumentReference instrument(
      String isin, String yahooTicker, String eodhdTicker, String bloombergTicker,
      String blackrockProductId, String morningstarId, String benchmarkCategory, boolean active) {
    def inst = new InstrumentReference()
    setField(inst, "isin", isin)
    setField(inst, "yahooTicker", yahooTicker)
    setField(inst, "eodhdTicker", eodhdTicker)
    setField(inst, "bloombergTicker", bloombergTicker)
    setField(inst, "blackrockProductId", blackrockProductId)
    setField(inst, "morningstarId", morningstarId)
    setField(inst, "benchmarkCategory", benchmarkCategory)
    setField(inst, "active", active)
    return inst
  }

  private static void setField(Object obj, String fieldName, Object value) {
    def field = InstrumentReference.getDeclaredField(fieldName)
    field.setAccessible(true)
    field.set(obj, value)
  }
}
