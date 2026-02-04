package ee.tuleva.onboarding.fund


import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository
import ee.tuleva.onboarding.fund.statistics.PensionFundStatistics
import ee.tuleva.onboarding.fund.statistics.PensionFundStatisticsService
import ee.tuleva.onboarding.ledger.LedgerAccount
import ee.tuleva.onboarding.ledger.LedgerService
import ee.tuleva.onboarding.ledger.SystemAccount
import ee.tuleva.onboarding.locale.LocaleConfiguration
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.savings.fund.SavingsFundConfiguration
import spock.lang.Specification

import java.time.LocalDate
import java.time.ZoneId

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static ee.tuleva.onboarding.fund.FundFixture.additionalSavingsFund
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static java.util.stream.Collectors.toList

class FundServiceSpec extends Specification {

  def fundRepository = Mock(FundRepository)
  def pensionFundStatisticsService = Mock(PensionFundStatisticsService)
  def fundValueRepository = Mock(FundValueRepository)
  def localeService = Mock(LocaleService)
  def ledgerService = Mock(LedgerService)
  def savingsFundConfiguration = Mock(SavingsFundConfiguration)

  def fundService = new FundService(fundRepository, pensionFundStatisticsService,
      fundValueRepository, localeService, ledgerService, savingsFundConfiguration)

  def "can get funds and statistics"() {
    given:
    def fundManagerName = "Tuleva"
    def funds = sampleFunds().stream()
      .filter({ fund -> fund.fundManager.name == fundManagerName })
      .toList()
    fundRepository.findAllByFundManagerNameIgnoreCase(fundManagerName) >> funds
    def tulevaFund = funds.first()
    def volume = 1_000_000.0
    def nav = 1.64
    def peopleCount = 123
    pensionFundStatisticsService.getCachedStatistics() >>
      [new PensionFundStatistics(tulevaFund.isin, volume, nav, peopleCount)]
    localeService.getCurrentLocale() >> LocaleConfiguration.DEFAULT_LOCALE
    fundValueRepository.findLastValueForFund(_ as String) >> Optional.empty()

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
    def fundManagerName = "Tuleva"
    def funds = sampleFunds().stream()
      .filter({ fund -> fund.fundManager.name == fundManagerName })
      .toList()
    fundRepository.findAllByFundManagerNameIgnoreCase(fundManagerName) >> funds
    localeService.getCurrentLocale() >> Locale.forLanguageTag(language)

    def tulevaFund = funds.first()
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    fundValueRepository.findLastValueForFund(_ as String) >> Optional.empty()

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
    def fundManagerName = "Tuleva"
    def funds = sampleFunds().stream()
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
    localeService.getCurrentLocale() >> LocaleConfiguration.DEFAULT_LOCALE
    fundValueRepository.findLastValueForFund(_ as String) >> Optional.empty()

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

  def "gives a fallback nav when no statistics found"() {
    given:
    String fundManagerName = "Tuleva"
    def funds = sampleFunds().stream()
      .filter({ fund -> fund.fundManager.name == fundManagerName })
      .toList()
    fundRepository.findAllByFundManagerNameIgnoreCase(fundManagerName) >> funds
    def tulevaFund = funds.first()
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    localeService.getCurrentLocale() >> LocaleConfiguration.DEFAULT_LOCALE
    fundValueRepository.findLastValueForFund(tulevaFund.isin) >> Optional.of(
        aFundValue(tulevaFund.isin, LocalDate.parse("2023-11-03"),123.0))
    fundValueRepository.findLastValueForFund(_ as String) >> Optional.empty()
    savingsFundConfiguration.getIsin() >> "EE0000003283"

    when:
    def response = fundService.getFunds(Optional.of(fundManagerName))

    then:
    def fund = response.first()
    fund.isin == tulevaFund.isin
    fund.nav == 123.0
    fund.volume == null
    response.size() == 2
  }

  def "calculates savings fund volume from ledger outstanding units and NAV at end of NAV date"() {
    given:
    def savingsFund = additionalSavingsFund()
    fundRepository.findAll() >> [savingsFund]
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    localeService.getCurrentLocale() >> LocaleConfiguration.DEFAULT_LOCALE
    def navDate = LocalDate.parse("2025-01-15")
    def nav = 1.12345
    fundValueRepository.findLastValueForFund(savingsFund.isin) >> Optional.of(
        aFundValue(savingsFund.isin, navDate, nav))
    savingsFundConfiguration.getIsin() >> "EE0000003283"
    def endOfNavDate = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    def outstandingUnitsAccount = Mock(LedgerAccount)
    outstandingUnitsAccount.getBalanceAt(endOfNavDate) >> new BigDecimal("10000.00000")
    ledgerService.getSystemAccount(SystemAccount.FUND_UNITS_OUTSTANDING) >> outstandingUnitsAccount

    when:
    def response = fundService.getFunds(Optional.empty())

    then:
    def fund = response.first()
    fund.isin == savingsFund.isin
    fund.nav == nav
    fund.volume == new BigDecimal("11234.50")
  }

  def "savings fund volume is zero when no ledger entries exist"() {
    given:
    def savingsFund = additionalSavingsFund()
    fundRepository.findAll() >> [savingsFund]
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    localeService.getCurrentLocale() >> LocaleConfiguration.DEFAULT_LOCALE
    def navDate = LocalDate.parse("2025-01-15")
    def nav = 1.50000
    fundValueRepository.findLastValueForFund(savingsFund.isin) >> Optional.of(
        aFundValue(savingsFund.isin, navDate, nav))
    savingsFundConfiguration.getIsin() >> "EE0000003283"
    def endOfNavDate = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    def outstandingUnitsAccount = Mock(LedgerAccount)
    outstandingUnitsAccount.getBalanceAt(endOfNavDate) >> BigDecimal.ZERO
    ledgerService.getSystemAccount(SystemAccount.FUND_UNITS_OUTSTANDING) >> outstandingUnitsAccount

    when:
    def response = fundService.getFunds(Optional.empty())

    then:
    def fund = response.first()
    fund.isin == savingsFund.isin
    fund.nav == nav
    fund.volume == new BigDecimal("0.00")
  }
}
