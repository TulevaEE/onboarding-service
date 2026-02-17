package ee.tuleva.onboarding.fund

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository
import ee.tuleva.onboarding.fund.statistics.PensionFundStatistics
import ee.tuleva.onboarding.fund.statistics.PensionFundStatisticsService
import ee.tuleva.onboarding.ledger.LedgerAccount
import ee.tuleva.onboarding.ledger.LedgerService
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.savings.fund.SavingsFundConfiguration
import spock.lang.Specification

import java.time.LocalDate
import java.time.ZoneId

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static ee.tuleva.onboarding.fund.FundFixture.additionalSavingsFund
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS
import static ee.tuleva.onboarding.locale.LocaleConfiguration.DEFAULT_LOCALE
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds

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
    localeService.getCurrentLocale() >> DEFAULT_LOCALE
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
      .toList()
    fundRepository.findAllByFundManagerNameIgnoreCase(fundManagerName) >> funds
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    localeService.getCurrentLocale() >> DEFAULT_LOCALE
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
    localeService.getCurrentLocale() >> DEFAULT_LOCALE
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

  def "after issuance: uses latest NAV and current balance for volume"() {
    given:
    def savingsFund = additionalSavingsFund()
    fundRepository.findAll() >> [savingsFund]
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    localeService.getCurrentLocale() >> DEFAULT_LOCALE
    def navDate = LocalDate.parse("2025-01-20")
    def nav = 1.1234
    fundValueRepository.findLastValueForFund(savingsFund.isin) >> Optional.of(
        aFundValue(savingsFund.isin, navDate, nav))
    savingsFundConfiguration.getIsin() >> "EE0000003283"
    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    def outstandingUnitsAccount = Mock(LedgerAccount)
    outstandingUnitsAccount.getBalance() >> 10500.00000
    outstandingUnitsAccount.getBalanceAt(cutoff) >> 10000.00000
    ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING) >> outstandingUnitsAccount
    ledgerService.countAccountsWithPositiveBalance(FUND_UNITS) >> 42

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
    def navDate = LocalDate.parse("2025-01-20")
    def nav = 1.12345
    fundValueRepository.findLastValueForFund(savingsFund.isin) >> Optional.of(
        aFundValue(savingsFund.isin, navDate, nav))
    def previousNavDate = LocalDate.parse("2025-01-17")
    def previousNav = 1.11000
    fundValueRepository.getLatestValue(savingsFund.isin, navDate.minusDays(1)) >> Optional.of(
        aFundValue(savingsFund.isin, previousNavDate, previousNav))
    savingsFundConfiguration.getIsin() >> "EE0000003283"
    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    def outstandingUnitsAccount = Mock(LedgerAccount)
    outstandingUnitsAccount.getBalance() >> 10000.00000
    outstandingUnitsAccount.getBalanceAt(cutoff) >> 10000.00000
    ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING) >> outstandingUnitsAccount
    ledgerService.countAccountsWithPositiveBalance(FUND_UNITS) >> 42

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
    def navDate = LocalDate.parse("2025-01-17")
    def nav = 1.12345
    fundValueRepository.findLastValueForFund(savingsFund.isin) >> Optional.of(
        aFundValue(savingsFund.isin, navDate, nav))
    def previousNavDate = LocalDate.parse("2025-01-16")
    def previousNav = 1.11000
    fundValueRepository.getLatestValue(savingsFund.isin, navDate.minusDays(1)) >> Optional.of(
        aFundValue(savingsFund.isin, previousNavDate, previousNav))
    savingsFundConfiguration.getIsin() >> "EE0000003283"
    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    def outstandingUnitsAccount = Mock(LedgerAccount)
    outstandingUnitsAccount.getBalance() >> 10000.00000
    outstandingUnitsAccount.getBalanceAt(cutoff) >> 10000.00000
    ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING) >> outstandingUnitsAccount
    ledgerService.countAccountsWithPositiveBalance(FUND_UNITS) >> 42

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
    def navDate = LocalDate.parse("2025-01-17")
    def nav = 1.1234
    fundValueRepository.findLastValueForFund(savingsFund.isin) >> Optional.of(
        aFundValue(savingsFund.isin, navDate, nav))
    savingsFundConfiguration.getIsin() >> "EE0000003283"
    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    def outstandingUnitsAccount = Mock(LedgerAccount)
    outstandingUnitsAccount.getBalance() >> 10500.00000
    outstandingUnitsAccount.getBalanceAt(cutoff) >> 10000.00000
    ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING) >> outstandingUnitsAccount
    ledgerService.countAccountsWithPositiveBalance(FUND_UNITS) >> 42

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
    def navDate = LocalDate.parse("2025-01-17")
    def nav = 1.00000
    fundValueRepository.findLastValueForFund(savingsFund.isin) >> Optional.of(
        aFundValue(savingsFund.isin, navDate, nav))
    fundValueRepository.getLatestValue(savingsFund.isin, navDate.minusDays(1)) >> Optional.empty()
    savingsFundConfiguration.getIsin() >> "EE0000003283"
    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    def outstandingUnitsAccount = Mock(LedgerAccount)
    outstandingUnitsAccount.getBalance() >> 10000.00000
    outstandingUnitsAccount.getBalanceAt(cutoff) >> 10000.00000
    ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING) >> outstandingUnitsAccount
    ledgerService.countAccountsWithPositiveBalance(FUND_UNITS) >> 42

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
    def navDate = LocalDate.parse("2025-01-17")
    def nav = 1.12345
    fundValueRepository.findLastValueForFund(savingsFund.isin) >> Optional.of(
        aFundValue(savingsFund.isin, navDate, nav))
    def previousNav = 1.11000
    fundValueRepository.getLatestValue(savingsFund.isin, navDate.minusDays(1)) >> Optional.of(
        aFundValue(savingsFund.isin, LocalDate.parse("2025-01-16"), previousNav))
    savingsFundConfiguration.getIsin() >> "EE0000003283"
    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    def outstandingUnitsAccount = Mock(LedgerAccount)
    outstandingUnitsAccount.getBalance() >> 0.0
    outstandingUnitsAccount.getBalanceAt(cutoff) >> 0.0
    ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING) >> outstandingUnitsAccount
    ledgerService.countAccountsWithPositiveBalance(FUND_UNITS) >> 42

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
    def navDate = LocalDate.parse("2025-01-20")
    fundValueRepository.findLastValueForFund(savingsFund.isin) >> Optional.of(
        aFundValue(savingsFund.isin, navDate, nav))
    savingsFundConfiguration.getIsin() >> "EE0000003283"
    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    def outstandingUnitsAccount = Mock(LedgerAccount)
    outstandingUnitsAccount.getBalance() >> 10500.00000
    outstandingUnitsAccount.getBalanceAt(cutoff) >> 10000.00000
    ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING) >> outstandingUnitsAccount
    ledgerService.countAccountsWithPositiveBalance(FUND_UNITS) >> 42

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
    fundValueRepository.findLastValueForFund(tulevaFund.isin) >> Optional.of(
        aFundValue(tulevaFund.isin, LocalDate.parse("2023-11-03"), 123.0))
    fundValueRepository.findLastValueForFund(_ as String) >> Optional.empty()
    savingsFundConfiguration.getIsin() >> "EE0000003283"

    when:
    def response = fundService.getFunds(Optional.of(fundManagerName))

    then:
    def fund = response.first()
    fund.nav == 123.0
    fund.volume == null
  }
}
