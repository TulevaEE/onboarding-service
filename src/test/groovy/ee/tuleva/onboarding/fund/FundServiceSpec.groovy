package ee.tuleva.onboarding.fund

import ee.tuleva.onboarding.fund.statistics.PensionFundStatistics
import ee.tuleva.onboarding.fund.statistics.PensionFundStatisticsService
import ee.tuleva.onboarding.locale.LocaleConfiguration
import ee.tuleva.onboarding.locale.LocaleService
import spock.lang.Specification

import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static java.util.Collections.singletonList
import static java.util.stream.Collectors.toList

class FundServiceSpec extends Specification {

  def fundRepository = Mock(FundRepository)
  def pensionFundStatisticsService = Mock(PensionFundStatisticsService)
  def localeService = Mock(LocaleService)

  def fundService = new FundService(fundRepository, pensionFundStatisticsService,localeService)

  def "can get funds and statistics"() {
    given:
    String fundManagerName = "Tuleva"
    Iterable<Fund> funds = sampleFunds().stream()
      .filter({ fund -> fund.fundManager.name == fundManagerName })
      .collect(toList())
    fundRepository.findAllByFundManagerNameIgnoreCase(fundManagerName) >> funds
    def tulevaFund = funds.first()
    def volume = 1_000_000.0
    def nav = 1.64
    def peopleCount = 123
    pensionFundStatisticsService.getCachedStatistics() >>
      [new PensionFundStatistics(tulevaFund.isin, volume, nav, peopleCount)]
    localeService.getLanguage() >> LocaleConfiguration.DEFAULT_LANGUAGE
    when:
    def response = fundService.getFunds(Optional.of(fundManagerName))

    then:
    def fund = response.first()
    fund.isin == tulevaFund.isin
    fund.volume == volume
    fund.nav == nav
    fund.peopleCount == peopleCount
    response.size() == 2
  }

  def "can get funds with names in given language"() {
    given:
    String fundManagerName = "Tuleva"

    Iterable<Fund> funds = sampleFunds().stream()
      .filter({ fund -> fund.fundManager.name == fundManagerName })
      .collect(toList())
    fundRepository.findAllByFundManagerNameIgnoreCase(fundManagerName) >> funds
    localeService.getLanguage() >> language

    def tulevaFund = funds.first()
    pensionFundStatisticsService.getCachedStatistics() >> singletonList(PensionFundStatistics.getNull())

    when:
    def response = fundService.getFunds(Optional.of(fundManagerName))

    then:
    def fund = response.first()
    fund.isin == tulevaFund.isin
    fund.name == name
    response.size() == 2

    where:
    language | name
    "en"     | "Tuleva World Stock Fund"
    "et"     | "Tuleva maailma aktsiate pensionifond"
  }

  def "sorts funds by name"() {
    given:
    String fundManagerName = "Tuleva"
    Iterable<Fund> funds = sampleFunds().stream()
      .filter({ fund -> fund.fundManager.name == fundManagerName })
      .sorted(new Comparator<Fund>() {
        @Override
        int compare(Fund fund1, Fund fund2) {
          return fund2 <=> fund1
        }
      })
      .collect(toList())
    fundRepository.findAllByFundManagerNameIgnoreCase(fundManagerName) >> funds
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    localeService.getLanguage() >> LocaleConfiguration.DEFAULT_LANGUAGE

    when:
    def response = fundService.getFunds(Optional.of(fundManagerName))

    then:
    with(response[0]) {
      name == "Tuleva maailma aktsiate pensionifond"
    }
    with(response[1]) {
      name == "Tuleva maailma võlakirjade pensionifond"
    }
    response.size() == 2
  }
}
