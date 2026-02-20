package ee.tuleva.onboarding.administration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import spock.lang.Specification

import static org.mockito.ArgumentMatchers.any
import static org.mockito.Mockito.*

@SpringBootTest
class PortfolioAnalyticsSchedulerIntegrationSpec extends Specification{

  @MockitoBean
  private PortfolioAnalyticsSource portfolioAnalyticsSource

  @Autowired
  private PortfolioAnalyticsScheduler portfolioAnalyticsScheduler

  @Autowired
  private PortfolioAnalyticsRepository repository

  def "test processing CSV"() {
    when(portfolioAnalyticsSource.fetchCsv(any())).thenAnswer(invocation -> {
      InputStream is = getClass().getResourceAsStream("/portfolio_analytics_sample.csv")
      assert is != null
      return Optional.of(is)
    })

    when:
    portfolioAnalyticsScheduler.fetchPortfolioAnalytics()

    then:
    Iterable<PortfolioAnalytics> results = repository.findAll()
    assert !results.empty
    PortfolioAnalytics firstAnalytics = results.get(0)
    assert firstAnalytics.content.size() == 7
    Map<String, Object> content = firstAnalytics.getContent().get(0)

    "Equity Fund" == content.get("InstrumentType")
    "EUR" == content.get("FundCurr")
    "Tuleva Maailma Aktsiate Pensionifond" == content.get("Portfolio")
    new BigDecimal("12.2") == content.get("GainQC")
    new BigDecimal("12.2") == content.get("PriceQC")
    new BigDecimal("12.2") == content.get("ValuationPC")
    100 == content.get("Quantity")
    "ISIN1" == content.get("ISIN")
    "30.05.2024" == content.get("NAVDate")
    "" == content.get("MaturityDate")
    "Asset 1" == content.get("AssetName")
    "Equities" == content.get("AssetType")
    "Equity Fund" == content.get("Detailed Asset Type")
  }
}
