package ee.tuleva.onboarding.fund

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100

import ee.tuleva.onboarding.fund.FundNavValues
import ee.tuleva.onboarding.fund.statistics.PensionFundStatistics
import ee.tuleva.onboarding.fund.statistics.PensionFundStatisticsService
import ee.tuleva.onboarding.locale.LocaleService
import org.springframework.web.server.ResponseStatusException
import spock.lang.Specification

import java.time.LocalDate
import java.time.ZoneId

import static ee.tuleva.onboarding.fund.FundFixture.additionalSavingsFund
import static ee.tuleva.onboarding.locale.LocaleConfiguration.DEFAULT_LOCALE
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds

class FundServiceSpec extends Specification {

  def fundRepository = Mock(FundRepository)
  def pensionFundStatisticsService = Mock(PensionFundStatisticsService)
  def fundNavValues = Mock(FundNavValues)
  def localeService = Mock(LocaleService)
  def savingsFundUnitStats = Mock(SavingsFundUnitStats)
  def savingsFundNav = Stub(SavingsFundNav) { isSavingsFund("EE0000003283") >> true }

  def fundService = new FundService(fundRepository, pensionFundStatisticsService,
      fundNavValues, localeService, savingsFundUnitStats, savingsFundNav)

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
    localeService.getCurrentLocale() >> DEFAULT_LOCALE
    fundNavValues.lastValue(_ as String) >> Optional.empty()

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
    fundNavValues.lastValue(_ as String) >> Optional.empty()

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
      .toList()
    fundRepository.findAllByFundManagerNameIgnoreCase(fundManagerName) >> funds
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    localeService.getCurrentLocale() >> DEFAULT_LOCALE
    fundNavValues.lastValue(_ as String) >> Optional.empty()

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
    localeService.getCurrentLocale() >> DEFAULT_LOCALE
    fundNavValues.lastValue(tulevaFund.isin) >> Optional.of(
        new FundNavValues.NavPoint(LocalDate.parse("2023-11-03"), 123.0))
    fundNavValues.lastValue(_ as String) >> Optional.empty()


    when:
    def response = fundService.getFunds(Optional.of(fundManagerName))

