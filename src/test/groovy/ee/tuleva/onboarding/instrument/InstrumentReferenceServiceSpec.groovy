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
    instrumentReferenceRepository.findAll() >> [
        instrument("IE00B4L5Y983", "EUNL.DE", "EUNL.XETRA", "EUNL", null, null, "EQUITY_DM", true),
        instrument("IE00BFNM3G45", "SGAS.DE", "SGAS.XETRA", "SGAS", null, null, "EQUITY_DM", true),
        instrument("FR0013209921", "WLXU.PA", "WLXU.PA.EODHD", "WLXU", null, null, "EQUITY_DM", true),
        instrument("IE00BFNM3P36", "AYEM.DE", "AYEM.XETRA", "AYEM", null, null, "EQUITY_EM", true),
        instrument("LU0290358497", null, "DBXE.XETRA", "DBXE", "283108", null, "EQUITY_DM", true),
        instrument("IE00BKM4GZ66", null, "EMIM.XETRA", null, null, "F00000Q7RC", "EQUITY_EM", false),
    ]
    benchmarkCategoryProxyRepository.findAll() >> [
        new BenchmarkCategoryProxy(1L, "EQUITY_DM", "IE00B4L5Y983", null, "MSCI_WORLD"),
        new BenchmarkCategoryProxy(2L, "EQUITY_EM", "IE00BFNM3P36", null, "MSCI_EM"),
        new BenchmarkCategoryProxy(3L, "BOND_EURO", "FR0013209921", "FR0013209921", null),
        new BenchmarkCategoryProxy(4L, "BOND_GLOBAL", "IE00NOTCACHED", "IE00NOTCACHED", null),
    ]

    service = new InstrumentReferenceService(instrumentReferenceRepository, benchmarkCategoryProxyRepository, clock)
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

  def "findByIsin returns a deactivated instrument so history keeps resolving"() {
    expect:
    service.findByIsin("IE00BKM4GZ66").isPresent()
    !service.findByIsin("IE00BKM4GZ66").get().active
  }

  def "activeInstruments excludes deactivated instruments"() {
    when:
    def isins = service.activeInstruments().collect { it.isin }

    then:
    isins.size() == 5
    !isins.contains("IE00BKM4GZ66")
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

  def "resolveBenchmarkProxy derives the index storage key when the index proxy is the ETF itself"() {
    expect:
    service.resolveBenchmarkProxy("BOND_EURO", true) == Optional.of("FR0013209921.XPAR")
    service.resolveBenchmarkProxy("BOND_EURO", false) == Optional.of("FR0013209921.XPAR")
  }

  def "resolveBenchmarkProxy derives the ETF storage key rather than storing it"() {
    expect:
    service.resolveBenchmarkProxy("EQUITY_EM", true) == Optional.of("IE00BFNM3P36.XETR")
    service.resolveBenchmarkProxy("EQUITY_EM", false) == Optional.of("MSCI_EM")
  }

  def "resolveBenchmarkProxy returns empty when the proxy instrument is not in the cache"() {
    expect:
    service.resolveBenchmarkProxy("BOND_GLOBAL", true).isEmpty()
    service.resolveBenchmarkProxy("BOND_GLOBAL", false).isEmpty()
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
    def svc = new InstrumentReferenceService(repo, proxyRepo, clock)
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
    def svc = new InstrumentReferenceService(instrumentReferenceRepository, benchmarkCategoryProxyRepository, clock)

    when:
    svc.init()

    then:
    svc.activeInstruments().size() == 5
    svc.findByIsin("IE00BKM4GZ66").isPresent()
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
    def svc = new InstrumentReferenceService(repo, proxyRepo, clock)
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
    repo.findAll() >> []
    proxyRepo.findAll() >> []
    def svc = new InstrumentReferenceService(repo, proxyRepo, clock)

    when:
    svc.init()

    then:
    thrown(IllegalStateException)
  }

  def "init fails fast when the instrument table cannot be read"() {
    given:
    def repo = Mock(InstrumentReferenceRepository)
    def proxyRepo = Mock(BenchmarkCategoryProxyRepository)
    repo.findAll() >> { throw new RuntimeException("DB down") }
    proxyRepo.findAll() >> []
    def svc = new InstrumentReferenceService(repo, proxyRepo, clock)

    when:
    svc.init()

    then:
    thrown(IllegalStateException)
  }

  def "scheduledRefresh keeps the live cache when the row count drops by more than 20 percent"() {
    given:
    def repo = Mock(InstrumentReferenceRepository)
    def proxyRepo = Mock(BenchmarkCategoryProxyRepository)
    repo.findAll() >>> [instruments(10), instruments(7)]
    proxyRepo.findAll() >> []
    def svc = new InstrumentReferenceService(repo, proxyRepo, clock)
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
    repo.findAll() >>> [instruments(10), instruments(8)]
    proxyRepo.findAll() >> []
    def svc = new InstrumentReferenceService(repo, proxyRepo, clock)
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
    repo.findAll() >>> [instruments(3), { throw new RuntimeException("DB down") }]
    proxyRepo.findAll() >> []
    def svc = new InstrumentReferenceService(repo, proxyRepo, clock)
    svc.init()
    def refreshedAtBoot = svc.lastRefreshedAt

    when:
    clock.tick(1, ChronoUnit.HOURS)
    svc.scheduledRefresh()

    then:
    noExceptionThrown()
    svc.lastRefreshedAt == refreshedAtBoot
  }

  private static List<InstrumentReference> instruments(int count) {
    (1..count).collect {
      instrument("IE00TEST%03d".formatted(it), null, null, null, null, null, null, true)
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
    setField(inst, "active", active)
    return inst
  }

  private static void setField(Object obj, String fieldName, Object value) {
    def field = InstrumentReference.getDeclaredField(fieldName)
    field.setAccessible(true)
    field.set(obj, value)
  }
}
