package ee.tuleva.onboarding.instrument

import ee.tuleva.onboarding.time.MutableClock
import spock.lang.Specification

import java.time.temporal.ChronoUnit

class InstrumentReferenceServiceSpec extends Specification {

  InstrumentReferenceRepository instrumentReferenceRepository = Mock()
  BenchmarkCategoryProxyRepository benchmarkCategoryProxyRepository = Mock()
  MutableClock clock = new MutableClock()

  InstrumentReferenceService service

  def setup() {
    instrumentReferenceRepository.findAllByOrderByIdAsc() >> [
        instrument("IE00B4L5Y983", "EUNL.DE", "EUNL.XETRA", "EUNL", null, null, "EQUITY_DM", true),
        instrument("IE00BFNM3G45", "SGAS.DE", "SGAS.XETRA", "SGAS", null, null, "EQUITY_DM", true),
        instrument("FR0013209921", "WLXU.PA", "WLXU.PA.EODHD", "WLXU", null, null, "EQUITY_DM", true),
        instrument("IE00BFNM3P36", "AYEM.DE", "AYEM.XETRA", "AYEM", null, null, "EQUITY_EM", true),
        instrument("LU0290358497", null, "DBXE.XETRA", "DBXE", "283108", null, "EQUITY_DM", true),
        instrument("IE00BKM4GZ66", null, "EMIM.XETRA", null, null, "F00000Q7RC", "EQUITY_EM", false),
        instrument("IE00FUND0001", null, "IE00FUND0001.EUFUND", "FUNDX", null, null, null, true),
    ]
    benchmarkCategoryProxyRepository.findAll() >> [
        new BenchmarkCategoryProxy(1L, "EQUITY_DM", "IE00B4L5Y983", null, "MSCI_WORLD"),
        new BenchmarkCategoryProxy(2L, "EQUITY_EM", "IE00BFNM3P36", null, "MSCI_EM"),
        new BenchmarkCategoryProxy(3L, "BOND_EURO", "FR0013209921", "FR0013209921", null),
        new BenchmarkCategoryProxy(4L, "BOND_GLOBAL", "IE00NOTCACHED", "IE00NOTCACHED", null),
        new BenchmarkCategoryProxy(5L, "FUND_ONLY", "IE00FUND0001", "IE00FUND0001", null),
        new BenchmarkCategoryProxy(6L, "NO_INDEX_TARGET", "IE00B4L5Y983", null, null),
    ]

    def loader = new InstrumentSnapshotLoader(instrumentReferenceRepository, benchmarkCategoryProxyRepository)
    service = new InstrumentReferenceService(loader, clock)
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

  def "findByEodhdTicker returns matching instrument"() {
    expect:
    service.findByEodhdTicker("EUNL.XETRA").isPresent()
    service.findByEodhdTicker("EUNL.XETRA").get().isin == "IE00B4L5Y983"
    service.findByEodhdTicker("WLXU.PA.EODHD").get().isin == "FR0013209921"
  }

  def "findByEodhdTicker returns empty for unknown ticker"() {
    expect:
    service.findByEodhdTicker("NONEXISTENT.XETRA").isEmpty()
  }

  def "findByEodhdTicker returns a deactivated instrument so history keeps resolving"() {
    expect:
    service.findByEodhdTicker("EMIM.XETRA").get().isin == "IE00BKM4GZ66"
  }

  def "findByIsin returns a deactivated instrument so history keeps resolving"() {
    expect:
    service.findByIsin("IE00BKM4GZ66").isPresent()
    !service.findByIsin("IE00BKM4GZ66").get().active
  }

  def "activeInstruments excludes deactivated instruments and keeps the table row order"() {
    when:
    def isins = service.activeInstruments().collect { it.isin }

    then:
    isins == [
        "IE00B4L5Y983",
        "IE00BFNM3G45",
        "FR0013209921",
        "IE00BFNM3P36",
        "LU0290358497",
        "IE00FUND0001",
    ]
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

  def "getEodhdTickers returns tickers for active instruments in table row order"() {
    when:
    def tickers = service.getEodhdTickers()

    then:
    tickers == [
        "EUNL.XETRA",
        "SGAS.XETRA",
        "WLXU.PA.EODHD",
        "AYEM.XETRA",
        "DBXE.XETRA",
        "IE00FUND0001.EUFUND",
    ]
  }

  def "getEodhdTickers skips an instrument that has a ticker but is no longer listed on EODHD"() {
    given:
    def repo = Mock(InstrumentReferenceRepository)
    def proxyRepo = Mock(BenchmarkCategoryProxyRepository)
    def delisted = instrument("IE00DELIST1", "DELISTED.DE", "DELISTED.XETRA", "DELISTED", null, null, null, true)
    setField(delisted, "eodhdListed", false)
    repo.findAllByOrderByIdAsc() >> [
        instrument("IE00OK00001", "OK.DE", "OK.XETRA", "OK", null, null, null, true),
        delisted,
    ]
    proxyRepo.findAll() >> []
    def loader = new InstrumentSnapshotLoader(repo, proxyRepo)
    def svc = new InstrumentReferenceService(loader, clock)

    when:
    svc.init()

    then:
    svc.getEodhdTickers() == ["OK.XETRA"]
    svc.findByEodhdTicker("DELISTED.XETRA").get().isin == "IE00DELIST1"
    svc.dataFindings() == []
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

  def "resolveBenchmarkProxy returns the proxy instrument for exchange-traded"() {
    when:
    def proxy = service.resolveBenchmarkProxy("EQUITY_DM", true).orElseThrow()

    then:
    proxy.proxyInstrument().isin == "IE00B4L5Y983"
    proxy.storageKey() == "IE00B4L5Y983.XETR"
    !proxy.isIndex()
  }

  def "resolveBenchmarkProxy returns the index series for non-exchange-traded"() {
    when:
    def proxy = service.resolveBenchmarkProxy("EQUITY_DM", false).orElseThrow()

    then:
    proxy.proxyInstrument() == null
    proxy.indexSeriesKey() == "MSCI_WORLD"
    proxy.isIndex()
    proxy.storageKey() == "MSCI_WORLD"
  }

  def "resolveBenchmarkProxy fails loudly for a category with no configured proxy"() {
    when:
    service.resolveBenchmarkProxy("NONEXISTENT", true)

    then:
    thrown(InstrumentReferenceService.UnresolvableBenchmarkProxyException)
  }

  def "resolveBenchmarkProxy returns empty for null category"() {
    expect:
    service.resolveBenchmarkProxy(null, true).isEmpty()
  }

  def "resolveBenchmarkProxy derives the index storage key when the index proxy is the ETF itself"() {
    expect:
    proxyStorageKey("BOND_EURO", true) == Optional.of("FR0013209921.XPAR")
    proxyStorageKey("BOND_EURO", false) == Optional.of("FR0013209921.XPAR")
  }

  def "resolveBenchmarkProxy derives the ETF storage key rather than storing it"() {
    expect:
    proxyStorageKey("EQUITY_EM", true) == Optional.of("IE00BFNM3P36.XETR")
    proxyStorageKey("EQUITY_EM", false) == Optional.of("MSCI_EM")
  }

  def "resolveBenchmarkProxy fails loudly when the proxy instrument is not in the cache"() {
    when:
    service.resolveBenchmarkProxy("BOND_GLOBAL", exchangeTraded)

    then:
    thrown(InstrumentReferenceService.UnresolvableBenchmarkProxyException)

    where:
    exchangeTraded << [true, false]
  }

  def "resolveBenchmarkProxy fails loudly when the proxy names neither an index proxy ISIN nor an index series key"() {
    when:
    service.resolveBenchmarkProxy("NO_INDEX_TARGET", false)

    then:
    thrown(InstrumentReferenceService.UnresolvableBenchmarkProxyException)
  }

  def "resolveBenchmarkProxy fails loudly when the proxy instrument has no exchange listing"() {
    when:
    service.resolveBenchmarkProxy("FUND_ONLY", exchangeTraded)

    then:
    thrown(InstrumentReferenceService.UnresolvableBenchmarkProxyException)

    where:
    exchangeTraded << [true, false]
  }

  def "scheduledRefresh refreshes cache from DB"() {
    given:
    def repo = Mock(InstrumentReferenceRepository)
    def proxyRepo = Mock(BenchmarkCategoryProxyRepository)
    repo.findAllByOrderByIdAsc() >>> [
        [instrument("IE00OLD", "OLD.DE", "OLD.XETRA", "OLD", null, null, null, true)],
        [instrument("IE00NEW", "NEW.DE", "NEW.XETRA", "NEW", null, null, null, true)]
    ]
    proxyRepo.findAll() >> []
    def loader = new InstrumentSnapshotLoader(repo, proxyRepo)
    def svc = new InstrumentReferenceService(loader, clock)
    svc.init()

    when:
    svc.scheduledRefresh()

    then:
    svc.activeInstruments().size() == 1
    svc.findByIsin("IE00NEW").isPresent()
    svc.findByIsin("IE00OLD").isEmpty()
  }

  def "init populates cache"() {
    given:
    def loader = new InstrumentSnapshotLoader(instrumentReferenceRepository, benchmarkCategoryProxyRepository)
    def svc = new InstrumentReferenceService(loader, clock)

    when:
    svc.init()

    then:
    svc.activeInstruments().size() == 6
    svc.findByIsin("IE00BKM4GZ66").isPresent()
  }

  def "refresh handles exceptions gracefully"() {
    given:
    def repo = Mock(InstrumentReferenceRepository)
    def proxyRepo = Mock(BenchmarkCategoryProxyRepository)
    repo.findAllByOrderByIdAsc() >>> [
        [instrument("IE00OK", "OK.DE", "OK.XETRA", "OK", null, null, null, true)],
        { throw new RuntimeException("DB down") }
    ]
    proxyRepo.findAll() >> []
    def loader = new InstrumentSnapshotLoader(repo, proxyRepo)
    def svc = new InstrumentReferenceService(loader, clock)
    svc.init()

    when:
    svc.scheduledRefresh()

    then:
    noExceptionThrown()
    svc.activeInstruments().size() == 1
  }

  def "init fails fast when the instrument table is empty"() {
    given:
    def repo = Mock(InstrumentReferenceRepository)
    def proxyRepo = Mock(BenchmarkCategoryProxyRepository)
    repo.findAllByOrderByIdAsc() >> []
    proxyRepo.findAll() >> []
    def loader = new InstrumentSnapshotLoader(repo, proxyRepo)
    def svc = new InstrumentReferenceService(loader, clock)

    when:
    svc.init()

    then:
    thrown(IllegalStateException)
  }

  def "init fails fast when the instrument table cannot be read"() {
    given:
    def repo = Mock(InstrumentReferenceRepository)
    def proxyRepo = Mock(BenchmarkCategoryProxyRepository)
    repo.findAllByOrderByIdAsc() >> { throw new RuntimeException("DB down") }
    proxyRepo.findAll() >> []
    def loader = new InstrumentSnapshotLoader(repo, proxyRepo)
    def svc = new InstrumentReferenceService(loader, clock)

    when:
    svc.init()

    then:
    thrown(IllegalStateException)
  }

  def "scheduledRefresh keeps the live cache when the row count drops by more than 20 percent"() {
    given:
    def repo = Mock(InstrumentReferenceRepository)
    def proxyRepo = Mock(BenchmarkCategoryProxyRepository)
    repo.findAllByOrderByIdAsc() >>> [instruments(10), instruments(7)]
    proxyRepo.findAll() >> []
    def loader = new InstrumentSnapshotLoader(repo, proxyRepo)
    def svc = new InstrumentReferenceService(loader, clock)
    svc.init()
    def refreshedAtBoot = svc.lastRefreshedAt

    when:
    clock.tick(1, ChronoUnit.HOURS)
    svc.scheduledRefresh()

    then:
    svc.activeInstruments().size() == 10
    svc.lastRefreshedAt == refreshedAtBoot
  }

  def "scheduledRefresh applies a snapshot when the row count drops by 20 percent or less"() {
    given:
    def repo = Mock(InstrumentReferenceRepository)
    def proxyRepo = Mock(BenchmarkCategoryProxyRepository)
    repo.findAllByOrderByIdAsc() >>> [instruments(10), instruments(8)]
    proxyRepo.findAll() >> []
    def loader = new InstrumentSnapshotLoader(repo, proxyRepo)
    def svc = new InstrumentReferenceService(loader, clock)
    svc.init()

    when:
    clock.tick(1, ChronoUnit.HOURS)
    svc.scheduledRefresh()

    then:
    svc.activeInstruments().size() == 8
    svc.lastRefreshedAt == clock.instant()
  }

  def "lastRefreshedAt stays put when the refresh fails"() {
    given:
    def repo = Mock(InstrumentReferenceRepository)
    def proxyRepo = Mock(BenchmarkCategoryProxyRepository)
    repo.findAllByOrderByIdAsc() >>> [instruments(3), { throw new RuntimeException("DB down") }]
    proxyRepo.findAll() >> []
    def loader = new InstrumentSnapshotLoader(repo, proxyRepo)
    def svc = new InstrumentReferenceService(loader, clock)
    svc.init()
    def refreshedAtBoot = svc.lastRefreshedAt

    when:
    clock.tick(1, ChronoUnit.HOURS)
    svc.scheduledRefresh()

    then:
    noExceptionThrown()
    svc.lastRefreshedAt == refreshedAtBoot
  }

  def "an instrument that is not listed on EODHD may have no EODHD ticker"() {
    given:
    def repo = Mock(InstrumentReferenceRepository)
    def proxyRepo = Mock(BenchmarkCategoryProxyRepository)
    def unlisted = instrument("IE00NOTICK2", "NOTICK.DE", null, "NOTICK", null, null, null, true)
    setField(unlisted, "eodhdListed", false)
    repo.findAllByOrderByIdAsc() >> [unlisted]
    proxyRepo.findAll() >> []
    def loader = new InstrumentSnapshotLoader(repo, proxyRepo)
    def svc = new InstrumentReferenceService(loader, clock)

    when:
    svc.init()

    then:
    noExceptionThrown()
    svc.getEodhdTickers().isEmpty()
    svc.getYahooTickers() == ["NOTICK.DE"]
    svc.dataFindings() == []
  }

  def "dataFindings is empty for clean reference data"() {
    expect:
    service.dataFindings() == []
  }

  def "scheduledRefresh applies a snapshot that has findings"() {
    given:
    def repo = Mock(InstrumentReferenceRepository)
    def proxyRepo = Mock(BenchmarkCategoryProxyRepository)
    repo.findAllByOrderByIdAsc() >>> [
        [
            instrument("IE00LIVE001", "LIVE1.DE", "LIVE1.XETRA", "LIVE1", null, null, null, true),
            instrument("IE00LIVE002", "LIVE2.DE", "LIVE2.XETRA", "LIVE2", null, null, null, true),
        ],
        [
            instrument("IE00LIVE001", "LIVE1.DE", "DUP.XETRA", "LIVE1", null, null, null, true),
            instrument("IE00LIVE002", "LIVE2.DE", "DUP.XETRA", "LIVE2", null, null, null, true),
        ],
    ]
    proxyRepo.findAll() >> []
    def loader = new InstrumentSnapshotLoader(repo, proxyRepo)
    def svc = new InstrumentReferenceService(loader, clock)
    svc.init()

    when:
    clock.tick(1, ChronoUnit.HOURS)
    svc.scheduledRefresh()

    then:
    noExceptionThrown()
    svc.lastRefreshedAt == clock.instant()
    svc.findByEodhdTicker("DUP.XETRA").get().isin == "IE00LIVE001"
    svc.dataFindings() == [
        new InstrumentDataFinding.AmbiguousLookupKey("eodhdTicker", "DUP.XETRA", ["IE00LIVE001", "IE00LIVE002"])
    ]
  }

  def "scheduledRefresh clears the findings once the data is fixed"() {
    given:
    def repo = Mock(InstrumentReferenceRepository)
    def proxyRepo = Mock(BenchmarkCategoryProxyRepository)
    repo.findAllByOrderByIdAsc() >>> [
        [
            instrument("IE00LIVE001", "LIVE1.DE", "DUP.XETRA", "LIVE1", null, null, null, true),
            instrument("IE00LIVE002", "LIVE2.DE", "DUP.XETRA", "LIVE2", null, null, null, true),
        ],
        [
            instrument("IE00LIVE001", "LIVE1.DE", "LIVE1.XETRA", "LIVE1", null, null, null, true),
            instrument("IE00LIVE002", "LIVE2.DE", "LIVE2.XETRA", "LIVE2", null, null, null, true),
        ],
    ]
    proxyRepo.findAll() >> []
    def loader = new InstrumentSnapshotLoader(repo, proxyRepo)
    def svc = new InstrumentReferenceService(loader, clock)
    svc.init()

    when:
    clock.tick(1, ChronoUnit.HOURS)
    svc.scheduledRefresh()

    then:
    svc.dataFindings() == []
    svc.getEodhdTickers().toSorted() == ["LIVE1.XETRA", "LIVE2.XETRA"]
  }

  private Optional<String> proxyStorageKey(String category, boolean exchangeTraded) {
    return service.resolveBenchmarkProxy(category, exchangeTraded).map { it.storageKey() }
  }

  private static List<InstrumentReference> instruments(int count) {
    (1..count).collect {
      def isin = "IE00TEST%03d".formatted(it)
      instrument(isin, null, "${isin}.XETRA", null, null, null, null, true)
    }
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
    setField(inst, "eodhdListed", true)
    setField(inst, "active", active)
    return inst
  }

  private static void setField(Object obj, String fieldName, Object value) {
    def field = InstrumentReference.getDeclaredField(fieldName)
    field.setAccessible(true)
    field.set(obj, value)
  }
}