    then:
    def fund = response.first()
    fund.isin == tulevaFund.isin
    fund.nav == 123.0
    fund.volume == null
    response.size() == 2
  }

  def "after issuance: uses latest NAV and current balance for volume"() {
    given:
    def savingsFund = additionalSavingsFund()
    fundRepository.findAll() >> [savingsFund]
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    localeService.getCurrentLocale() >> DEFAULT_LOCALE
    def safeDate = LocalDate.parse("2025-01-17")
    def navDate = LocalDate.parse("2025-01-20")
    def nav = 1.1234
    savingsFundNav.safeMaxNavDate() >> safeDate
    fundNavValues.latestValueOnOrBefore(savingsFund.isin, safeDate) >> Optional.of(
        new FundNavValues.NavPoint(navDate, nav))

    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    savingsFundUnitStats.unitsOutstanding() >> 10500.00000
    savingsFundUnitStats.unitsOutstandingAt(cutoff) >> 10000.00000
    savingsFundUnitStats.unitHolderCount() >> 42

    when:
    def response = fundService.getFunds(Optional.empty())

    then:
    def fund = response.first()
    fund.nav == nav
    fund.volume == 11795.70
    fund.peopleCount == 42
  }

  def "during gap: uses previous NAV when issuance has not run yet"() {
    given:
    def savingsFund = additionalSavingsFund()
    fundRepository.findAll() >> [savingsFund]
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    localeService.getCurrentLocale() >> DEFAULT_LOCALE
    def safeDate = LocalDate.parse("2025-01-17")
    def navDate = LocalDate.parse("2025-01-20")
    def nav = 1.12345
    savingsFundNav.safeMaxNavDate() >> safeDate
    fundNavValues.latestValueOnOrBefore(savingsFund.isin, safeDate) >> Optional.of(
        new FundNavValues.NavPoint(navDate, nav))
    def previousNavDate = LocalDate.parse("2025-01-17")
    def previousNav = 1.11000
    fundNavValues.latestValueOnOrBefore(savingsFund.isin, navDate.minusDays(1)) >> Optional.of(
        new FundNavValues.NavPoint(previousNavDate, previousNav))

    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    savingsFundUnitStats.unitsOutstanding() >> 10000.00000
    savingsFundUnitStats.unitsOutstandingAt(cutoff) >> 10000.00000
    savingsFundUnitStats.unitHolderCount() >> 42

    when:
    def response = fundService.getFunds(Optional.empty())

    then:
    def fund = response.first()
    fund.nav == previousNav
    fund.volume == 11100.00
    fund.peopleCount == 42
  }

  def "weekend gap: uses previous NAV before Monday issuance"() {
    given:
    def savingsFund = additionalSavingsFund()
    fundRepository.findAll() >> [savingsFund]
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    localeService.getCurrentLocale() >> DEFAULT_LOCALE
    def safeDate = LocalDate.parse("2025-01-16")
    def navDate = LocalDate.parse("2025-01-16")
    def nav = 1.1235
    savingsFundNav.safeMaxNavDate() >> safeDate
    fundNavValues.latestValueOnOrBefore(savingsFund.isin, safeDate) >> Optional.of(
        new FundNavValues.NavPoint(navDate, nav))
    def previousNavDate = LocalDate.parse("2025-01-15")
    def previousNav = 1.1100
    fundNavValues.latestValueOnOrBefore(savingsFund.isin, navDate.minusDays(1)) >> Optional.of(
        new FundNavValues.NavPoint(previousNavDate, previousNav))

    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    savingsFundUnitStats.unitsOutstanding() >> 10000.00000
    savingsFundUnitStats.unitsOutstandingAt(cutoff) >> 10000.00000
    savingsFundUnitStats.unitHolderCount() >> 42

    when:
    def response = fundService.getFunds(Optional.empty())

    then:
    def fund = response.first()
    fund.nav == previousNav
    fund.volume == 11100.00
    fund.peopleCount == 42
  }

  def "weekend after issuance: uses latest NAV and current balance"() {
    given:
    def savingsFund = additionalSavingsFund()
    fundRepository.findAll() >> [savingsFund]
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    localeService.getCurrentLocale() >> DEFAULT_LOCALE
    def safeDate = LocalDate.parse("2025-01-16")
    def navDate = LocalDate.parse("2025-01-17")
    def nav = 1.1234
    savingsFundNav.safeMaxNavDate() >> safeDate
    fundNavValues.latestValueOnOrBefore(savingsFund.isin, safeDate) >> Optional.of(
        new FundNavValues.NavPoint(navDate, nav))

    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    savingsFundUnitStats.unitsOutstanding() >> 10500.00000
    savingsFundUnitStats.unitsOutstandingAt(cutoff) >> 10000.00000
    savingsFundUnitStats.unitHolderCount() >> 42

    when:
    def response = fundService.getFunds(Optional.empty())

    then:
    def fund = response.first()
    fund.nav == nav
    fund.volume == 11795.70
    fund.peopleCount == 42
  }

  def "no previous NAV exists: falls back to current NAV during gap"() {
    given:
    def savingsFund = additionalSavingsFund()
    fundRepository.findAll() >> [savingsFund]
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    localeService.getCurrentLocale() >> DEFAULT_LOCALE
    def safeDate = LocalDate.parse("2025-01-16")
    def navDate = LocalDate.parse("2025-01-17")
    def nav = 1.00000
    savingsFundNav.safeMaxNavDate() >> safeDate
    fundNavValues.latestValueOnOrBefore(savingsFund.isin, safeDate) >> Optional.of(
        new FundNavValues.NavPoint(navDate, nav))
    fundNavValues.latestValueOnOrBefore(savingsFund.isin, navDate.minusDays(1)) >> Optional.empty()

    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    savingsFundUnitStats.unitsOutstanding() >> 10000.00000
    savingsFundUnitStats.unitsOutstandingAt(cutoff) >> 10000.00000
    savingsFundUnitStats.unitHolderCount() >> 42

    when:
    def response = fundService.getFunds(Optional.empty())

    then:
    def fund = response.first()
    fund.nav == nav
    fund.volume == 10000.00
    fund.peopleCount == 42
  }

  def "zero balance: volume is zero during gap"() {
    given:
    def savingsFund = additionalSavingsFund()
    fundRepository.findAll() >> [savingsFund]
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    localeService.getCurrentLocale() >> DEFAULT_LOCALE
    def safeDate = LocalDate.parse("2025-01-16")
    def navDate = LocalDate.parse("2025-01-16")
    def nav = 1.1235
    savingsFundNav.safeMaxNavDate() >> safeDate
    fundNavValues.latestValueOnOrBefore(savingsFund.isin, safeDate) >> Optional.of(
        new FundNavValues.NavPoint(navDate, nav))
    def previousNav = 1.1100
    fundNavValues.latestValueOnOrBefore(savingsFund.isin, navDate.minusDays(1)) >> Optional.of(
        new FundNavValues.NavPoint(LocalDate.parse("2025-01-15"), previousNav))

    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    savingsFundUnitStats.unitsOutstanding() >> 0.0
    savingsFundUnitStats.unitsOutstandingAt(cutoff) >> 0.0
    savingsFundUnitStats.unitHolderCount() >> 42

    when:
    def response = fundService.getFunds(Optional.empty())

    then:
    def fund = response.first()
    fund.volume == 0.00
    fund.peopleCount == 42
  }

  def "savings fund NAV has 4 decimal places"() {
    given:
    def savingsFund = additionalSavingsFund()
    fundRepository.findAll() >> [savingsFund]
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    localeService.getCurrentLocale() >> DEFAULT_LOCALE
    def safeDate = LocalDate.parse("2025-01-17")
    def navDate = LocalDate.parse("2025-01-20")
    savingsFundNav.safeMaxNavDate() >> safeDate
    fundNavValues.latestValueOnOrBefore(savingsFund.isin, safeDate) >> Optional.of(
        new FundNavValues.NavPoint(navDate, nav))

    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    savingsFundUnitStats.unitsOutstanding() >> 10500.00000
    savingsFundUnitStats.unitsOutstandingAt(cutoff) >> 10000.00000
    savingsFundUnitStats.unitHolderCount() >> 42

    when:
    def response = fundService.getFunds(Optional.empty())

    then:
    def fund = response.first()
    fund.nav == expectedNav
    fund.nav.scale() == 4

    where:
    nav      | expectedNav
    1.0      | 1.0000
    1.23450  | 1.2345
  }

  def "savings fund NAV newer than safe date is not visible"() {
    given:
    def savingsFund = additionalSavingsFund()
    fundRepository.findAll() >> [savingsFund]
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    localeService.getCurrentLocale() >> DEFAULT_LOCALE
    def safeDate = LocalDate.parse("2025-01-16")
    def safeNav = 1.1000
    savingsFundNav.safeMaxNavDate() >> safeDate
    fundNavValues.latestValueOnOrBefore(savingsFund.isin, safeDate) >> Optional.of(
        new FundNavValues.NavPoint(safeDate, safeNav))
    def cutoff = safeDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    savingsFundUnitStats.unitsOutstanding() >> 10500.00000
    savingsFundUnitStats.unitsOutstandingAt(cutoff) >> 10000.00000
    savingsFundUnitStats.unitHolderCount() >> 42

    when:
    def response = fundService.getFunds(Optional.empty())

    then:
    def fund = response.first()
    fund.nav == safeNav
    0 * fundNavValues.lastValue(savingsFund.isin)
  }

  def "non-savings fund returns null volume"() {
    given:
    String fundManagerName = "Tuleva"
    def funds = sampleFunds().stream()
      .filter({ fund -> fund.fundManager.name == fundManagerName })
      .toList()
    fundRepository.findAllByFundManagerNameIgnoreCase(fundManagerName) >> funds
    def tulevaFund = funds.first()
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    localeService.getCurrentLocale() >> DEFAULT_LOCALE
    fundNavValues.lastValue(tulevaFund.isin) >> Optional.of(
        new FundNavValues.NavPoint(LocalDate.parse("2023-11-03"), 123.0))
    fundNavValues.lastValue(_ as String) >> Optional.empty()


    when:
    def response = fundService.getFunds(Optional.of(fundManagerName))

    then:
    def fund = response.first()
    fund.nav == 123.0
    fund.volume == null
  }
}
