package ee.tuleva.onboarding.instrument

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification


@SpringBootTest
@ActiveProfiles("test")
class InstrumentReferenceServiceIntSpec extends Specification {

  @Autowired
  InstrumentReferenceService service

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
}
