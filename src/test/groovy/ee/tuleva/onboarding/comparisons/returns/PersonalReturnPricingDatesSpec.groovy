package ee.tuleva.onboarding.comparisons.returns

import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider
import ee.tuleva.onboarding.comparisons.overview.BalancePriceDates
import ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider
import ee.tuleva.onboarding.comparisons.returns.provider.ReturnCalculationParameters
import ee.tuleva.onboarding.deadline.PublicHolidays
import ee.tuleva.onboarding.epis.CashFlow
import ee.tuleva.onboarding.epis.CashFlowStatement
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider.SECOND_PILLAR
import static ee.tuleva.onboarding.currency.Currency.EUR
import static java.time.ZoneOffset.UTC

class PersonalReturnPricingDatesSpec extends Specification {

  static final String FUND = "EE3600109435"

  Person person = samplePerson()
  FundRepository fundRepository = Mock()
  EpisService episService = Mock()
  FundValueProvider fundValueProvider = Mock()

  def setup() {
    fundRepository.findAllByPillar(2) >> [Fund.builder().isin(FUND).build()]
  }

  def "a year to today grows by the unit price between the last two published prices"() {
    given:
    def today = LocalDate.parse("2026-10-01")
    def provider = personalReturnProviderAt(today)
    pricesPublishedOn(LocalDate.parse("2025-09-30"), LocalDate.parse("2026-09-30"))
    balancesBetween(LocalDate.parse("2025-10-01"), today, 10000.00, 12110.00)

    when:
    def returns = provider.getReturns(periodBetween(LocalDate.parse("2025-10-01"), today))

    then:
    returns.returns.first().rate == 0.2110
  }

  def "a year starting on a Monday is measured from the Friday price that values its opening balance"() {
    given:
    def provider = personalReturnProviderAt(LocalDate.parse("2026-10-01"))
    pricesPublishedOn(LocalDate.parse("2025-04-04"), LocalDate.parse("2026-04-07"))
    balancesBetween(LocalDate.parse("2025-04-07"), LocalDate.parse("2026-04-07"), 10000.00, 12110.00)

    when:
    def returns = provider.getReturns(
        periodBetween(LocalDate.parse("2025-04-07"), LocalDate.parse("2026-04-07")))

    then:
    returns.returns.first().rate == 0.2091
  }

  private PersonalReturnProvider personalReturnProviderAt(LocalDate today) {
    def clock = Clock.fixed(today.atTime(10, 25).toInstant(UTC), UTC)
    def accountOverviewProvider =
        new AccountOverviewProvider(fundRepository, episService, new BalancePriceDates(fundValueProvider), clock)
    return new PersonalReturnProvider(
        accountOverviewProvider, new ReturnCalculator(fundValueProvider, new PublicHolidays()))
  }

  private void pricesPublishedOn(LocalDate... priceDates) {
    fundValueProvider.getLatestValue(FUND, _ as LocalDate) >> { String key, LocalDate date ->
      def latest = priceDates.findAll { !it.isAfter(date) }.max()
      latest ? Optional.of(aFundValue(key, latest, 1.0)) : Optional.empty()
    }
  }

  private void balancesBetween(LocalDate from, LocalDate to, BigDecimal opening, BigDecimal closing) {
    episService.getCashFlowStatement(person, from, to) >> CashFlowStatement.builder()
        .startBalance([(FUND): balance(opening)])
        .endBalance([(FUND): balance(closing)])
        .transactions([])
        .build()
  }

  private static CashFlow balance(BigDecimal amount) {
    return CashFlow.builder().isin(FUND).amount(amount).nav(1.0).currency(EUR)
        .time(Instant.parse("2025-01-01T00:00:00Z")).build()
  }

  private ReturnCalculationParameters periodBetween(LocalDate from, LocalDate to) {
    return new ReturnCalculationParameters(
        person, from.atStartOfDay(UTC).toInstant(), to.atStartOfDay(UTC).toInstant(), 2, [SECOND_PILLAR])
  }
}
