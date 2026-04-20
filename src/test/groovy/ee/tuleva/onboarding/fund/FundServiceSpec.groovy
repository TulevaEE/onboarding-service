package ee.tuleva.onboarding.fund

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository
import ee.tuleva.onboarding.fund.statistics.PensionFundStatistics
import ee.tuleva.onboarding.fund.statistics.PensionFundStatisticsService
import ee.tuleva.onboarding.ledger.LedgerAccount
import ee.tuleva.onboarding.ledger.LedgerService
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.savings.fund.SavingsFundConfiguration
import ee.tuleva.onboarding.savings.fund.nav.FundNavProvider
import org.springframework.web.server.ResponseStatusException
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
  def savingsFundConfiguration = Stub(SavingsFundConfiguration) { getIsin() >> "EE0000003283" }
  def savingsFundNavProvider = Mock(FundNavProvider)

  def fundService = new FundService(fundRepository, pensionFundStatisticsService,
      fundValueRepository, localeService, ledgerService, savingsFundConfiguration,
      savingsFundNavProvider)

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
    savingsFundNavProvider.safeMaxNavDate() >> safeDate
    fundValueRepository.getLatestValue(savingsFund.isin, safeDate) >> Optional.of(
        aFundValue(savingsFund.isin, navDate, nav))

    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    def outstandingUnitsAccount = Mock(LedgerAccount)
    outstandingUnitsAccount.getBalance() >> 10500.00000
    outstandingUnitsAccount.getBalanceAt(cutoff) >> 10000.00000
    ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100) >> outstandingUnitsAccount
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
    def safeDate = LocalDate.parse("2025-01-17")
    def navDate = LocalDate.parse("2025-01-20")
    def nav = 1.12345
    savingsFundNavProvider.safeMaxNavDate() >> safeDate
    fundValueRepository.getLatestValue(savingsFund.isin, safeDate) >> Optional.of(
        aFundValue(savingsFund.isin, navDate, nav))
    def previousNavDate = LocalDate.parse("2025-01-17")
    def previousNav = 1.11000
    fundValueRepository.getLatestValue(savingsFund.isin, navDate.minusDays(1)) >> Optional.of(
        aFundValue(savingsFund.isin, previousNavDate, previousNav))

    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    def outstandingUnitsAccount = Mock(LedgerAccount)
    outstandingUnitsAccount.getBalance() >> 10000.00000
    outstandingUnitsAccount.getBalanceAt(cutoff) >> 10000.00000
    ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100) >> outstandingUnitsAccount
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
    def safeDate = LocalDate.parse("2025-01-16")
    def navDate = LocalDate.parse("2025-01-16")
    def nav = 1.1235
    savingsFundNavProvider.safeMaxNavDate() >> safeDate
    fundValueRepository.getLatestValue(savingsFund.isin, safeDate) >> Optional.of(
        aFundValue(savingsFund.isin, navDate, nav))
    def previousNavDate = LocalDate.parse("2025-01-15")
    def previousNav = 1.1100
    fundValueRepository.getLatestValue(savingsFund.isin, navDate.minusDays(1)) >> Optional.of(
        aFundValue(savingsFund.isin, previousNavDate, previousNav))

    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    def outstandingUnitsAccount = Mock(LedgerAccount)
    outstandingUnitsAccount.getBalance() >> 10000.00000
    outstandingUnitsAccount.getBalanceAt(cutoff) >> 10000.00000
    ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100) >> outstandingUnitsAccount
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
    def safeDate = LocalDate.parse("2025-01-16")
    def navDate = LocalDate.parse("2025-01-17")
    def nav = 1.1234
    savingsFundNavProvider.safeMaxNavDate() >> safeDate
    fundValueRepository.getLatestValue(savingsFund.isin, safeDate) >> Optional.of(
        aFundValue(savingsFund.isin, navDate, nav))

    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    def outstandingUnitsAccount = Mock(LedgerAccount)
    outstandingUnitsAccount.getBalance() >> 10500.00000
    outstandingUnitsAccount.getBalanceAt(cutoff) >> 10000.00000
    ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100) >> outstandingUnitsAccount
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
    def safeDate = LocalDate.parse("2025-01-16")
    def navDate = LocalDate.parse("2025-01-17")
    def nav = 1.00000
    savingsFundNavProvider.safeMaxNavDate() >> safeDate
    fundValueRepository.getLatestValue(savingsFund.isin, safeDate) >> Optional.of(
        aFundValue(savingsFund.isin, navDate, nav))
    fundValueRepository.getLatestValue(savingsFund.isin, navDate.minusDays(1)) >> Optional.empty()

    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    def outstandingUnitsAccount = Mock(LedgerAccount)
    outstandingUnitsAccount.getBalance() >> 10000.00000
    outstandingUnitsAccount.getBalanceAt(cutoff) >> 10000.00000
    ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100) >> outstandingUnitsAccount
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
    def safeDate = LocalDate.parse("2025-01-16")
    def navDate = LocalDate.parse("2025-01-16")
    def nav = 1.1235
    savingsFundNavProvider.safeMaxNavDate() >> safeDate
    fundValueRepository.getLatestValue(savingsFund.isin, safeDate) >> Optional.of(
        aFundValue(savingsFund.isin, navDate, nav))
    def previousNav = 1.1100
    fundValueRepository.getLatestValue(savingsFund.isin, navDate.minusDays(1)) >> Optional.of(
        aFundValue(savingsFund.isin, LocalDate.parse("2025-01-15"), previousNav))

    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    def outstandingUnitsAccount = Mock(LedgerAccount)
    outstandingUnitsAccount.getBalance() >> 0.0
    outstandingUnitsAccount.getBalanceAt(cutoff) >> 0.0
    ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100) >> outstandingUnitsAccount
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
    def safeDate = LocalDate.parse("2025-01-17")
    def navDate = LocalDate.parse("2025-01-20")
    savingsFundNavProvider.safeMaxNavDate() >> safeDate
    fundValueRepository.getLatestValue(savingsFund.isin, safeDate) >> Optional.of(
        aFundValue(savingsFund.isin, navDate, nav))

    def cutoff = navDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    def outstandingUnitsAccount = Mock(LedgerAccount)
    outstandingUnitsAccount.getBalance() >> 10500.00000
    outstandingUnitsAccount.getBalanceAt(cutoff) >> 10000.00000
    ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100) >> outstandingUnitsAccount
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

  def "savings fund NAV newer than safe date is not visible"() {
    given:
    def savingsFund = additionalSavingsFund()
    fundRepository.findAll() >> [savingsFund]
    pensionFundStatisticsService.getCachedStatistics() >> [PensionFundStatistics.getNull()]
    localeService.getCurrentLocale() >> DEFAULT_LOCALE
    def safeDate = LocalDate.parse("2025-01-16")
    def safeNav = 1.1000
    savingsFundNavProvider.safeMaxNavDate() >> safeDate
    fundValueRepository.getLatestValue(savingsFund.isin, safeDate) >> Optional.of(
        aFundValue(savingsFund.isin, safeDate, safeNav))
    def cutoff = safeDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant()
    def outstandingUnitsAccount = Mock(LedgerAccount)
    outstandingUnitsAccount.getBalance() >> 10500.00000
    outstandingUnitsAccount.getBalanceAt(cutoff) >> 10000.00000
    ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100) >> outstandingUnitsAccount
    ledgerService.countAccountsWithPositiveBalance(FUND_UNITS) >> 42

    when:
    def response = fundService.getFunds(Optional.empty())

    then:
    def fund = response.first()
    fund.nav == safeNav
    0 * fundValueRepository.findLastValueForFund(savingsFund.isin)
  }

  def "getNavHistory returns mapped fund values"() {
    given:
    def isin = "EE0000003283"
    def startDate = LocalDate.of(2026, 2, 2)
    def endDate = LocalDate.of(2026, 4, 14)
    fundRepository.findByIsin(isin) >> additionalSavingsFund()
    savingsFundNavProvider.safeMaxNavDate() >> LocalDate.of(2099, 1, 1)
    fundValueRepository.findValuesBetweenDates(isin, startDate, endDate) >> [
        aFundValue(isin, LocalDate.of(2026, 2, 3), 1.0000),
        aFundValue(isin, LocalDate.of(2026, 2, 4), 1.0012),
    ]

    when:
    def result = fundService.getNavHistory(isin, startDate, endDate)

    then:
    result.size() == 2
    result[0] == new NavValueResponse(LocalDate.of(2026, 2, 3), 1.0000G)
    result[1] == new NavValueResponse(LocalDate.of(2026, 2, 4), 1.0012G)
  }

  def "getNavHistory defaults null dates to full range"() {
    given:
    def isin = "EE0000003283"
    def safeMaxDate = LocalDate.of(2026, 4, 19)
    fundRepository.findByIsin(isin) >> additionalSavingsFund()
    savingsFundNavProvider.safeMaxNavDate() >> safeMaxDate
    fundValueRepository.findValuesBetweenDates(isin, LocalDate.EPOCH, safeMaxDate) >> []

    when:
    def result = fundService.getNavHistory(isin, null, null)

    then:
    result.isEmpty()
  }

  def "getNavHistory caps savings fund end date to safeMaxNavDate to prevent early publication"() {
    given:
    def isin = "EE0000003283"
    def safeMaxDate = LocalDate.of(2026, 4, 19)
    def requestedEnd = LocalDate.of(2026, 4, 20)
    fundRepository.findByIsin(isin) >> additionalSavingsFund()
    savingsFundNavProvider.safeMaxNavDate() >> safeMaxDate

    when:
    fundService.getNavHistory(isin, null, requestedEnd)

    then:
    1 * fundValueRepository.findValuesBetweenDates(isin, LocalDate.EPOCH, safeMaxDate) >> []
    0 * fundValueRepository.findValuesBetweenDates(isin, _, requestedEnd)
  }

  def "getNavHistory does not cap non-savings fund end date"() {
    given:
    def nonSavingsFund = sampleFunds().stream()
        .filter({ f -> f.fundManager.name == "Tuleva" }).findFirst().get()
    def isin = nonSavingsFund.isin
    def requestedEnd = LocalDate.of(2026, 4, 20)
    fundRepository.findByIsin(isin) >> nonSavingsFund

    when:
    fundService.getNavHistory(isin, null, requestedEnd)

    then:
    0 * savingsFundNavProvider.safeMaxNavDate()
    1 * fundValueRepository.findValuesBetweenDates(isin, LocalDate.EPOCH, requestedEnd) >> []
  }

  def "getNavHistory returns empty list when start date is after capped end date"() {
    given:
    def isin = "EE0000003283"
    def safeMaxDate = LocalDate.of(2026, 4, 19)
    def requestedStart = LocalDate.of(2026, 4, 20)
    def requestedEnd = LocalDate.of(2026, 4, 25)
    fundRepository.findByIsin(isin) >> additionalSavingsFund()
    savingsFundNavProvider.safeMaxNavDate() >> safeMaxDate

    when:
    def result = fundService.getNavHistory(isin, requestedStart, requestedEnd)

    then:
    result.isEmpty()
    0 * fundValueRepository.findValuesBetweenDates(_, _, _)
  }

  def "getNavHistory uses DB fund ISIN not request ISIN for query and cap check"() {
    given:
    def requestIsin = "ee0000003283"
    def dbIsin = "EE0000003283"
    def savingsFund = additionalSavingsFund()
    def safeMaxDate = LocalDate.of(2026, 4, 19)
    fundRepository.findByIsin(requestIsin) >> savingsFund
    savingsFundNavProvider.safeMaxNavDate() >> safeMaxDate

    when:
    fundService.getNavHistory(requestIsin, null, LocalDate.of(2026, 4, 25))

    then:
    1 * fundValueRepository.findValuesBetweenDates(dbIsin, LocalDate.EPOCH, safeMaxDate) >> []
    0 * fundValueRepository.findValuesBetweenDates(requestIsin, _, _)
  }

  def "getNavHistory throws 404 for unknown ISIN"() {
    given:
    fundRepository.findByIsin("UNKNOWN") >> null

    when:
    fundService.getNavHistory("UNKNOWN", null, null)

    then:
    thrown(ResponseStatusException)
  }

  def "getNavHistoryCsv returns semicolon-delimited CSV with BOM"() {
    given:
    def isin = "EE0000003283"
    def startDate = LocalDate.of(2026, 2, 2)
    def endDate = LocalDate.of(2026, 4, 14)
    fundRepository.findByIsin(isin) >> additionalSavingsFund()
    savingsFundNavProvider.safeMaxNavDate() >> LocalDate.of(2099, 1, 1)
    fundValueRepository.findValuesBetweenDates(isin, startDate, endDate) >> [
        aFundValue(isin, LocalDate.of(2026, 2, 3), 1.0000),
        aFundValue(isin, LocalDate.of(2026, 2, 4), 1.0012),
    ]

    when:
    def bytes = fundService.getNavHistoryCsv(isin, startDate, endDate)

    then:
    bytes[0] == (byte) 0xEF
    bytes[1] == (byte) 0xBB
    bytes[2] == (byte) 0xBF
    def csv = new String(bytes, "UTF-8")
    def lines = csv.trim().split("\r\n")
    lines.length == 3
    lines[0].endsWith("Kuupäev;NAV (EUR)")
    lines[1] == "03.02.2026;1.0000"
    lines[2] == "04.02.2026;1.0012"
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


    when:
    def response = fundService.getFunds(Optional.of(fundManagerName))

    then:
    def fund = response.first()
    fund.nav == 123.0
    fund.volume == null
  }
}
